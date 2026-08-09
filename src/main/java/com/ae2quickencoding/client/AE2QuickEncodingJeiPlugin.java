package com.ae2quickencoding.client;

import appeng.client.gui.implementations.GuiExpandedProcessingPatternTerm;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.container.interfaces.IJEIGhostIngredients;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.gui.IGhostIngredientHandler;
import mezz.jei.api.gui.IGhostIngredientHandler.Target;
import mezz.jei.bookmarks.BookmarkItem;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

@JEIPlugin
public final class AE2QuickEncodingJeiPlugin implements IModPlugin {
    @Override
    public void register(IModRegistry registry) {
        registry.addGhostIngredientHandler(GuiPatternTerm.class, new PatternTerminalGhostHandler<GuiPatternTerm>());
        registry.addGhostIngredientHandler(
                GuiExpandedProcessingPatternTerm.class,
                new PatternTerminalGhostHandler<GuiExpandedProcessingPatternTerm>()
        );
        registerOptionalPatternTerminalHandler(registry, "com.glodblock.github.client.GuiFluidPatternTerminal");
        registerOptionalPatternTerminalHandler(registry, "com.glodblock.github.client.GuiWirelessFluidPatternTerminal");
        registerOptionalPatternTerminalHandler(registry, "com.glodblock.github.client.GuiExtendedFluidPatternTerminal");
        registerOptionalPatternTerminalHandler(registry, "com.glodblock.github.client.client.gui.GuiFluidPatternTerminal");
        registerOptionalPatternTerminalHandler(registry, "com.glodblock.github.client.client.gui.GuiWirelessFluidPatternTerminal");
        registerOptionalPatternTerminalHandler(registry, "com.glodblock.github.client.client.gui.GuiExtendedFluidPatternTerminal");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerOptionalPatternTerminalHandler(IModRegistry registry, String className) {
        try {
            Class<?> guiClass = Class.forName(className);
            if (GuiScreen.class.isAssignableFrom(guiClass)) {
                registry.addGhostIngredientHandler((Class) guiClass, new PatternTerminalGhostHandler<GuiScreen>());
            }
        } catch (ClassNotFoundException ignored) {
            // AE2FC is optional.
        } catch (LinkageError ignored) {
            // Do not prevent the base AE2 integration when an optional client class is unavailable.
        }
    }

    private static final class PatternTerminalGhostHandler<T extends GuiScreen>
            implements IGhostIngredientHandler<T> {
        @Override
        @SuppressWarnings("unchecked")
        public <I> List<Target<I>> getTargets(T gui, I ingredient, boolean doStart) {
            List<Target<I>> targets = new ArrayList<>();
            Object actualIngredient = unwrapBookmarkIngredient(ingredient);
            if (ClientHandler.isSettingsMode(gui)) {
                targets.addAll((List<Target<I>>) (List<?>)
                        ClientHandler.getBlacklistGhostTargets(gui, actualIngredient));
                return targets;
            }
            if (gui instanceof IJEIGhostIngredients) {
                Object importedIngredient = actualIngredient instanceof ItemStack
                        ? EncodingSettings.replaceInput((ItemStack) actualIngredient)
                        : actualIngredient;
                List<Target<?>> ae2Targets = ((IJEIGhostIngredients) gui).getPhantomTargets(importedIngredient);
                targets.addAll((List<Target<I>>) (List<?>) ae2Targets);
            }
            return targets;
        }

        @Override
        public void onComplete() {
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

    }
}
