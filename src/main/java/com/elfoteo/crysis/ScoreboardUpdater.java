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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.*;

/**
 * Updates a sidebar Objective called "TeamScores" with:
 *   • Team RED / BLUE scores
 *   • A little "FLAGS" subheader
 *   • One line per Flag: [🚩 FlagName          <OWNER>]
 *
 * Relies on CaptureTheFlagData.getFlags() → List<FlagInfo>.
 */
@EventBusSubscriber(modid = CrysisMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ScoreboardUpdater {
    private static int tickCounter = 0;
    private static MinecraftServer server;

    // Unicode symbols
    private static final String TROPHY_UNICODE = "\uD83C\uDFC6 ";
    private static final String FLAG_UNICODE   = "\uD83D\uDEA9 "; // trailing space

    // “Character‐count” width for centering and team‐lines
    private static final int MAX_WIDTH = 20;

    public static void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        Scoreboard scoreboard = server.getScoreboard();

        // Remove existing objective if present
        Objective existing = scoreboard.getObjective("TeamScores");
        if (existing != null) {
            scoreboard.removeObjective(existing);
        }

        // Create a new sidebar objective; title under 20 chars
        Objective teamScores = scoreboard.addObjective(
                "TeamScores",
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
    public static void onServerTick(ServerTickEvent.Post event) {
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
        Objective teamScores = scoreboard.getObjective("TeamScores");
        if (teamScores == null) return;

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
        // (Assumes CaptureTheFlagData now has a public List<FlagInfo> getFlags() method.)
        List<FlagInfo> allFlags = new ArrayList<>(data.getFlags().stream().toList());
        // Sort by name (empty name sorts first)
        allFlags.sort(Comparator.comparing(flag -> {
            String name = flag.getName();
            return (name == null || name.isEmpty()) ? "" : name;
        }));

        // Each flag entry: [🚩 <Name>      <OWNER>]  (OWNER right‐aligned)
        for (FlagInfo info : allFlags) {
            String name  = info.getName();
            if (name == null || name.isEmpty()) {
                // You could choose to skip unnamed flags, or show “(unnamed)”
                name = "(unnamed)";
            }
            Team owner = info.getOwner();
            ordered.add(new OrderEntry(formatFlagLine(name, owner), 4 + ordered.size()));
        }

        // ─── Assign descending scores so first entry is at top ─────────────────────────────
        int total = ordered.size();
        for (int i = 0; i < total; i++) {
            ordered.get(i).scoreValue = total - i;
        }

        // ─── Push to Scoreboard, removing any stale lines ───────────────────────────────────
        Set<String> desiredNames = new HashSet<>();
        for (OrderEntry entry : ordered) {
            desiredNames.add(entry.displayText);
            setArtificialScore(scoreboard, entry.displayText, teamScores, entry.scoreValue);
        }

        // Remove any existing scoreboard entries not in desiredNames
        for (var existing : scoreboard.listPlayerScores(teamScores)) {
            String existingName = existing.owner();
            if (!desiredNames.contains(existingName)) {
                scoreboard.resetSinglePlayerScore(ScoreHolder.forNameOnly(existingName), teamScores);
            }
        }
    }


    // ─── “FLAGS” & “TEAMS” Animation Helpers ─────────────────────────────────────────────────

    private static final int ANIM_WIDTH    = 5;              // chars on each side
    private static final int TOTAL_FRAMES  = ANIM_WIDTH * 3; // full cycle

    /**
     * Builds an animated header like “===== HEADER =====”,
     * then centers it in MAX_WIDTH.
     */
    private static String getAnimatedHeader(String header, int tickCounter) {
        int cycle = (tickCounter / 5) % TOTAL_FRAMES;

        String left  = wavePattern(cycle, true);
        String right = wavePattern(cycle, false);

        String animated = left + " " + header + " " + right;
        return centerText(animated);
    }

    // Builds the left or right wave (alternating '=' and '-') of length ANIM_WIDTH
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

    /**
     * Sets an “artificial” score for displayName under the given Objective.
     */
    private static void setArtificialScore(Scoreboard scoreboard, String displayName, Objective obj, int artificialValue) {
        if (obj == null) return;
        ScoreHolder holder = ScoreHolder.forNameOnly(displayName);
        scoreboard.getOrCreatePlayerScore(holder, obj).set(artificialValue);
    }

    /**
     * Formats a “RED <spaces> 123” or “BLUE <spaces> 45” line,
     * using plain .length() to count characters.
     */
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

    // In the same class (com.elfoteo.crysis.nanosuit.NanosuitUpgrades),
// remove any "import net.minecraft.client.Minecraft" or "Font" imports.
// Then paste in this code:

    private static final int MAX_WIDTH_PX = 100; // keep your original pixel?budget

    /**
     * Exact pixel widths (in px) for each ASCII character (0x20 ? 0x7E),
     * based on Minecraft?s default font metrics. If a character is not in
     * this table (e.g. your FLAG_UNICODE), we return a fallback width of 7px.
     */
    private static final int getCharWidth(char c) {
        return switch (c) {
            // Width = 2
            case '!', ',', '.', ':', ';', 'i', '|', '¡' -> 2;
                // Width = 3
            case '\'', 'l', 'ì', 'í' -> 3;
                // Width = 4
            case ' ', 'I', '[', ']', 't', 'ï', '×' -> 4;
                // Width = 5
            case '\"', '(', ')', '*', '<', '>', 'f', 'k', '{', '}' -> 5;
                // Width = 7
            case '@', '~', '®' -> 7;
                // All other characters default to width = 6
            default -> 6;
        };
    }


    /** Compute full string width (sum of per-char widths). */
    private static int getStringWidth(String text) {
        int total = 0;
        for (char c : text.toCharArray()) {
            total += getCharWidth(c);
        }
        return total;
    }

    /**
     * Replaces your old formatFlagLine(...) with a pure-Java, server-safe version.
     * This uses getStringWidth(...) above instead of Font.width().
     */
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

        // Compose the left part: Unicode flag icon + name
        String leftRaw = FLAG_UNICODE + flagName;

        // Measure pixel widths using our static lookup:
        int leftWidthPx  = getStringWidth(leftRaw);
        int rightWidthPx = getStringWidth(ownerRaw);

        int availablePx = MAX_WIDTH_PX - rightWidthPx;
        if (leftWidthPx > availablePx) {
            // Truncate flagName until it fits in (availablePx)
            // (we always keep at least the FLAG_UNICODE)
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
            availablePx = MAX_WIDTH_PX - rightWidthPx;
        }

        int paddingPx = Math.max(0, availablePx - leftWidthPx);
        // Since one space is 4px, we need paddingPx/4 spaces (rounded down).
        int numSpaces = paddingPx / getCharWidth(' ');
        String spaces = " ".repeat(numSpaces);

        return ChatFormatting.GREEN
                + leftRaw
                + ChatFormatting.RESET
                + spaces
                + ownerColor
                + ownerRaw
                + ChatFormatting.RESET;
    }

    /**
     * Centers a “plain‐text” string in MAX_WIDTH (character count).
     * If s.length() >= MAX_WIDTH, it simply truncates to MAX_WIDTH.
     */
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
