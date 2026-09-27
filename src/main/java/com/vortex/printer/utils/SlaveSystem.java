package com.vortex.printer.utils;

import com.vortex.printer.interfaces.MapPrinter;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class SlaveSystem {

    public static int commandDelay = 0;
    public static String directMessageCommand = "w";
    public static String senderPrefix = "";
    public static String senderSuffix = "";
    public static int randomLength = 0;
    public static ArrayList<String> slaves = new ArrayList<>();
    public static HashMap<String, Boolean> activeSlavesDict = new HashMap<>();
    public static HashMap<String, Boolean> finishedSlavesDict = new HashMap<>();
    public static SlaveTableController tableController = null;

    private static MapPrinter printerModule = null;
    private static int timeout = 0;
    private static ArrayList<String> toBeSentMessages = new ArrayList<>();
    private static ArrayList<String> toBeConfirmedSlaves = new ArrayList<>();
    private static String master = null;

    private static final Random RANDOM = new Random();
    private static final char[] RANDOM_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    public static void setupSlaveSystem(MapPrinter module, int delay, String dmCommand, String prefix, String suffix, int randomSuffixLength) {
        printerModule = module;
        commandDelay = delay;
        directMessageCommand = dmCommand;
        senderPrefix = prefix;
        senderSuffix = suffix;
        randomLength = randomSuffixLength;
        slaves.clear();
        toBeSentMessages.clear();
        toBeConfirmedSlaves.clear();
        activeSlavesDict.clear();
        finishedSlavesDict.clear();
        master = null;
    }

    public static void queueMasterDM(String message) {
        if (master != null) {
            queueDM(master, message);
        }
    }

    public static void queueDM(String recipient, String message) {
        toBeSentMessages.add(directMessageCommand + " " + recipient + " " + message);
    }

    public static boolean allSlavesFinished() {
        for (String slave : finishedSlavesDict.keySet()) {
            if (!finishedSlavesDict.get(slave)) return false;
        }
        return true;
    }

    public static void setAllSlavesUnfinished() {
        for (String slave : finishedSlavesDict.keySet()) {
            finishedSlavesDict.put(slave, false);
        }
    }

    public static boolean isSlave() {
        return master != null;
    }

    public static void sendToAllSlaves(String message) {
        for (String slave : slaves) {
            queueDM(slave, message);
        }
    }

    public static void startAllSlaves() {
        for (String slave : activeSlavesDict.keySet()) {
            if (!activeSlavesDict.get(slave)) {
                queueDM(slave, "start");
                activeSlavesDict.put(slave, true);
            }
        }
        if (printerModule != null && !printerModule.isActive() && !printerModule.getActivationReset())
            printerModule.toggle();
    }

    public static void pauseAllSlaves() {
        sendToAllSlaves("pause");
        for (String slave : activeSlavesDict.keySet()) {
            activeSlavesDict.put(slave, false);
        }
        if (printerModule != null && printerModule.isActive() && !printerModule.getActivationReset())
            printerModule.toggle();
    }

    public static void skipNextBuilding() {
        sendToAllSlaves("skip");
        if (printerModule != null) printerModule.skipBuilding();
    }

    public static void generateIntervals() {
        int sectionSize = (int) Math.ceil((float) 128 / (float) (slaves.size() + 1));
        ArrayList<Pair<Integer, Integer>> intervals = new ArrayList<>();
        for (int end = 127; end >= 0; end -= sectionSize) {
            int start = Math.max(0, end - sectionSize + 1);
            intervals.add(new Pair<>(start, end));
        }
        Collections.reverse(intervals);
        printerModule.setInterval(intervals.remove((intervals.size() - 1) / 2));

        toBeSentMessages.removeIf(message -> message.contains("interval"));

        ArrayList<String> sortedSlaves = new ArrayList<>(slaves);
        Collections.sort(sortedSlaves, String.CASE_INSENSITIVE_ORDER);

        for (int i = 0; i < intervals.size(); i++) {
            String slave = sortedSlaves.get(i);
            queueDM(slave, "interval:" + intervals.get(i).getLeft() + ":" + intervals.get(i).getRight());
        }
    }

    public static void registerSlaves() {
        if (printerModule == null) {
            ChatUtils.warning("The module needs to be enabled to register new slaves.");
            return;
        }
        ArrayList<String> foundPlayers = new ArrayList<>();
        if (mc.level != null) {
            for (var player : mc.level.players()) {
                if (!mc.player.equals(player)) {
                    foundPlayers.add(player.getName().getString());
                }
            }
        }
        if (foundPlayers.isEmpty()) {
            ChatUtils.warning("No players found in render distance.");
        }
        toBeConfirmedSlaves = foundPlayers;
        for (String slave : foundPlayers) {
            if (slaves.contains(slave)) continue;
            queueDM(slave, "register");
        }
    }

    public static void removeSlave(String slave) {
        slaves.remove(slave);
        activeSlavesDict.remove(slave);
        finishedSlavesDict.remove(slave);
        queueDM(slave, "remove");
        generateIntervals();
    }

    public static boolean canSeePlayer(String playerName) {
        if (mc.level == null) return false;
        for (var player : mc.level.players()) {
            if (player.getName().getString().equals(playerName)) {
                return true;
            }
        }
        return false;
    }

    private static void handleMessage(String rawMessage, String sender) {
        String content;
        if (sender != null) {
            content = rawMessage;
        } else {
            int prefixIndex = rawMessage.indexOf(senderPrefix);
            int suffixIndex = rawMessage.indexOf(senderSuffix);
            if (prefixIndex == -1 || suffixIndex == -1) return;

            sender = rawMessage.substring(prefixIndex + senderPrefix.length(), suffixIndex);
            if (sender.equals(mc.player.getName().getString())) return;
            content = rawMessage.substring(suffixIndex + senderSuffix.length());
        }

        String[] colonSplit = content.replace(" ", "").split(":");
        String command = colonSplit[0];
        if (command.equals("register") && master == null && toBeConfirmedSlaves.isEmpty()
            && slaves.isEmpty() && canSeePlayer(sender)) {
            master = sender;
            queueMasterDM("accept");
        }
        if (sender.equals(master)) {
            switch (command) {
                case "interval":
                    if (colonSplit.length < 3) break;
                    Pair<Integer, Integer> interval = new Pair<>(Integer.valueOf(colonSplit[1]), Integer.valueOf(colonSplit[2]));
                    printerModule.setInterval(interval);
                    break;
                case "pause":
                    printerModule.pause();
                    break;
                case "start":
                    printerModule.start();
                    break;
                case "remove":
                    master = null;
                    printerModule.toggle();
                    break;
                case "skip":
                    printerModule.skipBuilding();
                    break;
                case "mine":
                    if (colonSplit.length < 2) break;
                    printerModule.mineLine(Integer.valueOf(colonSplit[1]));
            }
        }
        if (slaves.contains(sender) || toBeConfirmedSlaves.contains(sender)) {
            switch (command) {
                case "accept":
                    slaves.add(sender);
                    finishedSlavesDict.put(sender, false);
                    activeSlavesDict.put(sender, false);
                    toBeConfirmedSlaves.remove(sender);
                    ChatUtils.info("Registered slave: " + sender + " Total slaves: " + slaves.size());
                    generateIntervals();
                    if (tableController != null) tableController.rebuild();
                    break;
                case "finished":
                    finishedSlavesDict.put(sender, true);
                    activeSlavesDict.put(sender, false);
                    printerModule.slaveFinished(sender);
                    if (tableController != null) tableController.rebuild();
                    break;
                case "error":
                    if (colonSplit.length < 3) break;
                    BlockPos relativeErrorPos = new BlockPos(Integer.valueOf(colonSplit[1]), 0, Integer.valueOf(colonSplit[2]));
                    printerModule.addError(relativeErrorPos);
                    break;
            }
        }
    }

    @EventHandler
    private static void onReceivePacket(PacketEvent.Receive event) {
        if (printerModule == null) return;

        if (event.packet instanceof ClientboundPlayerChatPacket packet) {
            String senderName = "";
            if (mc.getConnection() != null) {
                PlayerInfo info = mc.getConnection().getPlayerInfo(packet.sender());
                if (info != null) senderName = info.getProfile().name();
            }
            handleMessage(packet.body().content(), senderName);
        }

        if (event.packet instanceof ClientboundSystemChatPacket packet) {
            handleMessage(packet.content().getString(), null);
        }
    }

    @EventHandler
    private static void onTick(TickEvent.Pre event) {
        if (mc.getConnection() == null) return;
        if (timeout > 0) timeout--;
        if (!toBeSentMessages.isEmpty()) {
            if (timeout <= 0) {
                String message = toBeSentMessages.remove(0);
                if (randomLength > 0) {
                    StringBuilder sb = new StringBuilder(randomLength);
                    for (int i = 0; i < randomLength; i++) {
                        sb.append(RANDOM_CHARS[RANDOM.nextInt(RANDOM_CHARS.length)]);
                    }
                    message += ":" + sb;
                }
                mc.getConnection().sendChat(message);
                timeout = commandDelay;
            }
        }
    }
}
