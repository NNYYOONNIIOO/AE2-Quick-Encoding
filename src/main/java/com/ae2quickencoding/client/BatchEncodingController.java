package com.ae2quickencoding.client;

import com.ae2quickencoding.network.EncodeRecipeMessage;
import com.ae2quickencoding.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SideOnly(Side.CLIENT)
public final class BatchEncodingController {
    private static RecipeEntry currentEntry;
    private static int currentRequestId;
    private static int nextRequestId = 1;
    private static List<RecipeEntry> queuedEntries = Collections.emptyList();
    private static int nextEntryIndex;
    private static boolean running;

    private BatchEncodingController() {
    }

    public static boolean start() {
        if (running) {
            return true;
        }

        List<RecipeEntry> entries = new ArrayList<>();
        RecipeEntry firstUnsupportedEntry = null;
        for (RecipeEntry entry : RecipeBookmarkCatalog.collect()) {
            if (entry != null && entry.isEncodable()) {
                entries.add(entry);
            } else if (firstUnsupportedEntry == null && entry != null
                    && entry.getUnsupportedReason() != null
                    && !entry.getUnsupportedReason().isEmpty()) {
                firstUnsupportedEntry = entry;
            }
        }
        if (firstUnsupportedEntry != null) {
            showUnsupportedReason(firstUnsupportedEntry);
        }
        if (entries.isEmpty()) {
            return firstUnsupportedEntry != null;
        }

        queuedEntries = entries;
        nextEntryIndex = 0;
        running = true;
        if (!sendNext()) {
            stop();
            return false;
        }
        return true;
    }

    private static void showUnsupportedReason(RecipeEntry entry) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.ingameGUI != null) {
            minecraft.ingameGUI.getChatGUI().printChatMessage(
                    new TextComponentTranslation(entry.getUnsupportedReason()));
        }
    }

    public static void handleResult(int requestId, boolean completed) {
        if (!running || currentEntry == null || requestId != currentRequestId) {
            return;
        }

        RecipeEntry finishedEntry = currentEntry;
        currentEntry = null;
        currentRequestId = 0;
        if (!completed) {
            stop();
            return;
        }
        if (!RecipeBookmarkCatalog.removeBookmark(finishedEntry)) {
            stop();
            return;
        }
        if (!sendNext()) {
            stop();
        }
    }

    private static boolean sendNext() {
        while (nextEntryIndex < queuedEntries.size()) {
            RecipeEntry entry = queuedEntries.get(nextEntryIndex++);
            if (entry == null || !entry.isEncodable()) {
                continue;
            }

            currentEntry = entry;
            currentRequestId = nextRequestId();
            NetworkHandler.CHANNEL.sendToServer(new EncodeRecipeMessage(
                    currentRequestId,
                    currentEntry.getPatternData(),
                    true
            ));
            return true;
        }

        currentEntry = null;
        currentRequestId = 0;
        return false;
    }

    private static void stop() {
        currentEntry = null;
        currentRequestId = 0;
        queuedEntries = Collections.emptyList();
        nextEntryIndex = 0;
        running = false;
    }

    private static int nextRequestId() {
        int result = nextRequestId++;
        if (nextRequestId <= 0) {
            nextRequestId = 1;
        }
        return result;
    }
}
