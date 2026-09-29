package com.example.addon.mixin;

import com.example.addon.modules.BetterTabPlus;

import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(PlayerTabOverlay.class)
public class BetterTabPlusMixin {

    // ============================================================
    // LAYOUT CONSTANTS
    // ============================================================

    private static final int TOP = 32;
    private static final int HEADER_HEIGHT = 12;
    private static final int ROW_HEIGHT = 10;
    private static final int SCREEN_MARGIN = 8;
    private static final int MIN_COLUMN_WIDTH = 90;

    // ============================================================
    // RANK DETECTION
    // ============================================================

    private static final Pattern OWNER_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])owner(?![a-z0-9])");

    private static final Pattern LEGEND_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])legend(?![a-z0-9])");

    private static final Pattern APEX_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])apex(?![a-z0-9])");

    private static final Pattern YOUTUBER_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])(?:youtuber|youtube)(?![a-z0-9])");

    private static final Pattern ELITE_PLUS_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])elite\\s*(?:\\+|plus)(?![a-z0-9])");

    private static final Pattern ELITE_ULTRA_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])elite\\s+ultra(?![a-z0-9])");

    private static final Pattern ELITE_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])elite(?![a-z0-9])");

    private static final Pattern PRIME_ULTRA_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])prime\\s+ultra(?![a-z0-9])");

    private static final Pattern PRIME_PATTERN =
        Pattern.compile("(?i)(?<![a-z0-9])prime(?![a-z0-9])");

    // ============================================================
    // TAB RENDER
    // ============================================================

    @Inject(
        method = "extractRenderState",
        at = @At("HEAD"),
        cancellable = true
    )
    private void renderBetterTab(
        GuiGraphicsExtractor context,
        int width,
        Scoreboard scoreboard,
        Objective objective,
        CallbackInfo ci
    ) {
        BetterTabPlus module =
            Modules.get().get(BetterTabPlus.class);

        if (module == null || !module.isActive()) {
            return;
        }

        Minecraft mc =
            Minecraft.getInstance();

        if (
            mc.player == null
                || mc.level == null
                || mc.getConnection() == null
        ) {
            ci.cancel();
            return;
        }

        // ========================================================
        // GET PLAYERS
        // ========================================================

        List<PlayerInfo> players =
            new ArrayList<>(
                mc.getConnection().getOnlinePlayers()
            );

        if (players.isEmpty()) {
            ci.cancel();
            return;
        }

        int totalPlayerCount =
            players.size();

        // ========================================================
        // SORT PLAYERS
        // ========================================================

        players.sort((a, b) -> {
            int rankA = getRankPriority(a);
            int rankB = getRankPriority(b);

            if (rankA != rankB) {
                return Integer.compare(rankA, rankB);
            }

            return a.getProfile().name()
                .compareToIgnoreCase(
                    b.getProfile().name()
                );
        });

        // ========================================================
        // MAX PLAYERS
        // ========================================================

        int maxPlayers =
            Math.min(
                module.maxPlayers.get(),
                players.size()
            );

        players =
            new ArrayList<>(
                players.subList(0, maxPlayers)
            );

        // ========================================================
        // SCALE
        // ========================================================

        float scale =
            module.scale.get().floatValue() / 100.0f;

        if (scale <= 0.0f) {
            scale = 1.0f;
        }

        context.pose().pushMatrix();

        context.pose().scale(
            scale,
            scale
        );

        // ========================================================
        // SCALED SCREEN SIZE
        // ========================================================

        int scaledWidth =
            (int) (width / scale);

        int scaledHeight =
            (int) (
                mc.getWindow().getGuiScaledHeight()
                    / scale
            );

        // ========================================================
        // AVAILABLE SPACE
        // ========================================================

        int availableWidth =
            Math.max(
                MIN_COLUMN_WIDTH,
                scaledWidth - (SCREEN_MARGIN * 2)
            );

        int availableHeight =
            Math.max(
                ROW_HEIGHT,
                scaledHeight - TOP - SCREEN_MARGIN
            );

        int maximumRowsThatFit =
            Math.max(
                1,
                availableHeight / ROW_HEIGHT
            );

        // ========================================================
        // INITIAL ROW COUNT
        // ========================================================

        int rows =
            Math.min(
                module.columnHeight.get(),
                maximumRowsThatFit
            );

        rows =
            Math.max(
                1,
                Math.min(
                    rows,
                    players.size()
                )
            );

        // ========================================================
        // INITIAL COLUMN COUNT
        // ========================================================

        int columns =
            (int) Math.ceil(
                (double) players.size() / rows
            );

        columns =
            Math.max(
                1,
                columns
            );

        // ========================================================
        // MAXIMUM COLUMNS
        // ========================================================

        int maxColumnsThatFit =
            Math.max(
                1,
                availableWidth / MIN_COLUMN_WIDTH
            );

        if (columns > maxColumnsThatFit) {
            columns = maxColumnsThatFit;

            rows =
                (int) Math.ceil(
                    (double) players.size() / columns
                );
        }

        // ========================================================
        // VERTICAL FIT
        // ========================================================

        while (
            rows > maximumRowsThatFit
                && columns < maxColumnsThatFit
        ) {
            columns++;

            rows =
                (int) Math.ceil(
                    (double) players.size() / columns
                );
        }

        // ========================================================
        // FINAL GRID
        // ========================================================

        columns =
            Math.max(
                1,
                columns
            );

        rows =
            Math.max(
                1,
                (int) Math.ceil(
                    (double) players.size() / columns
                )
            );

        // ========================================================
        // COLUMN WIDTH
        // ========================================================

        int columnWidth =
            Math.max(
                1,
                availableWidth / columns
            );

        int totalWidth =
            columnWidth * columns;

        int left =
            (scaledWidth - totalWidth) / 2;

        // ========================================================
        // PLAYER COUNT HEADER
        // ========================================================

        if (module.showPlayerCount.get()) {
            String playerCountText =
                totalPlayerCount + " Players";

            int playerCountWidth =
                mc.font.width(
                    playerCountText
                );

            int playerCountX =
                (scaledWidth - playerCountWidth) / 2;

            context.text(
                mc.font,
                playerCountText,
                playerCountX,
                TOP - HEADER_HEIGHT,
                0xFFFFFFFF
            );
        }

        // ========================================================
        // RENDER PLAYERS
        // ========================================================

        for (
            int i = 0;
            i < players.size();
            i++
        ) {
            PlayerInfo entry =
                players.get(i);

            int column =
                i / rows;

            int row =
                i % rows;

            int x =
                left + column * columnWidth;

            int y =
                TOP + row * ROW_HEIGHT;

            renderPlayer(
                context,
                mc,
                module,
                entry,
                x,
                y,
                columnWidth
            );
        }

        context.pose().popMatrix();

        // Cancel vanilla tab.
        ci.cancel();
    }

    // ============================================================
    // PLAYER RENDERING
    // ============================================================

    private static void renderPlayer(
        GuiGraphicsExtractor context,
        Minecraft mc,
        BetterTabPlus module,
        PlayerInfo entry,
        int x,
        int y,
        int width
    ) {
        int right =
            x + width - 2;

        // Background
        context.fill(
            x,
            y,
            right,
            y + ROW_HEIGHT,
            0x55000000
        );

        String playerName =
            entry.getProfile().name();

        // Rank
        String rank =
            getRank(entry);

        MutableComponent rankComponent =
            getRankText(
                rank,
                module.simpleRanks.get()
            );

        // Username
        MutableComponent name =
            Component.literal(playerName);

        name.setStyle(
            name.getStyle().withColor(
                getUsernameColor(rank)
            )
        );

        // Self
        boolean self =
            mc.player != null
                && mc.player.getUUID().equals(
                    entry.getProfile().id()
                );

        // Friend
        boolean friend =
            Friends.get().isFriend(entry);

        if (
            self
                && module.highlightSelf.get()
        ) {
            Color color =
                module.selfColor.get();

            name.setStyle(
                name.getStyle().withColor(
                    color.getPacked()
                )
            );
        }

        else if (
            friend
                && module.highlightFriends.get()
        ) {
            name.setStyle(
                name.getStyle().withColor(
                    0xFF55FF55
                )
            );
        }

        // ========================================================
        // PING
        // ========================================================

        String pingText =
            "";

        if (module.showPing.get()) {
            pingText =
                entry.getLatency() + "ms";
        }

        int pingWidth =
            module.showPing.get()
                ? mc.font.width(pingText)
                : 0;

        int pingX =
            right - pingWidth - 4;

        // ========================================================
        // GAMEMODE
        // ========================================================

        String gamemode =
            module.showGamemode.get()
                ? getGamemode(entry)
                : "";

        int gamemodeWidth =
            module.showGamemode.get()
                ? mc.font.width(gamemode)
                : 0;

        int gamemodeX =
            pingX - gamemodeWidth - 6;

        int nameRight;

        if (module.showGamemode.get()) {
            nameRight =
                gamemodeX - 4;
        }

        else if (module.showPing.get()) {
            nameRight =
                pingX - 4;
        }

        else {
            nameRight =
                right - 4;
        }

        int availableNameWidth =
            Math.max(
                10,
                nameRight - x - 3
            );

        // ========================================================
        // RANK WIDTH
        // ========================================================

        int rankWidth =
            mc.font.width(
                rankComponent
            );

        // ========================================================
        // USERNAME WIDTH
        // ========================================================

        int usernameWidth =
            Math.max(
                1,
                availableNameWidth - rankWidth
            );

        // Trim username only
        MutableComponent trimmedName =
            trimUsername(
                mc,
                name,
                usernameWidth
            );

        // Draw rank
        context.text(
            mc.font,
            rankComponent,
            x + 2,
            y,
            0xFFFFFFFF
        );

        // Draw username
        int usernameX =
            x + 2 + rankWidth;

        context.text(
            mc.font,
            trimmedName,
            usernameX,
            y,
            0xFFFFFFFF
        );

        // Draw ping
        if (module.showPing.get()) {
            int ping =
                entry.getLatency();

            context.text(
                mc.font,
                pingText,
                pingX,
                y,
                getPingColor(ping)
            );
        }

        // Draw gamemode
        if (module.showGamemode.get()) {
            context.text(
                mc.font,
                gamemode,
                gamemodeX,
                y,
                0xFFFFFFFF
            );
        }
    }

    // ============================================================
    // RANK TEXT
    // ============================================================

    private static MutableComponent getRankText(
        String rank,
        boolean simple
    ) {
        return switch (rank) {

            // OWNER
            case "owner" -> {
                String text =
                    simple ? "[Ow]" : "[OWNER]";

                MutableComponent result =
                    Component.literal(text);

                result.setStyle(
                    result.getStyle().withColor(
                        0xFFFF5555
                    )
                );

                yield result;
            }

            // LEGEND
            case "legend" -> {
                String text =
                    simple ? "[L]" : "[Legend]";

                MutableComponent result =
                    Component.literal(text);

                result.setStyle(
                    result.getStyle().withColor(
                        0xFFFFFF55
                    )
                );

                yield result;
            }

            // APEX
            case "apex" -> {
                String text =
                    simple ? "[A]" : "[Apex]";

                MutableComponent result =
                    Component.literal(text);

                result.setStyle(
                    result.getStyle().withColor(
                        0xFFFFAA00
                    )
                );

                yield result;
            }

            // YOUTUBER
            case "youtuber" -> {
                MutableComponent result =
                    Component.literal("[");

                result.setStyle(
                    result.getStyle().withColor(
                        0xFFFFFFFF
                    )
                );

                String first =
                    simple ? "Y" : "You";

                String second =
                    simple ? "T]" : "Tuber]";

                MutableComponent you =
                    Component.literal(first);

                you.setStyle(
                    you.getStyle().withColor(
                        0xFFFF5555
                    )
                );

                MutableComponent tuber =
                    Component.literal(second);

                tuber.setStyle(
                    tuber.getStyle().withColor(
                        0xFFAAAAAA
                    )
                );

                result.append(you);
                result.append(tuber);

                yield result;
            }

            // ELITE+
            case "elite+" -> {
                String text =
                    simple ? "[E+]" : "[Elite+]";

                MutableComponent result =
                    Component.literal(text);

                result.setStyle(
                    result.getStyle().withColor(
                        0xFF55AAFF
                    )
                );

                yield result;
            }

            // ELITE ULTRA
            case "elite ultra" -> {
                MutableComponent result =
                    Component.literal("[");

                result.setStyle(
                    result.getStyle().withColor(
                        0xFFFFFFFF
                    )
                );

                String elite =
                    simple ? "E" : "Elite";

                String ultra =
                    simple ? "u]" : " Ultra]";

                MutableComponent eliteText =
                    Component.literal(elite);

                eliteText.setStyle(
                    eliteText.getStyle().withColor(
                        0xFFFFAA00
                    )
                );

                MutableComponent ultraText =
                    Component.literal(ultra);

                ultraText.setStyle(
                    ultraText.getStyle().withColor(
                        0xFFFF5555
                    )
                );

                result.append(eliteText);
                result.append(ultraText);

                yield result;
            }

            // ELITE
            case "elite" -> {
                String text =
                    simple ? "[E]" : "[Elite]";

                MutableComponent result =
                    Component.literal(text);

                result.setStyle(
                    result.getStyle().withColor(
                        0xFFFFFF55
                    )
                );

                yield result;
            }

            // PRIME ULTRA
            case "prime ultra" -> {
                MutableComponent result =
                    Component.literal("[");

                result.setStyle(
                    result.getStyle().withColor(
                        0xFFFFFFFF
                    )
                );

                String prime =
                    simple ? "P" : "Prime";

                String ultra =
                    simple ? "u]" : " Ultra]";

                MutableComponent primeText =
                    Component.literal(prime);

                primeText.setStyle(
                    primeText.getStyle().withColor(
                        0xFF55AAFF
                    )
                );

                MutableComponent ultraText =
                    Component.literal(ultra);

                ultraText.setStyle(
                    ultraText.getStyle().withColor(
                        0xFFFF5555
                    )
                );

                result.append(primeText);
                result.append(ultraText);

                yield result;
            }

            // PRIME - CYAN RGB(0, 255, 255)
            case "prime" -> {
                String text =
                    simple ? "[P]" : "[Prime]";

                MutableComponent result =
                    Component.literal(text);

                result.setStyle(
                    result.getStyle().withColor(
                        0xFF00FFFF
                    )
                );

                yield result;
            }

            default ->
                Component.empty();
        };
    }

    // ============================================================
    // USERNAME COLOR
    // ============================================================

    private static int getUsernameColor(
        String rank
    ) {
        return switch (rank) {

            // OWNER - WHITE
            case "owner" ->
                0xFFFFFFFF;

            // LEGEND - WHITE
            case "legend" ->
                0xFFFFFFFF;

            // APEX - ORANGE
            case "apex" ->
                0xFFFFAA00;

            // YOUTUBER - YELLOW
            case "youtuber" ->
                0xFFFFFF55;

            // ELITE+ - WHITE
            case "elite+" ->
                0xFFFFFFFF;

            // ELITE + ELITE ULTRA - CYAN RGB(0, 255, 255)
            case "elite",
                 "elite ultra" ->
                0xFF00FFFF;

            // PRIME ULTRA - BLUE
            case "prime ultra" ->
                0xFF55AAFF;

            // PRIME - WHITE
            case "prime" ->
                0xFFFFFFFF;

            // UNRANKED - RGB(200, 200, 200)
            default ->
                0xFFC8C8C8;
        };
    }

    // ============================================================
    // RANK PRIORITY
    // ============================================================

    private static int getRankPriority(
        PlayerInfo entry
    ) {
        String rank =
            getRank(entry);

        return switch (rank) {

            case "owner" -> 0;
            case "legend" -> 1;
            case "apex" -> 2;
            case "youtuber" -> 3;
            case "elite+" -> 4;
            case "elite ultra" -> 5;
            case "elite" -> 6;
            case "prime ultra" -> 7;
            case "prime" -> 8;

            default -> 9;
        };
    }

    // ============================================================
    // GET RANK
    // ============================================================

    private static String getRank(
        PlayerInfo entry
    ) {
        // Scoreboard team prefix
        if (
            entry.getTeam() != null
        ) {
            String prefix =
                entry.getTeam()
                    .getPlayerPrefix()
                    .getString();

            String rank =
                detectRank(prefix);

            if (!rank.isEmpty()) {
                return rank;
            }
        }

        // Display name
        Component displayName =
            entry.getTabListDisplayName();

        if (displayName != null) {
            String display =
                displayName.getString();

            String rank =
                detectRankFromDisplayName(
                    display
                );

            if (!rank.isEmpty()) {
                return rank;
            }
        }

        // Team name
        if (
            entry.getTeam() != null
        ) {
            String teamName =
                entry.getTeam()
                    .getName();

            String rank =
                detectExactRank(teamName);

            if (!rank.isEmpty()) {
                return rank;
            }
        }

        return "";
    }

    // ============================================================
    // DETECT RANK FROM DISPLAY NAME
    // ============================================================

    private static String detectRankFromDisplayName(
        String text
    ) {
        if (
            text == null
                || text.isEmpty()
        ) {
            return "";
        }

        String trimmed =
            text.trim();

        if (
            startsWithRankPrefix(
                trimmed,
                "owner"
            )
        ) {
            return "owner";
        }

        if (
            startsWithRankPrefix(
                trimmed,
                "legend"
            )
        ) {
            return "legend";
        }

        if (
            startsWithRankPrefix(
                trimmed,
                "apex"
            )
        ) {
            return "apex";
        }

        if (
            startsWithRankPrefix(
                trimmed,
                "youtuber"
            )
            || startsWithRankPrefix(
                trimmed,
                "youtube"
            )
        ) {
            return "youtuber";
        }

        if (
            startsWithRankPrefix(
                trimmed,
                "elite+"
            )
            || startsWithRankPrefix(
                trimmed,
                "elite plus"
            )
        ) {
            return "elite+";
        }

        if (
            startsWithRankPrefix(
                trimmed,
                "elite ultra"
            )
        ) {
            return "elite ultra";
        }

        if (
            startsWithRankPrefix(
                trimmed,
                "elite"
            )
        ) {
            return "elite";
        }

        if (
            startsWithRankPrefix(
                trimmed,
                "prime ultra"
            )
        ) {
            return "prime ultra";
        }

        if (
            startsWithRankPrefix(
                trimmed,
                "prime"
            )
        ) {
            return "prime";
        }

        return "";
    }

    // ============================================================
    // STARTS WITH RANK PREFIX
    // ============================================================

    private static boolean startsWithRankPrefix(
        String text,
        String rank
    ) {
        String lower =
            text.toLowerCase(
                Locale.ROOT
            );

        String normalizedRank =
            rank.toLowerCase(
                Locale.ROOT
            );

        if (
            lower.startsWith(
                "[" + normalizedRank + "]"
            )
        ) {
            return true;
        }

        if (
            lower.startsWith(
                "<" + normalizedRank + ">"
            )
        ) {
            return true;
        }

        if (
            lower.startsWith(
                "(" + normalizedRank + ")"
            )
        ) {
            return true;
        }

        if (
            lower.startsWith(
                normalizedRank
            )
        ) {
            int length =
                normalizedRank.length();

            if (
                lower.length() == length
            ) {
                return true;
            }

            char next =
                lower.charAt(length);

            return !Character.isLetterOrDigit(
                next
            );
        }

        return false;
    }

    // ============================================================
    // DETECT EXACT RANK
    // ============================================================

    private static String detectExactRank(
        String text
    ) {
        if (
            text == null
                || text.isEmpty()
        ) {
            return "";
        }

        String normalized =
            text.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if (
            normalized.startsWith("[")
                && normalized.endsWith("]")
        ) {
            normalized =
                normalized.substring(
                    1,
                    normalized.length() - 1
                ).trim();
        }

        if (
            normalized.startsWith("<")
                && normalized.endsWith(">")
        ) {
            normalized =
                normalized.substring(
                    1,
                    normalized.length() - 1
                ).trim();
        }

        if (
            normalized.startsWith("(")
                && normalized.endsWith(")")
        ) {
            normalized =
                normalized.substring(
                    1,
                    normalized.length() - 1
                ).trim();
        }

        return switch (normalized) {

            case "owner" ->
                "owner";

            case "legend" ->
                "legend";

            case "apex" ->
                "apex";

            case "youtuber",
                 "youtube" ->
                "youtuber";

            case "elite+",
                 "elite plus" ->
                "elite+";

            case "elite ultra" ->
                "elite ultra";

            case "elite" ->
                "elite";

            case "prime ultra" ->
                "prime ultra";

            case "prime" ->
                "prime";

            default ->
                "";
        };
    }

    // ============================================================
    // DETECT RANK IN PREFIX
    // ============================================================

    private static String detectRank(
        String text
    ) {
        if (
            text == null
                || text.isEmpty()
        ) {
            return "";
        }

        // More specific ranks first.

        if (
            matches(
                ELITE_PLUS_PATTERN,
                text
            )
        ) {
            return "elite+";
        }

        if (
            matches(
                ELITE_ULTRA_PATTERN,
                text
            )
        ) {
            return "elite ultra";
        }

        if (
            matches(
                PRIME_ULTRA_PATTERN,
                text
            )
        ) {
            return "prime ultra";
        }

        if (
            matches(
                OWNER_PATTERN,
                text
            )
        ) {
            return "owner";
        }

        if (
            matches(
                LEGEND_PATTERN,
                text
            )
        ) {
            return "legend";
        }

        if (
            matches(
                APEX_PATTERN,
                text
            )
        ) {
            return "apex";
        }

        if (
            matches(
                YOUTUBER_PATTERN,
                text
            )
        ) {
            return "youtuber";
        }

        if (
            matches(
                ELITE_PATTERN,
                text
            )
        ) {
            return "elite";
        }

        if (
            matches(
                PRIME_PATTERN,
                text
            )
        ) {
            return "prime";
        }

        return "";
    }

    // ============================================================
    // PATTERN MATCH
    // ============================================================

    private static boolean matches(
        Pattern pattern,
        String text
    ) {
        Matcher matcher =
            pattern.matcher(text);

        return matcher.find();
    }

    // ============================================================
    // TRIM USERNAME ONLY
    // ============================================================

    private static MutableComponent trimUsername(
        Minecraft mc,
        MutableComponent name,
        int maxWidth
    ) {
        if (
            maxWidth <= 0
        ) {
            return Component.empty();
        }

        if (
            mc.font.width(name)
                <= maxWidth
        ) {
            return name;
        }

        String plain =
            name.getString();

        String suffix =
            "...";

        int suffixWidth =
            mc.font.width(
                suffix
            );

        int allowed =
            Math.max(
                1,
                maxWidth - suffixWidth
            );

        StringBuilder result =
            new StringBuilder();

        for (
            int i = 0;
            i < plain.length();
            i++
        ) {
            String candidate =
                result.toString()
                    + plain.charAt(i);

            if (
                mc.font
                    .width(candidate)
                    > allowed
            ) {
                break;
            }

            result.append(
                plain.charAt(i)
            );
        }

        MutableComponent trimmed =
            Component.literal(
                result + suffix
            );

        trimmed.setStyle(
            name.getStyle()
        );

        return trimmed;
    }

    // ============================================================
    // PING COLOR
    // ============================================================

    private static int getPingColor(
        int ping
    ) {
        if (ping < 75) {
            return 0xFF55FF55;
        }

        if (ping < 150) {
            return 0xFFFFFF55;
        }

        if (ping < 250) {
            return 0xFFFFAA00;
        }

        return 0xFFFF5555;
    }

    // ============================================================
    // GAMEMODE
    // ============================================================

    private static String getGamemode(
        PlayerInfo entry
    ) {
        if (
            entry.getGameMode() == null
        ) {
            return "?";
        }

        return switch (
            entry.getGameMode()
        ) {
            case SURVIVAL ->
                "Survival";

            case CREATIVE ->
                "Creative";

            case ADVENTURE ->
                "Adventure";

            case SPECTATOR ->
                "Spectator";
        };
    }
}