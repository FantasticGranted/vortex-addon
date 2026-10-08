package com.vortex.modules;

import com.vortex.Vortex;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;

import java.util.List;

public class CleanGui extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgHudElements = settings.createGroup("HUD Elements");
    private final SettingGroup sgMinimal = settings.createGroup("Minimal Mode");

    private final Setting<Boolean> hideInMenus = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-in-menus")
        .description("Hides the Meteor HUD when in inventory screens or game menus.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> minimalMode = sgMinimal.add(new BoolSetting.Builder()
        .name("minimal-mode")
        .description("Enables minimal mode - hides most HUD elements for a cleaner look.")
        .defaultValue(false)
        .onChanged(this::onMinimalModeChanged)
        .build()
    );

    private final Setting<Boolean> hideArmorHud = sgHudElements.add(new BoolSetting.Builder()
        .name("hide-armor-hud")
        .description("Hides the armor HUD element.")
        .defaultValue(false)
        .visible(() -> !minimalMode.get())
        .build()
    );

    private final Setting<Boolean> hideInventoryHud = sgHudElements.add(new BoolSetting.Builder()
        .name("hide-inventory-hud")
        .description("Hides the inventory HUD element.")
        .defaultValue(false)
        .visible(() -> !minimalMode.get())
        .build()
    );

    private final Setting<Boolean> hideActiveModulesHud = sgHudElements.add(new BoolSetting.Builder()
        .name("hide-active-modules-hud")
        .description("Hides the active modules HUD element.")
        .defaultValue(false)
        .visible(() -> !minimalMode.get())
        .build()
    );

    private final Setting<Boolean> hideCompassHud = sgHudElements.add(new BoolSetting.Builder()
        .name("hide-compass-hud")
        .description("Hides the compass HUD element.")
        .defaultValue(false)
        .visible(() -> !minimalMode.get())
        .build()
    );

    private final Setting<Boolean> hideCoordinatesHud = sgHudElements.add(new BoolSetting.Builder()
        .name("hide-coordinates-hud")
        .description("Hides the coordinates HUD element.")
        .defaultValue(false)
        .visible(() -> !minimalMode.get())
        .build()
    );

    private final Setting<Boolean> hideFpsHud = sgHudElements.add(new BoolSetting.Builder()
        .name("hide-fps-hud")
        .description("Hides the FPS HUD element.")
        .defaultValue(false)
        .visible(() -> !minimalMode.get())
        .build()
    );

    private final Setting<Boolean> hideLagNotifierHud = sgHudElements.add(new BoolSetting.Builder()
        .name("hide-lag-notifier-hud")
        .description("Hides the lag notifier HUD element.")
        .defaultValue(false)
        .visible(() -> !minimalMode.get())
        .build()
    );

    private final Setting<Boolean> hidePotionTimersHud = sgHudElements.add(new BoolSetting.Builder()
        .name("hide-potion-timers-hud")
        .description("Hides the potion timers HUD element.")
        .defaultValue(false)
        .visible(() -> !minimalMode.get())
        .build()
    );

    private final Setting<Boolean> hideTextHud = sgHudElements.add(new BoolSetting.Builder()
        .name("hide-text-hud")
        .description("Hides custom text HUD elements.")
        .defaultValue(false)
        .visible(() -> !minimalMode.get())
        .build()
    );

    private final Setting<Boolean> hideItemHud = sgHudElements.add(new BoolSetting.Builder()
        .name("hide-item-hud")
        .description("Hides the item HUD element.")
        .defaultValue(false)
        .visible(() -> !minimalMode.get())
        .build()
    );

    private final Setting<Keybind> toggleMinimal = sgGeneral.add(new KeybindSetting.Builder()
        .name("toggle-minimal")
        .description("Keybind to toggle minimal mode.")
        .defaultValue(Keybind.none())
        .build()
    );

    public CleanGui() {
        super(Vortex.CATEGORY, "clean-gui", "Reduces GUI clutter by hiding HUD elements and providing minimal mode.");
    }

    @Override
    public void onActivate() {
        if (minimalMode.get()) {
            applyMinimalMode(true);
        }
        updateHudVisibility();
    }

    @Override
    public void onDeactivate() {
        if (minimalMode.get()) {
            applyMinimalMode(false);
        }
        updateHudVisibility();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (toggleMinimal.get().isPressed()) {
            minimalMode.set(!minimalMode.get());
        }
    }

    private void onMinimalModeChanged(boolean value) {
        if (isActive()) {
            applyMinimalMode(value);
        }
    }

    private void applyMinimalMode(boolean enable) {
        Hud hud = Hud.get();

        for (HudElement element : hud) {
            if (enable) {
                if (shouldHideInMinimal(element)) {
                    Setting<?> activeSetting = element.settings.get("active");
                    if (activeSetting instanceof BoolSetting boolSetting) {
                        boolSetting.set(false);
                    }
                }
            } else {
                Setting<?> activeSetting = element.settings.get("active");
                if (activeSetting instanceof BoolSetting boolSetting) {
                    boolSetting.set(true);
                }
            }
        }
    }

    private boolean shouldHideInMinimal(HudElement element) {
        String name = element.getClass().getSimpleName();
        return switch (name) {
            case "ArmorHud", "InventoryHud", "ActiveModulesHud", "CompassHud",
                 "CoordinatesHud", "FpsHud", "LagNotifierHud", "PotionTimersHud",
                 "TextHud", "ItemHud", "KeyboardHud", "HoleHud", "PlayerModelHud",
                 "PlayerRadarHud", "CombatHud", "ModuleInfosHud" -> true;
            default -> false;
        };
    }

    private void updateHudVisibility() {
        Hud hud = Hud.get();

        for (HudElement element : hud) {
            String name = element.getClass().getSimpleName();
            boolean shouldHide = false;

            if (minimalMode.get()) {
                shouldHide = shouldHideInMinimal(element);
            } else {
                shouldHide = switch (name) {
                    case "ArmorHud" -> hideArmorHud.get();
                    case "InventoryHud" -> hideInventoryHud.get();
                    case "ActiveModulesHud" -> hideActiveModulesHud.get();
                    case "CompassHud" -> hideCompassHud.get();
                    case "CoordinatesHud" -> hideCoordinatesHud.get();
                    case "FpsHud" -> hideFpsHud.get();
                    case "LagNotifierHud" -> hideLagNotifierHud.get();
                    case "PotionTimersHud" -> hidePotionTimersHud.get();
                    case "TextHud" -> hideTextHud.get();
                    case "ItemHud" -> hideItemHud.get();
                    default -> false;
                };
            }

            Setting<?> activeSetting = element.settings.get("active");
            if (activeSetting instanceof BoolSetting boolSetting) {
                boolean currentActive = boolSetting.get();
                if (shouldHide != !currentActive) {
                    boolSetting.set(!shouldHide);
                }
            }
        }
    }

    @Override
    public String getInfoString() {
        return minimalMode.get() ? "Minimal" : "Custom";
    }
}