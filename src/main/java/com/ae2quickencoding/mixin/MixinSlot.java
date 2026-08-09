package com.ae2quickencoding.mixin;

import com.ae2quickencoding.client.ClientHandler;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class MixinSlot {
    @Inject(
            method = {"getStack", "func_75211_c"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ae2QuickEncoding$hideSettingsPatternStack(CallbackInfoReturnable<ItemStack> callback) {
        Slot slot = (Slot) (Object) this;
        if (ClientHandler.isSettingsPatternConfigSlot(slot)) {
            callback.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(
            method = {"getHasStack", "func_75216_d"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ae2QuickEncoding$hideSettingsPatternStackState(CallbackInfoReturnable<Boolean> callback) {
        Slot slot = (Slot) (Object) this;
        if (ClientHandler.isSettingsPatternConfigSlot(slot)) {
            callback.setReturnValue(false);
        }
    }
}
