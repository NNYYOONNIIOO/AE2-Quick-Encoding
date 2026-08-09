package com.ae2quickencoding.client;

import appeng.api.config.ActionItems;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.Settings;
import appeng.container.slot.AppEngSlot;
import appeng.client.gui.implementations.GuiExpandedProcessingPatternTerm;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.me.SlotME;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
import mezz.jei.api.gui.IGhostIngredientHandler;
import mezz.jei.bookmarks.BookmarkItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.client.event.GuiContainerEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@SideOnly(Side.CLIENT)
public final class ClientHandler {
    private static final Map<GuiScreen, Boolean> SETTINGS_MODE = new WeakHashMap<>();
    private static final Map<GuiScreen, Integer> BLACKLIST_SCROLL = new WeakHashMap<>();
    private static final Map<GuiScreen, String> ORIGINAL_CUSTOM_NAMES = new WeakHashMap<>();
    private static final Map<GuiScreen, GuiButton> FLUID_CRAFTING_BUTTONS = new WeakHashMap<>();
    private static final Map<GuiScreen, FluidPatternSettingsButton> FLUID_SETTINGS_BUTTONS = new WeakHashMap<>();
    private static final Map<GuiScreen, SettingsListModeButton> SETTINGS_LIST_MODE_BUTTONS = new WeakHashMap<>();
    private static GuiScreen activeSettingsGui;

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        GuiScreen gui = event.getGui();
        if (activeSettingsGui != null && activeSettingsGui != gui) {
            setSettingsMode(activeSettingsGui, false);
        }
        if (gui != null && !isSettingsMode(gui)) {
            BLACKLIST_SCROLL.remove(gui);
            ORIGINAL_CUSTOM_NAMES.remove(gui);
            FLUID_CRAFTING_BUTTONS.remove(gui);
            FLUID_SETTINGS_BUTTONS.remove(gui);
            SETTINGS_LIST_MODE_BUTTONS.remove(gui);
        }
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (!isPatternGui(gui)) {
            return;
        }
        ensureQuickEncodingControls(gui, event.getButtonList());
        if (isSettingsMode(gui)) {
            syncSettingsTitle(gui);
            syncSettingsButtonVisibility(gui);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onGuiAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        GuiScreen gui = event.getGui();
        GuiButton button = event.getButton();
        if (!isPatternGui(gui)) {
            return;
        }

        if (button instanceof SettingsButton) {
            setSettingsMode(gui, !isSettingsMode(gui));
            event.setCanceled(true);
            return;
        }

        if (button instanceof SettingsListModeButton) {
            if (isSettingsMode(gui)) {
                EncodingSettings.toggleReplacementMode();
                BLACKLIST_SCROLL.put(gui, 0);
                syncSettingsTitle(gui);
                syncSettingsListModeButton(gui, getButtonList(gui));
            }
            event.setCanceled(true);
            return;
        }

        if (isSettingsMode(gui)) {
            if (isPatternModeTab(gui, button)) {
                return;
            }
            if (isAllowedSettingsButton(gui, button)) {
                handleSettingsButton(gui, button);
            }
            event.setCanceled(true);
            return;
        }

        if (isPatternEncodeButton(gui, button) && GuiScreen.isCtrlKeyDown() && BatchEncodingController.start()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDrawForeground(GuiContainerEvent.DrawForeground event) {
        GuiContainer gui = event.getGuiContainer();
        if (!isSettingsMode(gui)) {
            return;
        }
        ensureQuickEncodingControls(gui, getButtonList(gui));
        syncSettingsButtonVisibility(gui);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onDrawScreenPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        GuiScreen gui = event.getGui();
        if (isPatternGui(gui)) {
            // AE2 reinitalizes this GUI in-place for terminal-style and search-mode changes.
            // That path clears buttonList without emitting another Forge init event.
            ensureQuickEncodingControls(gui, getButtonList(gui));
        }
        if (isSettingsMode(gui)) {
            syncSettingsTitle(gui);
            syncSettingsButtonVisibility(gui);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen screen = event.getGui();
        if (!isSettingsMode(screen) || !(screen instanceof GuiContainer)) {
            return;
        }

        GuiContainer gui = (GuiContainer) screen;
        drawProcessingFurnace(gui);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onGuiMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        GuiScreen gui = event.getGui();
        if (!isSettingsMode(gui) || gui.mc == null || !(gui instanceof GuiContainer)) {
            return;
        }

        int mouseX = Mouse.getEventX() * gui.width / gui.mc.displayWidth;
        int mouseY = gui.height - Mouse.getEventY() * gui.height / gui.mc.displayHeight - 1;
        if (!isInsideBlacklistSlot(gui, mouseX, mouseY)) {
            return;
        }

        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            List<SlotME> slots = getMESlots((GuiContainer) gui);
            int columns = getBlacklistColumns(slots);
            int direction = wheel > 0 ? -1 : 1;
            setBlacklistScroll(gui, getBlacklistScroll(gui) + direction * columns);
            event.setCanceled(true);
        }
    }

    public static boolean isPatternGui(GuiScreen gui) {
        return gui instanceof GuiPatternTerm || gui instanceof GuiExpandedProcessingPatternTerm;
    }

    public static boolean isSettingsMode(GuiScreen gui) {
        return gui != null && Boolean.TRUE.equals(SETTINGS_MODE.get(gui));
    }

    public static Slot resolveSettingsSlot(GuiScreen gui, Slot suppliedSlot) {
        if (!isSettingsMode(gui)) {
            return suppliedSlot;
        }
        if (isSlotInGui(gui, suppliedSlot)) {
            return suppliedSlot;
        }
        return getSettingsSlotAtMouseEvent(gui);
    }

    public static boolean isBlacklistProxySlot(SlotME slot) {
        if (slot == null || !(activeSettingsGui instanceof GuiContainer) || !isSettingsMode(activeSettingsGui)) {
            return false;
        }
        Container container = getGuiContainer((GuiContainer) activeSettingsGui);
        return container != null && container.inventorySlots.contains(slot);
    }

    public static ItemStack getBlacklistDisplayStack(SlotME slot) {
        if (!isBlacklistProxySlot(slot)) {
            return ItemStack.EMPTY;
        }
        List<SlotME> slots = getMESlots((GuiContainer) activeSettingsGui);
        int slotIndex = slots.indexOf(slot);
        int blacklistIndex = getBlacklistScroll(activeSettingsGui) + slotIndex;
        List<ItemStack> markedItems = getSettingsMarkedItems();
        if (slotIndex < 0 || blacklistIndex < 0 || blacklistIndex >= markedItems.size()) {
            return ItemStack.EMPTY;
        }
        return markedItems.get(blacklistIndex).copy();
    }

    public static void removeBlacklistSlot(SlotME slot) {
        ItemStack stack = getBlacklistDisplayStack(slot);
        if (!stack.isEmpty()) {
            removeSettingsMarkedItem(stack);
            getBlacklistScroll(activeSettingsGui);
        }
    }

    public static boolean isSettingsBlacklistSlot(GuiScreen gui, Slot slot) {
        return isSettingsMode(gui)
                && slot instanceof SlotME
                && isBlacklistProxySlot((SlotME) slot);
    }

    public static void handleBlacklistSlotClick(SlotME slot, int mouseButton) {
        Minecraft minecraft = Minecraft.getMinecraft();
        ItemStack carried = minecraft.player == null
                ? ItemStack.EMPTY
                : minecraft.player.inventory.getItemStack();
        if (!carried.isEmpty()) {
            addSettingsMarkedItem(carried);
            return;
        }
        if (mouseButton == 0) {
            removeBlacklistSlot(slot);
        }
    }

    public static boolean addBlacklistGhostIngredient(Object ingredient) {
        Object actualIngredient = unwrapBookmarkIngredient(ingredient);
        return actualIngredient instanceof ItemStack
                && addSettingsMarkedItem((ItemStack) actualIngredient);
    }

    public static boolean addSettingsItemFromInventorySlot(GuiScreen gui, Slot slot) {
        if (!isSettingsMode(gui) || slot == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null || !isPlayerInventorySlot(slot, minecraft.player.inventory)) {
            return false;
        }
        ItemStack stack = slot.getStack();
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        addSettingsMarkedItem(stack);
        return true;
    }

    private static boolean isPlayerInventorySlot(Slot slot, InventoryPlayer inventory) {
        if (slot instanceof AppEngSlot) {
            return ((AppEngSlot) slot).isPlayerSide();
        }
        return slot.inventory == inventory;
    }

    public static boolean isSettingsPatternConfigSlot(GuiScreen gui, Slot slot) {
        return isSettingsMode(gui) && slot instanceof SlotFake && isSlotInGui(gui, slot);
    }

    public static boolean isSettingsPatternConfigSlot(Slot slot) {
        return activeSettingsGui != null && isSettingsPatternConfigSlot(activeSettingsGui, slot);
    }

    public static Slot getSettingsSlotAt(GuiScreen gui, int mouseX, int mouseY) {
        if (!(gui instanceof GuiContainer)) {
            return null;
        }
        GuiContainer containerGui = (GuiContainer) gui;
        Container container = getGuiContainer(containerGui);
        if (container == null) {
            return null;
        }
        int left = containerGui.getGuiLeft();
        int top = containerGui.getGuiTop();
        for (Slot slot : container.inventorySlots) {
            int x = left + slot.xPos - 1;
            int y = top + slot.yPos - 1;
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                return slot;
            }
        }
        return null;
    }

    public static Slot getSettingsSlotAtMouseEvent(GuiScreen gui) {
        if (gui == null || gui.mc == null) {
            return null;
        }
        int mouseX = Mouse.getEventX() * gui.width / gui.mc.displayWidth;
        int mouseY = gui.height - Mouse.getEventY() * gui.height / gui.mc.displayHeight - 1;
        return getSettingsSlotAt(gui, mouseX, mouseY);
    }

    private static void setSettingsMode(GuiScreen gui, boolean enabled) {
        if (gui == null) {
            return;
        }
        if (enabled) {
            SETTINGS_MODE.put(gui, true);
            BLACKLIST_SCROLL.put(gui, 0);
            activeSettingsGui = gui;
            ensureQuickEncodingControls(gui, getButtonList(gui));
            syncSettingsTitle(gui);
        } else {
            SETTINGS_MODE.remove(gui);
            BLACKLIST_SCROLL.remove(gui);
            restoreSettingsTitle(gui);
            syncFluidPatternSettingsButton(gui);
            syncSettingsListModeButton(gui, getButtonList(gui));
            if (activeSettingsGui == gui) {
                activeSettingsGui = null;
            }
        }
    }

    private static void syncSettingsTitle(GuiScreen gui) {
        ContainerPatternEncoder container = getPatternContainer(gui);
        if (container == null) {
            return;
        }
        if (!ORIGINAL_CUSTOM_NAMES.containsKey(gui)) {
            ORIGINAL_CUSTOM_NAMES.put(gui, container.getCustomName());
        }
        container.setCustomName(I18n.translateToLocal(EncodingSettings.isReplacementMode()
                ? "ae2_quick_encoding.gui.replacements"
                : "ae2_quick_encoding.gui.blacklist"));
    }

    private static void restoreSettingsTitle(GuiScreen gui) {
        if (!ORIGINAL_CUSTOM_NAMES.containsKey(gui)) {
            return;
        }
        ContainerPatternEncoder container = getPatternContainer(gui);
        if (container != null) {
            container.setCustomName(ORIGINAL_CUSTOM_NAMES.get(gui));
        }
        ORIGINAL_CUSTOM_NAMES.remove(gui);
    }

    private static void ensureQuickEncodingControls(GuiScreen gui, List<GuiButton> buttons) {
        if (buttons == null) {
            return;
        }
        ensureSettingsButton(gui, buttons);
        installFluidPatternSettingsButton(gui, buttons);
        syncSettingsListModeButton(gui, buttons);
    }

    private static void ensureSettingsButton(GuiScreen gui, List<GuiButton> buttons) {
        for (GuiButton button : buttons) {
            if (button instanceof SettingsButton) {
                return;
            }
        }

        GuiButton encodeButton = findStandardEncodeButton(gui, buttons);
        if (encodeButton != null) {
            buttons.add(new SettingsButton(encodeButton.x + encodeButton.width + 1, encodeButton.y));
        }
    }

    private static void syncSettingsListModeButton(GuiScreen gui, List<GuiButton> buttons) {
        if (buttons == null) {
            return;
        }

        GuiButton browseButton = findBrowseButton(gui, buttons);
        SettingsListModeButton modeButton = SETTINGS_LIST_MODE_BUTTONS.get(gui);
        if (browseButton == null) {
            if (modeButton != null) {
                modeButton.visible = false;
                modeButton.enabled = false;
            }
            return;
        }

        if (modeButton == null || !buttons.contains(modeButton)) {
            modeButton = null;
            for (GuiButton button : buttons) {
                if (button instanceof SettingsListModeButton) {
                    modeButton = (SettingsListModeButton) button;
                    break;
                }
            }
            if (modeButton == null) {
                modeButton = new SettingsListModeButton(browseButton.x, browseButton.y);
                buttons.add(modeButton);
            }
            SETTINGS_LIST_MODE_BUTTONS.put(gui, modeButton);
        }

        boolean settingsMode = isSettingsMode(gui);
        browseButton.visible = !settingsMode;
        browseButton.enabled = !settingsMode;
        modeButton.x = browseButton.x;
        modeButton.y = browseButton.y;
        modeButton.setReplacementMode(EncodingSettings.isReplacementMode());
        modeButton.visible = settingsMode;
        modeButton.enabled = settingsMode;
    }

    private static GuiButton findBrowseButton(GuiScreen gui, List<GuiButton> buttons) {
        GuiButton fieldButton = getButtonField(gui, "ViewBox");
        if (isBrowseButton(fieldButton) && buttons.contains(fieldButton)) {
            return fieldButton;
        }
        for (GuiButton button : buttons) {
            if (isBrowseButton(button)) {
                return button;
            }
        }
        return null;
    }

    private static boolean isBrowseButton(GuiButton button) {
        return button instanceof GuiImgButton
                && ((GuiImgButton) button).getSetting() == Settings.VIEW_MODE;
    }

    private static void installFluidPatternSettingsButton(GuiScreen gui, List<GuiButton> buttons) {
        if (buttons == null) {
            return;
        }
        GuiButton original = findNativeFluidCraftingButton(gui, buttons);
        if (original == null) {
            return;
        }

        FLUID_CRAFTING_BUTTONS.put(gui, original);
        FluidPatternSettingsButton existing = FLUID_SETTINGS_BUTTONS.get(gui);
        if (existing != null && buttons.contains(existing)) {
            return;
        }
        for (GuiButton button : buttons) {
            if (button instanceof FluidPatternSettingsButton) {
                FLUID_SETTINGS_BUTTONS.put(gui, (FluidPatternSettingsButton) button);
                return;
            }
        }

        FluidPatternSettingsButton replacement = new FluidPatternSettingsButton(original.x, original.y);
        replacement.visible = false;
        replacement.enabled = false;
        buttons.add(replacement);
        FLUID_SETTINGS_BUTTONS.put(gui, replacement);
    }

    private static void syncFluidPatternSettingsButton(GuiScreen gui) {
        GuiButton original = FLUID_CRAFTING_BUTTONS.get(gui);
        FluidPatternSettingsButton replacement = FLUID_SETTINGS_BUTTONS.get(gui);
        if (original == null || replacement == null) {
            List<GuiButton> buttons = getButtonList(gui);
            installFluidPatternSettingsButton(gui, buttons);
            original = FLUID_CRAFTING_BUTTONS.get(gui);
            replacement = FLUID_SETTINGS_BUTTONS.get(gui);
        }
        if (original == null || replacement == null) {
            return;
        }

        ContainerPatternEncoder container = getPatternContainer(gui);
        boolean crafting = container == null || container.isCraftingMode();
        if (isSettingsMode(gui)) {
            original.visible = false;
            replacement.setFluidPatternEnabled(EncodingSettings.isCraftingFluidPatternEnabled());
            replacement.visible = crafting;
            replacement.enabled = crafting;
        } else {
            original.visible = crafting;
            replacement.visible = false;
            replacement.enabled = false;
        }
    }

    private static ContainerPatternEncoder getPatternContainer(GuiScreen gui) {
        if (!(gui instanceof GuiContainer)) {
            return null;
        }
        Container container = getGuiContainer((GuiContainer) gui);
        if (container instanceof ContainerPatternEncoder) {
            return (ContainerPatternEncoder) container;
        }

        Class<?> current = gui.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || !ContainerPatternEncoder.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(gui);
                    if (value instanceof ContainerPatternEncoder) {
                        return (ContainerPatternEncoder) value;
                    }
                } catch (IllegalAccessException ignored) {
                    // Continue with the next candidate field.
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static GuiButton findStandardEncodeButton(GuiScreen gui, List<GuiButton> buttons) {
        GuiButton optional = null;
        for (GuiButton button : buttons) {
            if (button instanceof GuiImgButton) {
                GuiImgButton imageButton = (GuiImgButton) button;
                if (imageButton.getSetting() == Settings.ACTIONS
                        && imageButton.getCurrentValue() == ActionItems.ENCODE) {
                    return button;
                }
            } else if (isOptionalFluidCraftEncodeButton(button)) {
                optional = button;
            }
        }
        return optional;
    }

    private static boolean isPatternEncodeButton(GuiScreen gui, GuiButton button) {
        if (button == null || !isPatternGui(gui)) {
            return false;
        }
        if (isFluidCraftingButton(gui, button)) {
            return true;
        }
        if (button instanceof GuiImgButton) {
            GuiImgButton imageButton = (GuiImgButton) button;
            return imageButton.getSetting() == Settings.ACTIONS
                    && imageButton.getCurrentValue() == ActionItems.ENCODE;
        }
        return isOptionalFluidCraftEncodeButton(button);
    }

    private static boolean isOptionalFluidCraftEncodeButton(GuiButton button) {
        return "CRAFT_FLUID".equals(getButtonProperty(button, "getSetting"))
                && "ENCODE".equals(getButtonProperty(button, "getCurrentValue"));
    }

    private static boolean isAllowedSettingsButton(GuiScreen gui, GuiButton button) {
        ContainerPatternEncoder container = getPatternContainer(gui);
        if (isFluidCraftingButton(gui, button)) {
            return container == null || container.isCraftingMode();
        }
        if (container == null) {
            return false;
        }
        boolean crafting = container.isCraftingMode();
        if (button instanceof GuiImgButton) {
            GuiImgButton imageButton = (GuiImgButton) button;
            if (imageButton.getSetting() != Settings.ACTIONS) {
                return false;
            }
            Enum value = imageButton.getCurrentValue();
            if (crafting) {
                return value == ItemSubstitution.ENABLED || value == ItemSubstitution.DISABLED;
            }
            return value == ActionItems.CLOSE
                    || value == ActionItems.MULTIPLY_BY_THREE
                    || value == ActionItems.MULTIPLY_BY_TWO
                    || value == ActionItems.DIVIDE_BY_THREE
                    || value == ActionItems.DIVIDE_BY_TWO;
        }

        String setting = getButtonProperty(button, "getSetting");
        if (setting == null) {
            return false;
        }
        if ("CRAFT_FLUID".equals(setting)) {
            return crafting;
        }
        if ("FORCE_COMBINE".equals(setting) || "NOT_COMBINE".equals(setting)) {
            return !crafting;
        }
        return "FLUID_FIRST".equals(setting) || "ORIGIN_ORDER".equals(setting);
    }

    private static boolean isPatternModeTab(GuiScreen gui, GuiButton button) {
        if (!(button instanceof GuiTabButton)) {
            return false;
        }
        return button == getButtonField(gui, "tabCraftButton")
                || button == getButtonField(gui, "tabProcessButton");
    }

    private static GuiButton getButtonField(GuiScreen gui, String name) {
        Field field = findField(gui.getClass(), name);
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(gui);
            return value instanceof GuiButton ? (GuiButton) value : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static void handleSettingsButton(GuiScreen gui, GuiButton button) {
        ContainerPatternEncoder container = getPatternContainer(gui);
        boolean crafting = container != null && container.isCraftingMode();
        if (isFluidCraftingButton(gui, button)) {
            EncodingSettings.setCraftingFluidPatternEnabled(
                    !EncodingSettings.isCraftingFluidPatternEnabled());
            return;
        }
        if (button instanceof GuiImgButton) {
            Enum value = ((GuiImgButton) button).getCurrentValue();
            if (value == ItemSubstitution.ENABLED || value == ItemSubstitution.DISABLED) {
                EncodingSettings.setCraftingSubstitutionEnabled(
                        !EncodingSettings.isCraftingSubstitutionEnabled());
            } else if (value == ActionItems.CLOSE) {
                EncodingSettings.resetProcessingFurnace();
            } else if (value == ActionItems.MULTIPLY_BY_THREE) {
                EncodingSettings.multiplyProcessingFurnace(3);
            } else if (value == ActionItems.MULTIPLY_BY_TWO) {
                EncodingSettings.multiplyProcessingFurnace(2);
            } else if (value == ActionItems.DIVIDE_BY_THREE) {
                EncodingSettings.divideProcessingFurnace(3);
            } else if (value == ActionItems.DIVIDE_BY_TWO) {
                EncodingSettings.divideProcessingFurnace(2);
            }
            return;
        }

        String setting = getButtonProperty(button, "getSetting");
        if ("CRAFT_FLUID".equals(setting) && crafting) {
            EncodingSettings.setCraftingFluidPatternEnabled(!EncodingSettings.isCraftingFluidPatternEnabled());
        } else if ("FORCE_COMBINE".equals(setting)) {
            EncodingSettings.setProcessingCombineEnabled(!EncodingSettings.isProcessingCombineEnabled());
        } else if ("NOT_COMBINE".equals(setting)) {
            EncodingSettings.setProcessingCombineEnabled(!EncodingSettings.isProcessingCombineEnabled());
        } else if ("FLUID_FIRST".equals(setting)) {
            setFluidFirst(crafting, !isFluidFirst(crafting));
        } else if ("ORIGIN_ORDER".equals(setting)) {
            setFluidFirst(crafting, !isFluidFirst(crafting));
        }
    }

    private static boolean isFluidFirst(boolean crafting) {
        return crafting
                ? EncodingSettings.isCraftingFluidFirst()
                : EncodingSettings.isProcessingFluidFirst();
    }

    private static void setFluidFirst(boolean crafting, boolean value) {
        if (crafting) {
            EncodingSettings.setCraftingFluidFirst(value);
        } else {
            EncodingSettings.setProcessingFluidFirst(value);
        }
    }

    private static void syncSettingsButtonVisibility(GuiScreen gui) {
        ContainerPatternEncoder container = getPatternContainer(gui);
        if (container == null) {
            syncFluidPatternSettingsButton(gui);
            return;
        }
        boolean crafting = container.isCraftingMode();
        boolean fluidFirst = isFluidFirst(crafting);
        setButtonVisible(gui, "substitutionsEnabledBtn",
                crafting && EncodingSettings.isCraftingSubstitutionEnabled());
        setButtonVisible(gui, "substitutionsDisabledBtn",
                crafting && !EncodingSettings.isCraftingSubstitutionEnabled());
        setButtonVisible(gui, "combineEnableBtn",
                !crafting && EncodingSettings.isProcessingCombineEnabled());
        setButtonVisible(gui, "combineDisableBtn",
                !crafting && !EncodingSettings.isProcessingCombineEnabled());
        setButtonVisible(gui, "fluidEnableBtn", fluidFirst);
        setButtonVisible(gui, "fluidDisableBtn", !fluidFirst);
        syncFluidPatternSettingsButton(gui);
        setButtonVisible(gui, "plusOneBtn", false);
        setButtonVisible(gui, "minusOneBtn", false);
    }

    private static void drawProcessingFurnace(GuiContainer gui) {
        ContainerPatternEncoder container = getPatternContainer(gui);
        if (container == null || container.isCraftingMode()) {
            return;
        }
        Slot firstInput = null;
        for (Slot slot : container.inventorySlots) {
            if (slot instanceof SlotFakeCraftingMatrix) {
                firstInput = slot;
                break;
            }
        }
        if (firstInput == null) {
            return;
        }

        int x = gui.getGuiLeft() + firstInput.xPos;
        int y = gui.getGuiTop() + firstInput.yPos;
        ItemStack furnace = new ItemStack(Blocks.FURNACE, EncodingSettings.getProcessingFurnaceCount());
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        RenderItem renderItem = gui.mc.getRenderItem();
        renderItem.renderItemAndEffectIntoGUI(furnace, x, y);
        renderItem.renderItemOverlayIntoGUI(gui.mc.fontRenderer, furnace, x, y, null);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static List<SlotME> getMESlots(GuiContainer gui) {
        Container container = getGuiContainer(gui);
        if (container == null) {
            return Collections.emptyList();
        }
        List<SlotME> slots = new ArrayList<>();
        for (Slot slot : container.inventorySlots) {
            if (slot instanceof SlotME) {
                slots.add((SlotME) slot);
            }
        }
        Collections.sort(slots, new Comparator<SlotME>() {
            @Override
            public int compare(SlotME first, SlotME second) {
                int byY = Integer.compare(first.yPos, second.yPos);
                return byY == 0 ? Integer.compare(first.xPos, second.xPos) : byY;
            }
        });
        return slots;
    }

    private static Container getGuiContainer(GuiContainer gui) {
        Field field = findField(gui.getClass(), "inventorySlots", "field_147002_h");
        if (field != null) {
            try {
                Object value = field.get(gui);
                if (value instanceof Container) {
                    return (Container) value;
                }
            } catch (IllegalAccessException ignored) {
                // The Mouse Tweaks accessor below is the fallback for this optional integration.
            }
        }
        try {
            Method method = gui.getClass().getMethod("MT_getContainer");
            Object value = method.invoke(gui);
            if (value instanceof Container) {
                return (Container) value;
            }
        } catch (ReflectiveOperationException ignored) {
            // Mouse Tweaks is optional; fall back to the normal GUI field below.
        }
        return null;
    }

    private static Rectangle getBlacklistArea(GuiScreen gui) {
        int left = gui instanceof GuiContainer ? ((GuiContainer) gui).getGuiLeft() : 0;
        int top = gui instanceof GuiContainer ? ((GuiContainer) gui).getGuiTop() : 0;
        List<SlotME> slots = gui instanceof GuiContainer ? getMESlots((GuiContainer) gui) : Collections.<SlotME>emptyList();
        if (slots.isEmpty()) {
            return new Rectangle(left, top + 18, 197, 54);
        }
        int minY = slots.get(0).yPos;
        int maxY = minY;
        for (SlotME slot : slots) {
            maxY = Math.max(maxY, slot.yPos);
        }
        return new Rectangle(left, top + minY, 197, maxY - minY + 18);
    }

    public static Rectangle getBlacklistAreaForGhost(GuiScreen gui) {
        return gui == null ? new Rectangle() : getBlacklistArea(gui);
    }

    public static List<Rectangle> getBlacklistSlotAreasForGhost(GuiScreen gui) {
        if (!(gui instanceof GuiContainer)) {
            return Collections.emptyList();
        }
        GuiContainer containerGui = (GuiContainer) gui;
        int left = containerGui.getGuiLeft();
        int top = containerGui.getGuiTop();
        List<Rectangle> result = new ArrayList<>();
        for (SlotME slot : getMESlots(containerGui)) {
            result.add(new Rectangle(left + slot.xPos - 1, top + slot.yPos - 1, 18, 18));
        }
        return result;
    }

    public static List<IGhostIngredientHandler.Target<?>> getBlacklistGhostTargets(GuiScreen gui, Object ingredient) {
        Object actualIngredient = unwrapBookmarkIngredient(ingredient);
        if (!isSettingsMode(gui) || !(actualIngredient instanceof ItemStack)) {
            return Collections.emptyList();
        }
        List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();
        for (Rectangle area : getBlacklistSlotAreasForGhost(gui)) {
            targets.add(new BlacklistGhostTarget(area));
        }
        return targets;
    }

    private static final class BlacklistGhostTarget implements IGhostIngredientHandler.Target<Object> {
        private final Rectangle area;

        private BlacklistGhostTarget(Rectangle area) {
            this.area = area;
        }

        @Override
        public Rectangle getArea() {
            return area;
        }

        @Override
        public void accept(Object ingredient) {
            addBlacklistGhostIngredient(ingredient);
        }
    }

    private static List<ItemStack> getSettingsMarkedItems() {
        return EncodingSettings.isReplacementMode()
                ? EncodingSettings.getReplacements()
                : EncodingSettings.getBlacklist();
    }

    private static boolean addSettingsMarkedItem(ItemStack stack) {
        return EncodingSettings.isReplacementMode()
                ? EncodingSettings.addReplacement(stack)
                : EncodingSettings.addBlacklist(stack);
    }

    private static boolean removeSettingsMarkedItem(ItemStack stack) {
        return EncodingSettings.isReplacementMode()
                ? EncodingSettings.removeReplacement(stack)
                : EncodingSettings.removeBlacklist(stack);
    }

    private static int getBlacklistScroll(GuiScreen gui) {
        List<SlotME> slots = gui instanceof GuiContainer ? getMESlots((GuiContainer) gui) : Collections.<SlotME>emptyList();
        int max = Math.max(0, getSettingsMarkedItems().size() - slots.size());
        int value = BLACKLIST_SCROLL.containsKey(gui) ? BLACKLIST_SCROLL.get(gui) : 0;
        value = Math.max(0, Math.min(max, value));
        BLACKLIST_SCROLL.put(gui, value);
        return value;
    }

    private static void setBlacklistScroll(GuiScreen gui, int value) {
        List<SlotME> slots = gui instanceof GuiContainer ? getMESlots((GuiContainer) gui) : Collections.<SlotME>emptyList();
        int max = Math.max(0, getSettingsMarkedItems().size() - slots.size());
        BLACKLIST_SCROLL.put(gui, Math.max(0, Math.min(max, value)));
    }

    private static int getBlacklistColumns(List<SlotME> slots) {
        if (slots.isEmpty()) {
            return 1;
        }
        int firstRowY = slots.get(0).yPos;
        int columns = 0;
        for (SlotME slot : slots) {
            if (slot.yPos != firstRowY) {
                break;
            }
            columns++;
        }
        return Math.max(1, columns);
    }

    private static boolean isInsideBlacklistSlot(GuiScreen gui, int mouseX, int mouseY) {
        for (Rectangle area : getBlacklistSlotAreasForGhost(gui)) {
            if (area.contains(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSlotInGui(GuiScreen gui, Slot slot) {
        if (slot == null || !(gui instanceof GuiContainer)) {
            return false;
        }
        Container container = getGuiContainer((GuiContainer) gui);
        return container != null && container.inventorySlots.contains(slot);
    }

    private static Object unwrapBookmarkIngredient(Object ingredient) {
        Object result = ingredient;
        while (result instanceof BookmarkItem) {
            Object next = ((BookmarkItem<?>) result).getIngredient();
            if (next == result) {
                break;
            }
            result = next;
        }
        return result;
    }

    private static boolean isFluidCraftingButton(GuiScreen gui, GuiButton button) {
        if (button == null || !isPatternGui(gui)) {
            return false;
        }
        if (button == FLUID_SETTINGS_BUTTONS.get(gui) || button == FLUID_CRAFTING_BUTTONS.get(gui)) {
            return true;
        }
        return button == getButtonField(gui, "craftingFluidBtn") || isNativeFluidCraftingButton(button);
    }

    private static GuiButton findNativeFluidCraftingButton(GuiScreen gui, List<GuiButton> buttons) {
        GuiButton fieldButton = getButtonField(gui, "craftingFluidBtn");
        if (isNativeFluidCraftingButton(fieldButton)) {
            return fieldButton;
        }
        if (buttons != null) {
            for (GuiButton button : buttons) {
                if (isNativeFluidCraftingButton(button)) {
                    return button;
                }
            }
        }
        return null;
    }

    private static boolean isNativeFluidCraftingButton(GuiButton button) {
        return button != null
                && "CRAFT_FLUID".equals(getButtonProperty(button, "getSetting"))
                && "ENCODE".equals(getButtonProperty(button, "getCurrentValue"));
    }

    private static String getButtonProperty(GuiButton button, String methodName) {
        if (button == null) {
            return null;
        }
        Class<?> current = button.getClass();
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                Object value = method.invoke(button);
                return value == null ? null : value.toString();
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private static void setButtonVisible(GuiScreen gui, String name, boolean visible) {
        GuiButton button = getButtonField(gui, name);
        if (button == null) {
            return;
        }
        button.visible = visible;
    }

    @SuppressWarnings("unchecked")
    private static List<GuiButton> getButtonList(GuiScreen gui) {
        if (gui == null) {
            return null;
        }
        Field field = findField(gui.getClass(), "buttonList", "field_146292_n");
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(gui);
            return value instanceof List ? (List<GuiButton>) value : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String... names) {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    // Try the next mapped name before walking up the hierarchy.
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

}
