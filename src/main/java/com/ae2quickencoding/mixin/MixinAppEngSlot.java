package com.ae2quickencoding.mixin;

import appeng.container.slot.AppEngSlot;
import com.ae2quickencoding.client.ClientHandler;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AppEngSlot.class)
public abstract class MixinAppEngSlot {
    @Inject(
            method = {"getStack", "func_75211_c"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ae2QuickEncoding$hideSettingsPatternStack(CallbackInfoReturnable<ItemStack> callback) {
        AppEngSlot slot = (AppEngSlot) (Object) this;
        if (ClientHandler.isSettingsPatternConfigSlot(slot)) {
            callback.setReturnValue(ItemStack.EMPTY);
        }
    }
}
