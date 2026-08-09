package com.ae2quickencoding.mixin;

import appeng.client.gui.AEBaseGui;
import appeng.client.me.SlotME;
import com.ae2quickencoding.client.ClientHandler;
import com.ae2quickencoding.client.SettingsSlotInteraction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AEBaseGui.class)
public abstract class MixinAEBaseGui {
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

        if (ClientHandler.isSettingsMode(gui)
                && (clickType == ClickType.QUICK_MOVE || GuiScreen.isShiftKeyDown())
                && ClientHandler.addSettingsItemFromInventorySlot(gui, slot)) {
            callback.cancel();
            return;
        }

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

        if (!SettingsSlotInteraction.isBlacklistSlot(gui, targetSlot)) {
            return;
        }

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
        callback.cancel();
    }

    @Inject(
            method = {"mouseClickMove", "func_146273_a"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ae2QuickEncoding$blockSettingsSlotDrag(
            int mouseX, int mouseY, int mouseButton, long dragTime, CallbackInfo callback) {
        GuiScreen gui = (GuiScreen) (Object) this;
        if (SettingsSlotInteraction.blocksDrag(gui, ClientHandler.getSettingsSlotAt(gui, mouseX, mouseY))) {
            callback.cancel();
        }
    }

    @Inject(
            method = {"handleMouseClick", "func_184098_a"},
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/api/gui/IGhostIngredientHandler$Target;accept(Ljava/lang/Object;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ae2QuickEncoding$stopAfterBlacklistGhostAccepted(
            Slot slot, int slotIndex, int mouseButton, ClickType clickType, CallbackInfo callback) {
        GuiScreen gui = (GuiScreen) (Object) this;
        if (!ClientHandler.isSettingsMode(gui)
                || !(slot instanceof SlotME)
                || !ClientHandler.isBlacklistProxySlot((SlotME) slot)) {
            return;
        }

        AccessorAEBaseGui accessor = (AccessorAEBaseGui) this;
        accessor.ae2QuickEncoding$setJeiGhostItem(false);
        accessor.ae2QuickEncoding$setDraggingJeiGhostItem(false);

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player != null) {
            minecraft.player.inventory.setItemStack(ItemStack.EMPTY);
        }
        callback.cancel();
    }
}
