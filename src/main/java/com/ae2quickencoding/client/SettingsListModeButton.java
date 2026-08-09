package com.ae2quickencoding.client;

import com.ae2quickencoding.AE2QuickEncoding;
import appeng.client.gui.widgets.ITooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;

final class SettingsListModeButton extends GuiButton implements ITooltip {
    private static final ResourceLocation AE2_STATES = new ResourceLocation(
            "appliedenergistics2", "textures/guis/states.png");
    private static final ResourceLocation BLACKLIST_TEXTURE = new ResourceLocation(
            AE2QuickEncoding.MOD_ID, "textures/guis/blacklist.png");
    private static final ResourceLocation REPLACEMENT_TEXTURE = new ResourceLocation(
            AE2QuickEncoding.MOD_ID, "textures/guis/change.png");
    private static final int AE2_TEXTURE_SIZE = 256;
    private static final int BUTTON_BACKGROUND_UV = AE2_TEXTURE_SIZE - 16;

    private boolean replacementMode;

    SettingsListModeButton(int x, int y) {
        super(0, x, y, 16, 16, "");
    }

    void setReplacementMode(boolean value) {
        this.replacementMode = value;
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        float brightness = this.enabled ? 1.0F : 0.5F;
        minecraft.getTextureManager().bindTexture(AE2_STATES);
        GlStateManager.color(brightness, brightness, brightness, 1.0F);
        this.drawTexturedModalRect(this.x, this.y, BUTTON_BACKGROUND_UV, BUTTON_BACKGROUND_UV, 16, 16);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        minecraft.getTextureManager().bindTexture(this.replacementMode ? REPLACEMENT_TEXTURE : BLACKLIST_TEXTURE);
        Gui.drawModalRectWithCustomSizedTexture(this.x, this.y, 0, 0, 16, 16, 16, 16);
        GlStateManager.disableBlend();
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public String getMessage() {
        return I18n.translateToLocal("ae2_quick_encoding.tooltip.mode") + "\n"
                + I18n.translateToLocal(this.replacementMode
                ? "ae2_quick_encoding.tooltip.mode.replacement"
                : "ae2_quick_encoding.tooltip.mode.blacklist");
    }

    @Override
    public int xPos() {
        return this.x;
    }

    @Override
    public int yPos() {
        return this.y;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }
}
