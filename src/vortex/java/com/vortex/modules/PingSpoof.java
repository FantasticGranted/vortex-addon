package com.vortex.modules;

import com.vortex.Vortex;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;

import java.util.ArrayDeque;
import java.util.Deque;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class PingSpoof extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("How much to fake your ping by in milliseconds.")
        .defaultValue(200)
        .min(0)
        .max(5000)
        .sliderRange(0, 2000)
        .build()
    );

    private static final class Pending {
        final Packet<?> packet;
        final long readyAt;

        Pending(Packet<?> packet, long readyAt) {
            this.packet = packet;
            this.readyAt = readyAt;
        }
    }

    private final Deque<Pending> pending = new ArrayDeque<>();

    public PingSpoof() {
        super(Vortex.CATEGORY, "ping-spoof", "Fakes your ping by delaying keepalive responses.");
    }

    @Override
    public void onActivate() {
        pending.clear();
    }

    @Override
    public void onDeactivate() {
        flush();
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!(event.packet instanceof ServerboundKeepAlivePacket)) return;

        int ms = delay.get();
        if (ms <= 0) return;

        event.setCancelled(true);
        pending.add(new Pending(event.packet, System.currentTimeMillis() + ms));
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        flush();
    }

    private void flush() {
        if (pending.isEmpty()) return;

        long now = System.currentTimeMillis();

        while (!pending.isEmpty() && pending.peek().readyAt <= now) {
            Pending p = pending.poll();

            if (mc.getConnection() == null) continue;

            new PacketEvent.Send(p.packet, mc.getConnection().getConnection()).sendSilently(p.packet);
        }
    }
}