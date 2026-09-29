package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class BetterTabPlus extends Module {

    // ============================================================
    // SETTING GROUPS
    // ============================================================

    private final SettingGroup sgDisplay =
        settings.getDefaultGroup();

    private final SettingGroup sgInformation =
        settings.createGroup("Information");

    private final SettingGroup sgHighlight =
        settings.createGroup("Highlight");

    // ============================================================
    // DISPLAY
    // ============================================================

    public final Setting<Double> scale =
        sgDisplay.add(new DoubleSetting.Builder()
            .name("scale")
            .description("Changes the size of the tab list.")
            .defaultValue(100.0)
            .min(40.0)
            .max(200.0)
            .sliderMin(40.0)
            .sliderMax(200.0)
            .build()
        );

    public final Setting<Integer> maxPlayers =
        sgDisplay.add(new IntSetting.Builder()
            .name("max-players")
            .description("Maximum number of players shown in the tab list.")
            .defaultValue(200)
            .min(1)
            .max(2000)
            .sliderMin(1)
            .sliderMax(2000)
            .build()
        );

    public final Setting<Integer> columnHeight =
        sgDisplay.add(new IntSetting.Builder()
            .name("column-height")
            .description("Number of players shown in each column.")
            .defaultValue(20)
            .min(1)
            .max(100)
            .sliderMin(1)
            .sliderMax(100)
            .build()
        );

    public final Setting<Boolean> simpleRanks =
        sgDisplay.add(new BoolSetting.Builder()
            .name("simple-ranks")
            .description("Shortens rank names such as [Apex] to [A].")
            .defaultValue(false)
            .build()
        );

    // ============================================================
    // INFORMATION
    // ============================================================

    public final Setting<Boolean> showPing =
        sgInformation.add(new BoolSetting.Builder()
            .name("show-ping")
            .description("Shows each player's ping.")
            .defaultValue(true)
            .build()
        );

    public final Setting<Boolean> showGamemode =
        sgInformation.add(new BoolSetting.Builder()
            .name("show-gamemode")
            .description("Shows each player's gamemode.")
            .defaultValue(false)
            .build()
        );

    public final Setting<Boolean> showPlayerCount =
        sgInformation.add(new BoolSetting.Builder()
            .name("show-player-count")
            .description("Shows the total number of players at the top.")
            .defaultValue(true)
            .build()
        );

    // ============================================================
    // HIGHLIGHT
    // ============================================================

    public final Setting<Boolean> highlightFriends =
        sgHighlight.add(new BoolSetting.Builder()
            .name("highlight-friends")
            .description("Highlights players who are on your Meteor friends list.")
            .defaultValue(true)
            .build()
        );

    public final Setting<Boolean> highlightSelf =
        sgHighlight.add(new BoolSetting.Builder()
            .name("highlight-self")
            .description("Highlights your own name.")
            .defaultValue(true)
            .build()
        );

    public final ColorSetting selfColor =
        sgHighlight.add(new ColorSetting.Builder()
            .name("self-color")
            .description("Color used to highlight your own name.")
            .defaultValue(new Color(0, 255, 255, 255))
            .build()
        );

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public BetterTabPlus() {
        super(
            QuinnAddon.CATEGORY,
            "better-tab-plus",
            "Improves and customizes the Minecraft player tab list."
        );
    }

    // ============================================================
    // MODULE INFO
    // ============================================================

    public String getInfo() {
        return maxPlayers.get() + " players";
    }
}