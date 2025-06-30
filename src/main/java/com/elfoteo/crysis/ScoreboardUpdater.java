package com.elfoteo.crysis;

import com.elfoteo.crysis.flag.CTFData;
import com.elfoteo.crysis.flag.FlagInfo;
import com.elfoteo.crysis.flag.Team;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.*;

/**
 * Updates a sidebar Objective called "TeamScores" with:
 *   • Team RED / BLUE scores
 *   • A little "FLAGS" subheader
 *   • One line per Flag: [🚩 FlagName          <OWNER>]
 *
 * If CTF is disabled, clears the scoreboard and stops updating.
 */
@EventBusSubscriber(modid = CrysisMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ScoreboardUpdater {
    private static int tickCounter = 0;
    private static MinecraftServer server;

    private static final String OBJECTIVE_NAME = "TeamScores";

    // Unicode symbols
    private static final String TROPHY_UNICODE = "\uD83C\uDFC6 ";
    private static final String FLAG_UNICODE   = "\uD83D\uDEA9 "; // trailing space

    // “Character‐count” width for centering and team‐lines
    private static final int MAX_WIDTH = 20;

    public static void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        Scoreboard scoreboard = server.getScoreboard();

        // Remove existing objective if present
        Objective existing = scoreboard.getObjective(OBJECTIVE_NAME);
        if (existing != null) {
            scoreboard.removeObjective(existing);
        }

        // Create a new sidebar objective; title under 20 chars
        Objective teamScores = scoreboard.addObjective(
                OBJECTIVE_NAME,
                ObjectiveCriteria.DUMMY,
                Component.literal(TROPHY_UNICODE + "CRYSIS CTF").withStyle(ChatFormatting.GOLD),
                RenderType.INTEGER,
                true,
                null
        );

        // Display on the right‐hand sidebar
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, teamScores);
    }

    @SubscribeEvent
    public static void onServerTick(Post event) {
        tickCounter++;
        // Update every 5 ticks
        if (tickCounter % 5 == 0 && server != null) {
            updateScoreboard(server.getScoreboard());
        }
    }

    private static void updateScoreboard(Scoreboard scoreboard) {
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) return;

        CTFData data = CTFData.getOrCreate(level);

        // If CTF is disabled, clear the objective and stop updating
        if (!data.isEnabled()) {
            Objective existing = scoreboard.getObjective(OBJECTIVE_NAME);
            if (existing != null) {
                scoreboard.removeObjective(existing);
            }
            return;
        }

        // Ensure objective exists (in case it was removed while disabled)
        Objective teamScores = scoreboard.getObjective(OBJECTIVE_NAME);
        if (teamScores == null) {
            teamScores = scoreboard.addObjective(
                    OBJECTIVE_NAME,
                    ObjectiveCriteria.DUMMY,
                    Component.literal(TROPHY_UNICODE + "CRYSIS CTF").withStyle(ChatFormatting.GOLD),
                    RenderType.INTEGER,
                    true,
                    null
            );
            scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, teamScores);
        }

        var ordered = new ArrayList<OrderEntry>();

        // ─── “TEAMS” Header ──────────────────────────────────────────────────────────────────
        String teamsPattern = getAnimatedHeader("TEAMS", tickCounter);
        String teamsHeaderCentered = centerText(teamsPattern);
        ordered.add(new OrderEntry(
                ChatFormatting.GOLD + teamsHeaderCentered + ChatFormatting.RESET,
                0
        ));

        // Team RED line
        ordered.add(new OrderEntry(formatTeamLine(Team.RED, data.getRedScore()), 1));
        // Team BLUE line
        ordered.add(new OrderEntry(formatTeamLine(Team.BLUE, data.getBlueScore()), 2));

        // ─── “FLAGS” Header ──────────────────────────────────────────────────────────────────
        String flagsPattern = getAnimatedHeader("FLAGS", tickCounter);
        String flagsHeaderCentered = centerText(flagsPattern);
        ordered.add(new OrderEntry(
                ChatFormatting.AQUA + flagsHeaderCentered + ChatFormatting.RESET,
                3
        ));

        // ─── One line per FlagInfo ─────────────────────────────────────────────────────────────
        List<FlagInfo> allFlags = new ArrayList<>(data.getFlags().stream().toList());
        allFlags.sort(Comparator.comparing(flag -> {
            String name = flag.getName();
            return (name == null || name.isEmpty()) ? "" : name;
        }));

        for (FlagInfo info : allFlags) {
            String name  = info.getName();
            if (name == null || name.isEmpty()) {
                name = "(unnamed)";
            }
            Team owner = info.getOwner();
            ordered.add(new OrderEntry(formatFlagLine(name, owner), 4 + ordered.size()));
        }

        // Assign descending scores so first entry is at top
        int total = ordered.size();
        for (int i = 0; i < total; i++) {
            ordered.get(i).scoreValue = total - i;
        }

        // Push to Scoreboard, removing stale lines
        Set<String> desiredNames = new HashSet<>();
        for (OrderEntry entry : ordered) {
            desiredNames.add(entry.displayText);
            setArtificialScore(scoreboard, entry.displayText, teamScores, entry.scoreValue);
        }

        // Remove any existing scoreboard entries not in desiredNames
        for (var existingScore : scoreboard.listPlayerScores(teamScores)) {
            String existingName = existingScore.owner();
            if (!desiredNames.contains(existingName)) {
                scoreboard.resetSinglePlayerScore(ScoreHolder.forNameOnly(existingName), teamScores);
            }
        }
    }

    // ─── “FLAGS” & “TEAMS” Animation Helpers ─────────────────────────────────────────────────

    private static final int ANIM_WIDTH    = 5;              // chars on each side
    private static final int TOTAL_FRAMES  = ANIM_WIDTH * 3; // full cycle

    private static String getAnimatedHeader(String header, int tickCounter) {
        int cycle = (tickCounter / 5) % TOTAL_FRAMES;
        String left  = wavePattern(cycle, true);
        String right = wavePattern(cycle, false);
        String animated = left + " " + header + " " + right;
        return centerText(animated);
    }

    private static String wavePattern(int cycle, boolean isLeft) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ANIM_WIDTH; i++) {
            int wavePos = isLeft
                    ? cycle - i
                    : cycle - (ANIM_WIDTH - 1 - i);
            char c = ((wavePos / ANIM_WIDTH) % 2 == 0) ? '=' : '-';
            builder.append(c);
        }
        return builder.toString();
    }

    // ─── SCOREBOARD UTILITIES ─────────────────────────────────────────────────────────────

    private static void setArtificialScore(Scoreboard scoreboard, String displayName, Objective obj, int artificialValue) {
        if (obj == null) return;
        ScoreHolder holder = ScoreHolder.forNameOnly(displayName);
        scoreboard.getOrCreatePlayerScore(holder, obj).set(artificialValue);
    }

    private static String formatTeamLine(Team team, int score) {
        String baseRaw;
        ChatFormatting color;
        if (team == Team.RED) {
            baseRaw = "RED";
            color   = ChatFormatting.RED;
        } else if (team == Team.BLUE) {
            baseRaw = "BLUE";
            color   = ChatFormatting.BLUE;
        } else {
            baseRaw = "NONE";
            color   = ChatFormatting.GRAY;
        }

        String scoreStr = String.valueOf(score);
        int leftLen  = baseRaw.length();
        int rightLen = scoreStr.length();
        int spaces   = MAX_WIDTH - leftLen - rightLen;
        if (spaces < 0) spaces = 0;

        return color
                + baseRaw
                + ChatFormatting.RESET
                + " ".repeat(spaces)
                + scoreStr;
    }

    private static int getCharWidth(char c) {
        return switch (c) {
            case '!', ',', '.', ':', ';', 'i', '|', '¡' -> 2;
            case '\'', 'l', 'ì', 'í'                    -> 3;
            case ' ', 'I', '[', ']', 't', 'ï', '×'       -> 4;
            case '\"', '(', ')', '*', '<', '>', 'f', 'k', '{', '}' -> 5;
            case '@', '~', '®'                          -> 7;
            default                                      -> 6;
        };
    }

    private static int getStringWidth(String text) {
        int total = 0;
        for (char c : text.toCharArray()) {
            total += getCharWidth(c);
        }
        return total;
    }

    private static String formatFlagLine(String flagName, Team owner) {
        String ownerRaw;
        ChatFormatting ownerColor;
        if (owner == Team.RED) {
            ownerRaw = "RED";
            ownerColor = ChatFormatting.RED;
        } else if (owner == Team.BLUE) {
            ownerRaw = "BLUE";
            ownerColor = ChatFormatting.BLUE;
        } else {
            ownerRaw = "NONE";
            ownerColor = ChatFormatting.GRAY;
        }

        String leftRaw = FLAG_UNICODE + flagName;
        int leftWidthPx  = getStringWidth(leftRaw);
        int rightWidthPx = getStringWidth(ownerRaw);
        int availablePx  = 100 - rightWidthPx; // MAX_WIDTH_PX inline

        if (leftWidthPx > availablePx) {
            int reservePx = availablePx - getCharWidth(FLAG_UNICODE.charAt(0));
            StringBuilder truncated = new StringBuilder();
            int accu = 0;
            for (char c : flagName.toCharArray()) {
                int w = getCharWidth(c);
                if (accu + w > reservePx) break;
                truncated.append(c);
                accu += w;
            }
            flagName = truncated.toString();
            leftRaw = FLAG_UNICODE + flagName;
            leftWidthPx = getStringWidth(leftRaw);
            availablePx = 100 - rightWidthPx;
        }

        int paddingPx  = Math.max(0, availablePx - leftWidthPx);
        int numSpaces  = paddingPx / getCharWidth(' ');
        String spaces = " ".repeat(numSpaces);

        return ChatFormatting.GREEN
                + leftRaw
                + ChatFormatting.RESET
                + spaces
                + ownerColor
                + ownerRaw
                + ChatFormatting.RESET;
    }

    private static String centerText(String s) {
        int len = s.length();
        if (len >= MAX_WIDTH) {
            return s.substring(0, MAX_WIDTH);
        }
        int padding = (MAX_WIDTH - len) / 2;
        return " ".repeat(padding) + s;
    }

    private static class OrderEntry {
        String displayText;
        int scoreValue;
        OrderEntry(String displayText, int idx) {
            this.displayText = displayText;
            this.scoreValue = idx;
        }
    }
}
