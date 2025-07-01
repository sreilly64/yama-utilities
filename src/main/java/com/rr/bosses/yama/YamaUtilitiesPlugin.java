package com.rr.bosses.yama;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@Slf4j
@PluginDescriptor(
		name = "Yama Utilities",
		description = "This plugin contains various QoL features for the Yama encounter.",
		tags = {"combat", "bosses", "bossing", "pve", "pvm"})
public class YamaUtilitiesPlugin extends Plugin {

	private boolean currentlyInsideYamasDomain;
	private NPC voiceOfYama;
	private PartyPluginDuoInfo partyPluginDuoInfo;
	private DuoNameAutoFillWidget duoNameAutoFillWidget;

	private final Map<String, Integer> personalDamage = new HashMap<>(); // key = enemy name, value = damage dealt to the enemy by the player
	private final Map<String, Integer> totalDamage = new HashMap<>(); // key = enemy name, value = total damage dealt to the enemy
	public static final int VOICE_OF_YAMA_NPC_ID = 14185;
	public static final int YAMAS_NPC_ID = 14176;
	public static final int JUDGE_OF_YAMA_NPC_ID = 14180;
	public static final int VOID_FLARE_NPC_ID = 14179;
	public static final int YAMAS_DOMAIN_REGION_ID = 6045;
	public static final int VAR_CLIENT_INT_CAMERA_LOAD_ID = 384;
	public static final String YAMA = "Yama";
	public static final String JUDGE_OF_YAMA = "Judge of Yama";
	public static final String VOID_FLARE = "Void Flare";
	public static final DecimalFormat DMG_FORMAT = new DecimalFormat("#,##0");
	public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("##0.0");
	public static final Set<String> ENEMY_NAMES = Set.of(
			YAMA, JUDGE_OF_YAMA, VOID_FLARE
	);
	public static final Set<String> PHASE_TRANSITION_OVERHEAD_TEXTS = Set.of(
			"Begone", "You bore me.", "Enough."
	);
	private static final Set<Integer> GAME_OBJECT_IDS_WHITELIST = Set.of(
			56247, 56249, 56264, 56358, 56265, 56335, 56336, 56337, 56338, 56339
	);
	private static final Set<Integer> WALL_OBJECTS_IDS_WHITELIST = Set.of(
			50909, 50910, 42251, 50908
	);
	private static final Set<Integer> GROUND_OBJECT_IDS_WHITELIST = Set.of(
			56358, 56246
	);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private YamaUtilitiesConfig config;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private WSClient wsClient;

	@Inject
	private PartyService partyService;

	public YamaUtilitiesPlugin()
	{
		this.partyPluginDuoInfo = new PartyPluginDuoInfo(false);
	}

	@Override
	protected void startUp() throws Exception {
		initializeBossDamageMaps();
		this.partyPluginDuoInfo.resetFields();
		checkPlayerCurrentLocation();
		wsClient.registerMessage(PartyPluginDuoInfo.class);
	}

	@Override
	protected void shutDown() throws Exception {
		wsClient.unregisterMessage(PartyPluginDuoInfo.class);
		clientThread.invoke(() ->
		{
			if (config.hideScenery() != SceneryFunction.NONE && client.getGameState() == GameState.LOGGED_IN)
			{
				client.setGameState(GameState.LOADING);
			}
		});
	}

	@Provides
    YamaUtilitiesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(YamaUtilitiesConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e) {
		if ((e.getGroup().equals("yamautilities") && e.getKey().equals("hideScenery")) && inRegion())
		{
			clientThread.invokeLater(() ->
			{
				if (client.getGameState() == GameState.LOGGED_IN)
				{
					client.setGameState(GameState.LOADING);
				}
			});
		}
	}

	@Subscribe(priority = 1) // run prior to plugins so that the member is joined by the time the plugins see it.
	public void onUserJoin(final UserJoin message)
	{
		if (this.partyService.getLocalMember() == null)
		{
			log.debug("Local member joined new party, resetting duo partner info.");
			this.partyPluginDuoInfo.resetFields();
		}
		sendPartyPluginDuoLocationMessage();
	}

	@Subscribe(priority = 1) // run prior to plugins so that the member is joined by the time the plugins see it.
	public void onUserPart(final UserPart message)
	{
		if (message.getMemberId() == this.partyPluginDuoInfo.getMemberId())
		{
			log.debug("Duo partner left the party, resetting duo partner info.");
			this.partyPluginDuoInfo.resetFields();
			setVoiceOfYamaOverheadText();
		}
	}

	@Subscribe
	public void onPartyPluginDuoInfo(PartyPluginDuoInfo event)
	{
		clientThread.invoke(() ->
		{
			log.debug("PartyPluginDuoLocation received with memberId = {} and in Yama's Domain = {}",
					event.getMemberId(),
					event.isCurrentlyInsideYamasDomain());
			if (event.getMemberId() == this.partyService.getLocalMember().getMemberId())
			{
				return;
			}
			if (this.partyPluginDuoInfo.getMemberId() == 0L || event.getMemberId() == this.partyPluginDuoInfo.getMemberId())
			{
				this.partyPluginDuoInfo = event;
				setVoiceOfYamaOverheadText();
				log.debug("Updated duo partner location.");
			}
		});
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!this.currentlyInsideYamasDomain)
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();

