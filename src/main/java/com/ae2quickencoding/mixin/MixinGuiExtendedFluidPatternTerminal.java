package com.ae2quickencoding.mixin;

import com.ae2quickencoding.client.SettingsSlotInteraction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {
        "com.glodblock.github.client.GuiExtendedFluidPatternTerminal",
        "com.glodblock.github.client.client.gui.GuiExtendedFluidPatternTerminal"
})
public abstract class MixinGuiExtendedFluidPatternTerminal {
    @Inject(
            method = {"handleMouseClick", "func_184098_a"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ae2QuickEncoding$blockSettingsSlotClick(
            Slot slot, int slotIndex, int mouseButton, ClickType clickType, CallbackInfo callback) {
        GuiScreen gui = (GuiScreen) (Object) this;
        AccessorAEBaseGui accessor = (AccessorAEBaseGui) this;
        Slot targetSlot = SettingsSlotInteraction.resolveTargetSlot(gui, slot);
        if (SettingsSlotInteraction.isPatternConfigSlot(gui, targetSlot)) {
            if (accessor.ae2QuickEncoding$isJeiGhostItem()) {
                accessor.ae2QuickEncoding$setJeiGhostItem(false);
                accessor.ae2QuickEncoding$setDraggingJeiGhostItem(false);
                Minecraft minecraft = Minecraft.getMinecraft();
                if (minecraft.player != null) {
                    minecraft.player.inventory.setItemStack(ItemStack.EMPTY);
                }
            }
            callback.cancel();
            return;
        }

        if (SettingsSlotInteraction.isBlacklistSlot(gui, targetSlot)) {
            if (accessor.ae2QuickEncoding$isJeiGhostItem()) {
                SettingsSlotInteraction.addGhostToBlacklist(accessor.ae2QuickEncoding$getBookmarkedIngredient());
                accessor.ae2QuickEncoding$setJeiGhostItem(false);
                accessor.ae2QuickEncoding$setDraggingJeiGhostItem(false);
                Minecraft minecraft = Minecraft.getMinecraft();
                if (minecraft.player != null) {
                    minecraft.player.inventory.setItemStack(ItemStack.EMPTY);
                }
            } else {
                SettingsSlotInteraction.handleBlacklistClick(targetSlot, mouseButton);
            }
        } else {
            return;
        }
        callback.cancel();
    }
}
