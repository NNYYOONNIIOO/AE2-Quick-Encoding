package com.ae2quickencoding.mixin;

import appeng.client.gui.AEBaseGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AEBaseGui.class)
public interface AccessorAEBaseGui {
    @Accessor(value = "isJeiGhostItem", remap = false)
    boolean ae2QuickEncoding$isJeiGhostItem();

    @Accessor(value = "isJeiGhostItem", remap = false)
    void ae2QuickEncoding$setJeiGhostItem(boolean value);

    @Accessor(value = "isDraggingJeiGhostItem", remap = false)
    boolean ae2QuickEncoding$isDraggingJeiGhostItem();

    @Accessor(value = "isDraggingJeiGhostItem", remap = false)
    void ae2QuickEncoding$setDraggingJeiGhostItem(boolean value);

    @Accessor(value = "bookmarkedIngredient", remap = false)
    Object ae2QuickEncoding$getBookmarkedIngredient();
}