		if (hitsplat.getAmount() == 0)
		{
			return;
		}

		Actor actor = event.getActor();
		if (!(actor instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) actor;
		String npcName = Text.removeTags(npc.getName());

		if (npcName == null)
		{
			return;
		}

		if (hitsplat.getHitsplatType() == HitsplatID.HEAL)
		{
			//might add healing tracking here
			return;
		}

		if (hitsplat.isMine())
		{
			personalDamage.computeIfPresent(npcName, (k,v) -> v + hitsplat.getAmount());
		}
		totalDamage.computeIfPresent(npcName, (k,v) -> v + hitsplat.getAmount());

	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (event.getNpc().getId() == VOICE_OF_YAMA_NPC_ID)
		{
			this.voiceOfYama = event.getNpc();
			setVoiceOfYamaOverheadText();
			log.debug("Voice of Yama spawned.");
		}

		if (!this.currentlyInsideYamasDomain)
		{
			return;
		}

		if (event.getNpc().getId() == YAMAS_NPC_ID)
		{
			log.debug("Yama spawned.");
			initializeBossDamageMaps();
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (event.getNpc().getId() == VOICE_OF_YAMA_NPC_ID)
		{
			this.voiceOfYama = null;
			log.debug("Voice of Yama despawned.");
		}

		if (!this.currentlyInsideYamasDomain)
		{
			return;
		}

		int npcId = event.getNpc().getId();
		if (npcId != YAMAS_NPC_ID && npcId != JUDGE_OF_YAMA_NPC_ID)
		{
			return;
		}

		if (npcId == YAMAS_NPC_ID)
		{
			if (this.config.printDamageToChat())
			{
				List<String> messages = new ArrayList<>(Collections.emptyList());
				int damageToYama = personalDamage.get(YAMA);

				String yamaDamageChatMessage = new ChatMessageBuilder()
						.append(ChatColorType.NORMAL)
						.append("Damage dealt to Yama - ")
						.append(Color.RED, DMG_FORMAT.format(damageToYama) + " (" + DECIMAL_FORMAT.format((double) damageToYama / totalDamage.get(YAMA) * 100) + "%)")
						.build();

				messages.add(yamaDamageChatMessage);

				int damageToJudge = personalDamage.get(JUDGE_OF_YAMA);
				String judgeDamageChatMessage = new ChatMessageBuilder()
						.append(ChatColorType.NORMAL)
						.append("Damage dealt to Judge of Yama - ")
						.append(Color.RED, DMG_FORMAT.format(damageToJudge) + " (" + DECIMAL_FORMAT.format((double) damageToJudge / totalDamage.get(JUDGE_OF_YAMA) * 100) + "%)")
						.build();

				messages.add(judgeDamageChatMessage);

				for (String message: messages)
				{
					chatMessageManager.queue(QueuedMessage.builder()
							.type(ChatMessageType.GAMEMESSAGE)
							.runeLiteFormattedMessage(message)
							.build());
				}
			}
		}
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		if (event.getIndex() == VAR_CLIENT_INT_CAMERA_LOAD_ID)
		{
			checkPlayerCurrentLocation();
		}

		if (event.getIndex() != VarClientInt.INPUT_TYPE) {
			return;
		}

		if (client.getVarcIntValue(VarClientInt.INPUT_TYPE) != 2 && client.getVarcIntValue(VarClientInt.INPUT_TYPE) != 8)
		{
			return;
		}
		//Add widget to join duo partner at the Voice of Yama and Friends List if in a Party Plugin together.
		clientThread.invokeLater(() -> {
			String title = client.getWidget(InterfaceID.Chatbox.MES_TEXT).getText();
			Pattern titlePattern = Pattern.compile("(Whose fight would you like to join\\? You must be on their friends list\\.|Enter name of friend to add to list)");
			Matcher titleMatcher = titlePattern.matcher(title);
			if (titleMatcher.find() && partyService.isInParty())
			{
				String duoDisplayName = getDuoPartnerDisplayName();
				log.debug("duoDisplayName: {}", duoDisplayName);
				if (duoDisplayName != null)
				{
					log.debug("Creating duo widget.");
					Widget mesLayerWidget = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
					duoNameAutoFillWidget = new DuoNameAutoFillWidget(mesLayerWidget, client);
					duoNameAutoFillWidget.showWidget(duoDisplayName, config);
				}
			}
		});
	}

	private void setVoiceOfYamaOverheadText()
	{
		if (this.voiceOfYama == null)
		{
			return;
		}

		if (!partyService.isInParty() || !this.partyPluginDuoInfo.isCurrentlyInsideYamasDomain())
		{
			this.voiceOfYama.setOverheadCycle(1);
			return;
		}

		this.voiceOfYama.setOverheadCycle(0);
		String duoDisplayName = partyService.getMemberById(this.partyPluginDuoInfo.getMemberId()).getDisplayName();
		if (duoDisplayName != null && !duoDisplayName.isEmpty() && !duoDisplayName.equalsIgnoreCase("<unknown>"))
		{
			this.voiceOfYama.setOverheadText(duoDisplayName + " has entered Yama's Domain");
		}
		else
		{
			this.voiceOfYama.setOverheadText("Duo partner has entered Yama's Domain");
		}
	}

	private String getDuoPartnerDisplayName()
	{
		if (!partyService.isInParty())
		{
			return null;
		}

		if (this.partyPluginDuoInfo.getMemberId() != 0L)
		{
			return partyService.getMemberById(this.partyPluginDuoInfo.getMemberId()).getDisplayName();
		}

		log.debug("party size = {}", partyService.getMembers().size());
		for (PartyMember partyMember: partyService.getMembers())
		{
			if (partyMember.getMemberId() != partyService.getLocalMember().getMemberId())
			{
				return partyService.getMemberById(partyMember.getMemberId()).getDisplayName();
			}
		}

		return null;
	}

	private void initializeBossDamageMaps()
	{
		for (String enemyName: ENEMY_NAMES)
		{
			personalDamage.put(enemyName, 0);
			totalDamage.put(enemyName, 0);
		}
	}

	private void checkPlayerCurrentLocation()
	{
		if (client.getLocalPlayer() == null)
		{
			return;
		}

		int currentRegionId = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID();
		boolean updatedCurrentlyInsideYamasDomain = YAMAS_DOMAIN_REGION_ID == currentRegionId;

		if (updatedCurrentlyInsideYamasDomain != this.currentlyInsideYamasDomain)
		{
			this.currentlyInsideYamasDomain = updatedCurrentlyInsideYamasDomain;
			log.debug("currentlyInsideYamasDomain updated to: {}", this.currentlyInsideYamasDomain);

			sendPartyPluginDuoLocationMessage();
		}
	}

	private void sendPartyPluginDuoLocationMessage()
	{
		if (!partyService.isInParty())
		{
			return;
		}

		log.debug("Sending updated location to party plugin. currentlyInsideYamasDomain: {}", this.currentlyInsideYamasDomain);
		PartyPluginDuoInfo duoInfo = PartyPluginDuoInfo.builder()
				.currentlyInsideYamasDomain(this.currentlyInsideYamasDomain)
				.build();
		partyService.send(duoInfo);
	}

	private void hideWallObjects()
	{
		if (config.hideScenery() != SceneryFunction.NONE && inRegion())
		{
			Scene scene = client.getTopLevelWorldView().getScene();
			for (int x = 0; x < Constants.SCENE_SIZE; x++)
			{
				for (int y = 0; y < Constants.SCENE_SIZE; y++)
				{
					Tile tile = scene.getTiles()[client.getTopLevelWorldView().getPlane()][x][y];
					if (tile == null)
					{
						continue;
					}
					WallObject wallObject = tile.getWallObject();
					if (wallObject != null && !GAME_OBJECT_IDS_WHITELIST.contains(wallObject.getId()) && config.hideScenery() == SceneryFunction.SCENERY_AND_WALLS)
					{
						scene.removeTile(tile);
					}
				}
			}
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event) {
		if (config.hideScenery() != SceneryFunction.NONE && inRegion()) {
			GameObject gameObject = event.getGameObject();
			int id = gameObject.getId();
			if (GAME_OBJECT_IDS_WHITELIST.contains(id) || (WALL_OBJECTS_IDS_WHITELIST.contains(id) && config.hideScenery() == SceneryFunction.SCENERY)) {
				return;
			}
			client.getTopLevelWorldView().getScene().removeGameObject(event.getGameObject());

			if (config.hideScenery() == SceneryFunction.SCENERY_AND_WALLS)
			{
				if (GAME_OBJECT_IDS_WHITELIST.contains(id)) {
					return;
				}
				client.getTopLevelWorldView().getScene().removeGameObject(event.getGameObject());
				hideWallObjects();
			}
		}
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event) {
		if (config.hideScenery() != SceneryFunction.NONE && inRegion())
		{
			GroundObject groundObject = event.getGroundObject();
			int id = groundObject.getId();
			if (GROUND_OBJECT_IDS_WHITELIST.contains(id))
			{
				return;
			}
			event.getTile().setGroundObject(null);
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event) {
		if (config.hideFadeTransition() && inRegion())
		{
			int id = event.getScriptId();
			Object[] args = event.getScriptEvent().getArguments();
			if (id == 948) {
				args[4] = 255;
				args[5] = 0;
			} else if (id == 1514) {
				args[3] = 25;
			}
		}
	}

	private boolean inRegion()
	{
		WorldView wv = client.getTopLevelWorldView();
		return wv.isInstance() && Arrays.stream(wv.getMapRegions()).anyMatch(i -> i == YAMAS_DOMAIN_REGION_ID);
	}
}
