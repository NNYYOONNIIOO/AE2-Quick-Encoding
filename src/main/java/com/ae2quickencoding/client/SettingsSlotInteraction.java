package com.ae2quickencoding.client;

import appeng.client.me.SlotME;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.Slot;

public final class SettingsSlotInteraction {
    private SettingsSlotInteraction() {
    }

    public static Slot resolveTargetSlot(GuiScreen gui, Slot suppliedSlot) {
        return ClientHandler.resolveSettingsSlot(gui, suppliedSlot);
    }

    public static boolean isPatternConfigSlot(GuiScreen gui, Slot slot) {
        return ClientHandler.isSettingsPatternConfigSlot(gui, slot);
    }

    public static boolean isBlacklistSlot(GuiScreen gui, Slot slot) {
        return ClientHandler.isSettingsBlacklistSlot(gui, slot);
    }

    public static void handleBlacklistClick(Slot slot, int mouseButton) {
        if (slot instanceof SlotME) {
            ClientHandler.handleBlacklistSlotClick((SlotME) slot, mouseButton);
        }
    }

    public static void addGhostToBlacklist(Object ingredient) {
        ClientHandler.addBlacklistGhostIngredient(ingredient);
    }

    public static boolean blocksDrag(GuiScreen gui, Slot slot) {
        Slot targetSlot = resolveTargetSlot(gui, slot);
        return isPatternConfigSlot(gui, targetSlot) || isBlacklistSlot(gui, targetSlot);
    }
}
