package com.vortex.modules;

import com.vortex.Vortex;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class TotemTracker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgChat = settings.createGroup("Chat");

    private final Setting<Integer> estimateStart = sgGeneral.add(new IntSetting.Builder()
        .name("estimate-start")
        .description("How many totems a fresh player is assumed to carry.")
        .defaultValue(17)
        .min(1)
        .sliderRange(1, 64)
        .build()
    );

    private final Setting<Boolean> nametags = sgGeneral.add(new BoolSetting.Builder()
        .name("nametags")
        .description("Shows estimated totems left above player nametags.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> nametagScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("nametag-scale")
        .description("Scale of the totem estimate text.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderRange(0.5, 3)
        .build()
    );

    private final Setting<Double> killWindow = sgGeneral.add(new DoubleSetting.Builder()
        .name("kill-window")
        .description("Seconds after your melee hit or crystal placement that a death counts as your kill.")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 15)
        .build()
    );

    private final Setting<Boolean> popDeathMessage = sgChat.add(new BoolSetting.Builder()
        .name("pop-death-message")
        .description("Chat message showing how many totems someone popped to death.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoGG = sgChat.add(new BoolSetting.Builder()
        .name("auto-gg")
        .description("Sends the gg format when someone you damaged pops to death.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> ggFormat = sgChat.add(new StringSetting.Builder()
        .name("gg-format")
        .description("Command sent on kill. {name} is replaced with the killed player's name.")
        .defaultValue("/w {name} GG")
        .build()
    );

    private static final int TOTEM_EVENT = 35;

    private final Map<UUID, Integer> pops = new HashMap<>();
    private final Map<UUID, Long> lastMelee = new HashMap<>();
    private long lastCrystalPlace;

    public TotemTracker() {
        super(Vortex.CATEGORY, "totem-tracker", "Estimates totems left on nametags, tracks pops and kills, auto GG.");
    }

    @Override
    public void onActivate() {
        clear();
    }

    @Override
    public void onDeactivate() {
        clear();
    }

    private void clear() {
        pops.clear();
        lastMelee.clear();
        lastCrystalPlace = 0;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.level == null) return;

        if (event.packet instanceof ClientboundEntityEventPacket packet && packet.getEventId() == TOTEM_EVENT) {
            Entity entity = packet.getEntity(mc.level);
            if (entity instanceof Player player && player != mc.player) {
                pops.merge(player.getUUID(), 1, Integer::sum);
            }
        } else if (event.packet instanceof ClientboundPlayerCombatKillPacket packet) {
            onPlayerDeath(packet.playerId());
        }
    }

    private void onPlayerDeath(int entityId) {
        if (mc.level == null) return;

        Player dead = null;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p.getId() == entityId) {
                dead = p;
                break;
            }
        }
        if (dead == null || dead == mc.player) return;

        UUID uuid = dead.getUUID();
        String name = dead.getName().getString();
        Integer prev = pops.remove(uuid);
        int popped = prev == null ? 0 : prev;
        boolean mine = isMyKill(uuid);
        lastMelee.remove(uuid);

        if (popDeathMessage.get() && popped > 0) {
            info("%s popped %d totem%s to death", name, popped, popped == 1 ? "" : "s");
        }
        if (autoGG.get() && mine) sendGG(name);
    }

    private boolean isMyKill(UUID uuid) {
        long window = (long) (killWindow.get() * 1000);
        long now = System.currentTimeMillis();
        Long hit = lastMelee.get(uuid);
        return hit != null && now - hit <= window || lastCrystalPlace != 0 && now - lastCrystalPlace <= window;
    }

    private void sendGG(String name) {
        String cmd = ggFormat.get().replace("{name}", name);
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.isEmpty() || mc.getConnection() == null) return;
        mc.getConnection().sendCommand(cmd);
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (event.entity instanceof Player player && player != mc.player) {
            lastMelee.put(player.getUUID(), System.currentTimeMillis());
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundUseItemOnPacket && holdingCrystal()) {
            lastCrystalPlace = System.currentTimeMillis();
        }
    }

    private boolean holdingCrystal() {
        return mc.player != null
            && (mc.player.getMainHandItem().getItem() == Items.END_CRYSTAL || mc.player.getOffhandItem().getItem() == Items.END_CRYSTAL);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!nametags.get() || mc.level == null || mc.gui.hud.isHidden()) return;

        TextRenderer text = TextRenderer.get();
        if (text.isBuilding()) return;

        double scale = nametagScale.get();
        boolean begun = false;

        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player) continue;

            int popped = pops.getOrDefault(p.getUUID(), 0);
            int est = Math.max(0, estimateStart.get() - popped);
            String label = "~" + est;

            Vector3d pos = new Vector3d(p.getX(), p.getY() + p.getBbHeight() + 0.85, p.getZ());
            if (!NametagUtils.to2D(pos, scale)) continue;

            if (!begun) {
                text.begin(event.graphics, scale);
                begun = true;
            }

            double width = text.getWidth(label, true);
            Color color = est == 0 ? new Color(255, 80, 80) : popped > 0 ? new Color(255, 190, 60) : new Color(255, 255, 255);

            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.quad(pos.x - width / 2 - 2, pos.y - 1, width + 4, text.getHeight() + 2, new Color(0, 0, 0, 120));
            Renderer2D.COLOR.render();

            text.render(label, pos.x - width / 2, pos.y, color, true);
        }

        if (begun) text.end();
    }
}
