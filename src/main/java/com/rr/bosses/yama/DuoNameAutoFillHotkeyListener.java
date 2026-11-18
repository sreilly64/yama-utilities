package com.rr.bosses.yama;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.HotkeyListener;

import javax.inject.Inject;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class DuoNameAutoFillHotkeyListener extends HotkeyListener
{

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    private YamaUtilitiesPlugin plugin;

    @Inject
    private DuoNameAutoFillHotkeyListener(YamaUtilitiesPlugin plugin, YamaUtilitiesConfig config)
    {
        super(config::autofillKeybind);
        this.plugin = plugin;
    }

    @Override
    public void hotkeyPressed()
    {
        if (client.getVarcIntValue(VarClientID.MESLAYERMODE) != 2 && client.getVarcIntValue(VarClientID.MESLAYERMODE) != 8)
        {
            return;
        }

        String title = client.getWidget(InterfaceID.Chatbox.MES_TEXT).getText();
        Pattern titlePattern = Pattern.compile("(Whose fight would you like to join\\? You must be on their friends list\\.|Enter name of friend to add to list)");
        Matcher titleMatcher = titlePattern.matcher(title);
        if (!titleMatcher.find())
        {
            return;
        }

        String displayName = plugin.getDuoPartnerDisplayName();
        if (displayName == null)
        {
            return;
        }

        Objects.requireNonNull(client.getWidget(InterfaceID.Chatbox.MES_TEXT2)).setText(displayName + "*");
        clientThread.invoke(() ->
                client.setVarcStrValue(VarClientID.MESLAYERINPUT, displayName));
    }

}
