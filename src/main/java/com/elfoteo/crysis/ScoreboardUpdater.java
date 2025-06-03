package com.elfoteo.crysis;

import com.elfoteo.crysis.flag.CaptureTheFlagData;
import com.elfoteo.crysis.flag.Team;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.*;

/**
 * ScoreboardUpdater builds a sidebar that looks like this:
 *
 *   [Playing Capture The Flag]
 *    Teams:
 *      Red    5
 *      Blue   12
 *    --- Flags ---
 *      12, 51, 21   BLUE
 *      12, 34, 56   RED
 *      65, 43, 21   NONE
 *
 * To force Minecraft to display in exactly that order, we assign each line a unique "artificial"
 * integer (so higher artificial ? higher on the list). We never call Integer.parseInt(...) on
 * something like "12, 51, 21". Instead, we treat it as plain text.
 */
@EventBusSubscriber(modid = CrysisMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ScoreboardUpdater {
    private static int tickCounter = 0;
    private static MinecraftServer server;

    // Called by CrysisMod during server startup
    public static void onServerStarting(ServerStartingEvent event) {
        server = event.getServer();
        Scoreboard scoreboard = server.getScoreboard();

        // Remove any old "GameSidebar" objective
        if (scoreboard.getObjective("GameSidebar") != null) {
            scoreboard.removeObjective(scoreboard.getObjective("GameSidebar"));
        }

        // Create a new objective named "GameSidebar" with display name "Playing Capture The Flag"
        Objective sidebar = scoreboard.addObjective(
                "GameSidebar",
                ObjectiveCriteria.DUMMY,
                // Title on top of the sidebar:
                Component.literal("Playing Capture The Flag").withStyle(ChatFormatting.GOLD),
                RenderType.INTEGER,
                true,
                null
        );

        // Add at least one placeholder so the objective isn't empty initially:
        // (We can add a dummy "Loading..." or just ensure teams exist.)
        addPlaceholder(scoreboard, "Teams:", sidebar);
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, sidebar);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % 100 == 0) { // every 5 seconds at 20 TPS
            updateScoreboard(server.getScoreboard());
        }
    }

    /**
     * Rebuilds the entire sidebar. We build a List<OrderEntry> in the exact visual order
     * (teams, then separator, then flags), assign each a descending artificial integer,
     * and write them into the scoreboard. Any old lines not in this set are removed.
     */
    private static void updateScoreboard(Scoreboard scoreboard) {
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        CaptureTheFlagData data = CaptureTheFlagData.getOrCreate(level);

        Objective sidebar = scoreboard.getObjective("GameSidebar");
        if (sidebar == null) return;

        // 1) Build a single list in the exact order we want:
        List<OrderEntry> ordered = new ArrayList<>();

        // 1.a) "Teams:" heading (no numeric data here)
        ordered.add(new OrderEntry("Teams:", 0));

        // 1.b) Red team line: "Red    <score>" in red text
        String redLine = ChatFormatting.RED + "Red" + ChatFormatting.RESET + "    " + data.getRedScore();
        ordered.add(new OrderEntry(redLine, 1));

        // 1.c) Blue team line: "Blue   <score>" in blue text
        String blueLine = ChatFormatting.BLUE + "Blue" + ChatFormatting.RESET + "   " + data.getBlueScore();
        ordered.add(new OrderEntry(blueLine, 2));

        // 1.d) Separator: "--- Flags ---"
        ordered.add(new OrderEntry(ChatFormatting.WHITE + "--- Flags ---" + ChatFormatting.RESET, 3));

        // 1.e) Flag lines: each key is something like "12, 51, 21" ? "<coords>    <OWNER>"
        //     OWNER is in red/blue/gray text. We use insertion?order from data.flagOwners.
        for (Map.Entry<String, Team> e : data.flagOwners.entrySet()) {
            String coords = e.getKey();        // e.g. "12, 51, 21"
            Team owner = e.getValue();
            String ownerText;
            if (owner == Team.RED) {
                ownerText = ChatFormatting.RED + "RED" + ChatFormatting.RESET;
            } else if (owner == Team.BLUE) {
                ownerText = ChatFormatting.BLUE + "BLUE" + ChatFormatting.RESET;
            } else {
                ownerText = ChatFormatting.GRAY + "NONE" + ChatFormatting.RESET;
            }
            // Two spaces between coords and owner
            String flagLine = coords + "    " + ownerText;
            ordered.add(new OrderEntry(flagLine, 0)); // second parameter is dummy; overwritten below
        }

        // 2) Assign each entry a unique artificial score so that the first in `ordered` gets the highest
        //    value, the next gets one less, etc. Minecraft will then render them in descending order.
        int total = ordered.size();
        for (int i = 0; i < total; i++) {
            // highest artificial = total, next = total-1, ..., last = 1
            ordered.get(i).scoreValue = (total - i);
        }

        // 3) Write them into the scoreboard, and collect "desiredNames" to remove stale entries later
        Set<String> desiredNames = new HashSet<>();
        for (OrderEntry entry : ordered) {
            desiredNames.add(entry.displayText);
            setArtificialScore(scoreboard, entry.displayText, sidebar, entry.scoreValue);
        }

        // 4) Remove any old lines that we no longer want
        for (PlayerScoreEntry existing : scoreboard.listPlayerScores(sidebar)) {
            String name = existing.owner();
            if (!desiredNames.contains(name)) {
                scoreboard.resetSinglePlayerScore(ScoreHolder.forNameOnly(name), sidebar);
            }
        }
    }

    /**
     * Ensures the objective isn't empty at startup. We add one placeholder line
     * (like "Teams:") so that Minecraft doesn?t hide the sidebar entirely.
     */
    private static void addPlaceholder(Scoreboard scoreboard, String text, Objective obj) {
        if (obj == null) return;
        ScoreHolder holder = ScoreHolder.forNameOnly(text);
        if (scoreboard.getPlayerScoreInfo(holder, obj) == null) {
            scoreboard.getOrCreatePlayerScore(holder, obj).set(0);
        }
    }

    /**
     * Sets the "artificial" integer value for a given line so Minecraft sorts it.
     */
    private static void setArtificialScore(Scoreboard scoreboard, String lineText, Objective obj, int artificialValue) {
        if (obj == null) return;
        ScoreHolder holder = ScoreHolder.forNameOnly(lineText);
        scoreboard.getOrCreatePlayerScore(holder, obj).set(artificialValue);
    }

    /**
     * A tiny helper to hold each line of text plus the "artificial" value we'll assign.
     */
    private static class OrderEntry {
        String displayText; // exactly what appears on-screen
        int    scoreValue;  // artificial value for sorting

        OrderEntry(String displayText, int dummy) {
            this.displayText = displayText;
            this.scoreValue = dummy; // overwritten once we know the total count
        }
    }
}
