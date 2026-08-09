package com.ae2quickencoding.client;

import appeng.client.gui.widgets.ITooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;

final class FluidPatternSettingsButton extends GuiButton implements ITooltip {
    private static final ResourceLocation AE2FC_STATES = new ResourceLocation("ae2fc", "textures/gui/states.png");
    private static final int TEXTURE_SIZE = 64;
    private static final int BUTTON_UV = 48;
    private static final int FLUID_CRAFT_U = 32;
    private static final int FLUID_CRAFT_V = 16;

    private boolean fluidPatternEnabled;

    FluidPatternSettingsButton(int x, int y) {
        super(0, x, y, 16, 16, "");
    }

    void setFluidPatternEnabled(boolean value) {
        this.fluidPatternEnabled = value;
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        minecraft.getTextureManager().bindTexture(AE2FC_STATES);
        GlStateManager.color(this.enabled ? 1.0F : 0.5F, this.enabled ? 1.0F : 0.5F,
                this.enabled ? 1.0F : 0.5F, 1.0F);
        Gui.drawModalRectWithCustomSizedTexture(this.x, this.y, BUTTON_UV, BUTTON_UV,
                16, 16, TEXTURE_SIZE, TEXTURE_SIZE);
        Gui.drawModalRectWithCustomSizedTexture(this.x, this.y, FLUID_CRAFT_U, FLUID_CRAFT_V,
                16, 16, TEXTURE_SIZE, TEXTURE_SIZE);
        Gui.drawRect(this.x + 12, this.y + 12, this.x + 15, this.y + 15,
                this.fluidPatternEnabled ? 0xff3ea83e : 0xffa83e3e);
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public String getMessage() {
        return I18n.translateToLocal("ae2_quick_encoding.tooltip.fluid_pattern") + "\n"
                + I18n.translateToLocal(this.fluidPatternEnabled
                ? "ae2_quick_encoding.tooltip.fluid_pattern.enable"
                : "ae2_quick_encoding.tooltip.fluid_pattern.disable");
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
