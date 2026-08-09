package com.ae2quickencoding.client;

import com.ae2quickencoding.model.PatternData;
import mezz.jei.autocrafting.RecipeBookmarkItem;
import net.minecraft.item.ItemStack;

public final class RecipeEntry {
    private final String title;
    private final String categoryTitle;
    private final ItemStack output;
    private final PatternData patternData;
    private final String unsupportedReason;
    private final RecipeBookmarkItem<?> bookmark;

    public RecipeEntry(String title, String categoryTitle, ItemStack output, PatternData patternData,
                       String unsupportedReason, RecipeBookmarkItem<?> bookmark) {
        this.title = title;
        this.categoryTitle = categoryTitle;
        this.output = output == null ? ItemStack.EMPTY : output.copy();
        this.patternData = patternData;
        this.unsupportedReason = unsupportedReason;
        this.bookmark = bookmark;
    }

    public String getTitle() {
        return title;
    }

    public String getCategoryTitle() {
        return categoryTitle;
    }

    public ItemStack getOutput() {
        return output.copy();
    }

    public PatternData getPatternData() {
        return patternData;
    }

    public String getUnsupportedReason() {
        return unsupportedReason;
    }

    public boolean isEncodable() {
        return patternData != null;
    }

    public RecipeBookmarkItem<?> getBookmark() {
        return bookmark;
    }
}
