package com.rr.bosses.yama;

import net.runelite.client.config.*;

@ConfigGroup("yamautilities")
public interface YamaUtilitiesConfig extends Config
{
	@ConfigSection(
			name = "Boss Damage Contribution",
			description = "Configure settings for the boss damage contribution settings",
			position = 0
	)
	String BOSS_DAMAGE_SETTINGS = "bossDamageSettings";

	@ConfigItem(
			keyName = "printDamageToChat",
			name = "Print Damage To Chat",
			description = "Print personal damage and percentage of total damage to the chat",
			section = BOSS_DAMAGE_SETTINGS,
			position = 0
	)
	default boolean printDamageToChat()
	{
		return true;
	}

	@ConfigSection(
			name = "Duo Name Autofill",
			description = "Configure duo's name auto fill settings",
			position = 1
	)
	String AUTOFILL_SETTINGS = "autofillSettings";

	@ConfigItem(
			keyName = "autofillKeybind",
			name = "Autofill Keybind",
			description = "DOES NOT SUPPORT MODIFIERS",
			section = AUTOFILL_SETTINGS,
			position = 0
	)
	default Keybind autofillKeybind()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
			keyName = "enterKeybind",
			name = "Enter Keybind",
			description = "DOES NOT SUPPORT MODIFIERS",
			section = AUTOFILL_SETTINGS,
			position = 1
	)
	default Keybind enterKeybind()
	{
		return Keybind.NOT_SET;
	}

	@ConfigSection(
			name = "Hide Scenery and Effects",
			description = "",
			position = 2
	)
	String HIDE = "hide";

	@ConfigItem(
			keyName = "hideScenery",
			name = "Hide Scenery",
			description = "Hides scenery within Yama's Domain.",
			section = HIDE,
			position = 0
	)
	default SceneryFunction hideScenery()
	{
		return SceneryFunction.NONE;
	}

	@ConfigItem(
			keyName = "hideFadeTransition",
			name = "Hide Fade-out Transitions",
			description = "Hide the camera fade-out transitions between Yama's phases.",
			section = HIDE,
			position = 1
	)
	default boolean hideFadeTransition() 
	{
		return false;
	}
}
