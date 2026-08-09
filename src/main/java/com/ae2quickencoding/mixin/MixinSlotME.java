package com.ae2quickencoding.mixin;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.me.SlotME;
import appeng.util.item.AEItemStack;
import com.ae2quickencoding.client.ClientHandler;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SlotME.class)
public abstract class MixinSlotME {
    @Inject(
            method = {"getStack", "func_75211_c"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ae2QuickEncoding$getBlacklistStack(CallbackInfoReturnable<ItemStack> callback) {
        SlotME slot = (SlotME) (Object) this;
        if (ClientHandler.isBlacklistProxySlot(slot)) {
            callback.setReturnValue(ClientHandler.getBlacklistDisplayStack(slot));
        }
    }

    @Inject(
            method = {"getHasStack", "func_75216_d"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ae2QuickEncoding$hasBlacklistStack(CallbackInfoReturnable<Boolean> callback) {
        SlotME slot = (SlotME) (Object) this;
        if (ClientHandler.isBlacklistProxySlot(slot)) {
            callback.setReturnValue(!ClientHandler.getBlacklistDisplayStack(slot).isEmpty());
        }
    }

    @Inject(method = "getAEStack", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2QuickEncoding$getBlacklistAeStack(CallbackInfoReturnable<IAEItemStack> callback) {
        SlotME slot = (SlotME) (Object) this;
        if (!ClientHandler.isBlacklistProxySlot(slot)) {
            return;
        }

        ItemStack stack = ClientHandler.getBlacklistDisplayStack(slot);
        callback.setReturnValue(stack.isEmpty() ? null : AEItemStack.fromItemStack(stack));
    }
}
