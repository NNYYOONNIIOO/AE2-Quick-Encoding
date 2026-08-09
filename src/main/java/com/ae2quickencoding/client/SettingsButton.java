package com.ae2quickencoding.client;

import com.ae2quickencoding.AE2QuickEncoding;
import appeng.client.gui.widgets.ITooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;

public final class SettingsButton extends GuiButton implements ITooltip {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            AE2QuickEncoding.MOD_ID, "textures/guis/set.png");

    public SettingsButton(int x, int y) {
        super(0, x, y, 16, 16, "");
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }
        minecraft.getTextureManager().bindTexture(TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.drawModalRectWithCustomSizedTexture(this.x, this.y, 0, 0, 16, 16, 16, 16);
    }

    @Override
    public String getMessage() {
        return I18n.translateToLocal("ae2_quick_encoding.gui.settings");
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
        return 16;
    }

    @Override
    public int getHeight() {
        return 16;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }
}
