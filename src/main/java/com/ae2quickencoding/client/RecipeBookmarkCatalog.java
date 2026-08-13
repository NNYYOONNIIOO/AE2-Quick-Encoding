package com.ae2quickencoding.client;

import com.ae2quickencoding.model.PatternData;
import com.ae2quickencoding.model.PatternLimits;
import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.wrapper.IShapedCraftingRecipeWrapper;
import mezz.jei.autocrafting.IngredientUtil;
import mezz.jei.autocrafting.RecipeBookmarkItem;
import mezz.jei.bookmarks.BookmarkGroup;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.bookmarks.BookmarkList;
import mezz.jei.ingredients.Ingredients;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class RecipeBookmarkCatalog {
    private static final Field RECIPE_FIELD = getField("recipe");
    private static final Field CATEGORY_FIELD = getField("category");

    private RecipeBookmarkCatalog() {
    }

    public static List<RecipeEntry> collect() {
        try {
            BookmarkList bookmarkList = Internal.getBookmarkList();
            List<RecipeEntry> entries = new ArrayList<>();
            Set<IRecipeWrapper> seen = Collections.newSetFromMap(new IdentityHashMap<IRecipeWrapper, Boolean>());

            for (BookmarkGroup group : bookmarkList.getBookmarkGroupsInternal()) {
                for (BookmarkItem<?> item : group.getItemsInternal()) {
                    if (!(item instanceof RecipeBookmarkItem)) {
                        continue;
                    }

                    RecipeBookmarkItem<?> recipeBookmark = (RecipeBookmarkItem<?>) item;
                    try {
                        recipeBookmark.populateWithFavorite();
                        IRecipeWrapper recipe = getRecipe(recipeBookmark);
                        IRecipeCategory<?> category = getCategory(recipeBookmark);
                        if (recipe == null || category == null || !seen.add(recipe)) {
                            continue;
                        }
                        entries.add(createEntry(recipeBookmark, recipe, category));
                    } catch (Throwable ignored) {
                        // A broken third-party recipe must not prevent other bookmarks from being usable.
                    }
                }
            }
            return entries;
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    public static boolean hasRecipeBookmarks() {
        for (RecipeEntry entry : collect()) {
            if (entry != null) {
                return true;
            }
        }
        return false;
    }

    public static boolean removeBookmark(RecipeEntry entry) {
        if (entry == null || entry.getBookmark() == null) {
            return false;
        }
        try {
            BookmarkList bookmarkList = Internal.getBookmarkList();
            RecipeBookmarkItem<?> bookmark = findCurrentBookmark(bookmarkList, entry.getBookmark());
            if (bookmark == null) {
                return false;
            }
            boolean removed = bookmarkList.remove(bookmark);
            if (removed) {
                return true;
            }
            return !containsBookmark(bookmarkList, bookmark);
        } catch (Throwable ignored) {
            // The caller stops the batch when the bookmark is still present.
            return false;
        }
    }

    /**
     * Removes all completed bookmarks from one HEI snapshot. Resolving and
     * removing them in one pass prevents HEI from rewriting dependent recipe
     * bookmarks between two individual removals.
     */
    public static boolean removeBookmarks(List<RecipeEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return false;
        }

        boolean removed = false;
        try {
            BookmarkList bookmarkList = Internal.getBookmarkList();
            Set<RecipeBookmarkItem<?>> expected =
                    Collections.newSetFromMap(new IdentityHashMap<RecipeBookmarkItem<?>, Boolean>());
            Set<RecipeBookmarkItem<?>> targets =
                    Collections.newSetFromMap(new IdentityHashMap<RecipeBookmarkItem<?>, Boolean>());
            for (RecipeEntry entry : entries) {
                if (entry != null && entry.getBookmark() != null) {
                    expected.add(entry.getBookmark());
                }
            }

            List<BookmarkGroup> groups = new ArrayList<>(bookmarkList.getBookmarkGroupsInternal());
            for (BookmarkGroup group : groups) {
                for (BookmarkItem<?> item : new ArrayList<>(group.getItemsInternal())) {
                    if (item instanceof RecipeBookmarkItem && expected.contains(item)) {
                        targets.add((RecipeBookmarkItem<?>) item);
                    }
                }
            }

            // If HEI rebuilt the list, retain the old matching behavior only
            // for entries that could not be found by identity.
            for (RecipeEntry entry : entries) {
                if (entry == null || entry.getBookmark() == null || expected.contains(entry.getBookmark())
                        && targets.contains(entry.getBookmark())) {
                    continue;
                }
                try {
                    RecipeBookmarkItem<?> current = findCurrentBookmark(bookmarkList, entry.getBookmark());
                    if (current != null) {
                        targets.add(current);
                    }
                } catch (Throwable ignored) {
                    // Continue resolving the other completed bookmarks.
                }
            }

            for (BookmarkGroup group : groups) {
                for (BookmarkItem<?> item : new ArrayList<>(group.getItemsInternal())) {
                    if (targets.contains(item)) {
                        // Use the group's public removal hook so RecipeBookmarkGroup
                        // also updates its dependency chain and derived display rows.
                        group.removeItem(item);
                        removed = true;
                    }
                }
                if (group.getItemsInternal().isEmpty()) {
                    bookmarkList.getBookmarkGroupsInternal().remove(group);
                }
            }

            if (removed) {
                notifyBookmarkListChanged(bookmarkList);
                bookmarkList.saveBookmarks();
            }
        } catch (Throwable ignored) {
            // Keep the encoded patterns even if HEI changes its bookmark API.
        }
        return removed;
    }

    private static void notifyBookmarkListChanged(BookmarkList bookmarkList) {
        try {
            Method method = BookmarkList.class.getDeclaredMethod("notifyListenersOfChange");
            method.setAccessible(true);
            method.invoke(bookmarkList);
        } catch (Throwable ignored) {
            // Saving still keeps the bookmark file consistent.
        }
    }

    private static RecipeBookmarkItem<?> findCurrentBookmark(BookmarkList bookmarkList,
                                                               RecipeBookmarkItem<?> expected)
            throws IllegalAccessException {
        for (BookmarkGroup group : bookmarkList.getBookmarkGroupsInternal()) {
            for (BookmarkItem<?> item : group.getItemsInternal()) {
                if (!(item instanceof RecipeBookmarkItem)) {
                    continue;
                }
                RecipeBookmarkItem<?> candidate = (RecipeBookmarkItem<?>) item;
                if (candidate == expected || matchesBookmark(expected, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean matchesBookmark(RecipeBookmarkItem<?> expected, RecipeBookmarkItem<?> candidate)
            throws IllegalAccessException {
        if (!IngredientUtil.equals(expected.getIngredient(), candidate.getIngredient())) {
            return false;
        }

        expected.populateWithFavorite();
        candidate.populateWithFavorite();
        IRecipeWrapper expectedRecipe = getRecipe(expected);
        IRecipeWrapper candidateRecipe = getRecipe(candidate);
        if (expectedRecipe == null || candidateRecipe == null
                || (expectedRecipe != candidateRecipe && !expectedRecipe.equals(candidateRecipe))) {
            return false;
        }

        IRecipeCategory<?> expectedCategory = getCategory(expected);
        IRecipeCategory<?> candidateCategory = getCategory(candidate);
        return expectedCategory == candidateCategory
                || (expectedCategory != null && candidateCategory != null
                && expectedCategory.getUid().equals(candidateCategory.getUid()));
    }

    private static boolean containsBookmark(BookmarkList bookmarkList, RecipeBookmarkItem<?> target) {
        for (BookmarkGroup group : bookmarkList.getBookmarkGroupsInternal()) {
            for (BookmarkItem<?> item : group.getItemsInternal()) {
                if (item == target) {
                    return true;
                }
            }
        }
        return false;
    }

    private static RecipeEntry createEntry(RecipeBookmarkItem<?> bookmark, IRecipeWrapper recipe, IRecipeCategory<?> category) {
        ItemStack bookmarkOutput = bookmark.getIngredient() instanceof ItemStack
                ? (ItemStack) bookmark.getIngredient()
                : ItemStack.EMPTY;
        String categoryTitle = category.getTitle();
        String title = bookmarkOutput.isEmpty() ? categoryTitle : bookmarkOutput.getDisplayName();

        try {
            PatternBuildResult result = buildPattern(recipe, category);
            ItemStack output = result.output.isEmpty() ? bookmarkOutput : result.output;
            if (output.isEmpty() && bookmarkOutput.isEmpty() && !result.outputName.isEmpty()) {
                title = result.outputName;
            }
            return new RecipeEntry(title, categoryTitle, output, result.data, result.reason, bookmark);
        } catch (Throwable ignored) {
            return new RecipeEntry(title, categoryTitle, bookmarkOutput, null,
                    "ae2_quick_encoding.recipe.unsupported_error", bookmark);
        }
    }

    private static PatternBuildResult buildPattern(IRecipeWrapper recipe, IRecipeCategory<?> category) {
        Ingredients ingredients = new Ingredients();
        recipe.getIngredients(ingredients);

        List<List<ItemStack>> inputSlots = ingredients.getInputs(VanillaTypes.ITEM);
        List<List<ItemStack>> outputSlots = ingredients.getOutputs(VanillaTypes.ITEM);
        List<FluidStack> fluidInputs = firstFluids(ingredients.getInputs(VanillaTypes.FLUID));
        List<FluidStack> fluidOutputs = firstFluids(ingredients.getOutputs(VanillaTypes.FLUID));
        boolean hasFluids = !fluidInputs.isEmpty() || !fluidOutputs.isEmpty();
        if (hasFluids && !Loader.isModLoaded("ae2fc")) {
            return PatternBuildResult.unsupported("ae2_quick_encoding.recipe.ae2fc_required");
        }

        // Replacement-list entries affect imported ingredients only; recipe outputs stay unchanged.
        List<ItemStack> outputs = firstItems(outputSlots);
        boolean crafting = VanillaRecipeCategoryUid.CRAFTING.equals(category.getUid());
        if (crafting) {
            if (outputs.isEmpty()) {
                return PatternBuildResult.unsupported("ae2_quick_encoding.recipe.no_item_output");
            }
            if (outputs.size() != 1) {
                return PatternBuildResult.unsupported("ae2_quick_encoding.recipe.multiple_crafting_outputs");
            }
            if (!fluidOutputs.isEmpty()) {
                return PatternBuildResult.unsupported("ae2_quick_encoding.recipe.fluid_crafting");
            }

            ItemStack[] grid = emptyArray(9);
            if (recipe instanceof IShapedCraftingRecipeWrapper) {
                IShapedCraftingRecipeWrapper shaped = (IShapedCraftingRecipeWrapper) recipe;
                int width = shaped.getWidth();
                int height = shaped.getHeight();
                if (width < 1 || width > 3 || height < 1 || height > 3 || inputSlots.size() > width * height) {
                    return PatternBuildResult.unsupported("ae2_quick_encoding.recipe.crafting_grid");
                }
                for (int i = 0; i < inputSlots.size(); i++) {
                    int row = i / width;
                    int column = i % width;
                    grid[row * 3 + column] = EncodingSettings.replaceInput(firstItem(inputSlots.get(i)));
                }
                if (hasFluids && !putFluidContainers(grid, fluidInputs)) {
                    return PatternBuildResult.unsupported("ae2_quick_encoding.recipe.fluid_crafting");
                }
            } else {
                List<ItemStack> itemInputs = replaceItems(firstItems(inputSlots));
                List<ItemStack> fluidContainers = filledBuckets(fluidInputs);
                if (fluidContainers.size() != fluidInputs.size()) {
                    return PatternBuildResult.unsupported("ae2_quick_encoding.recipe.fluid_crafting");
                }
                int target = 0;
                if (EncodingSettings.isCraftingFluidFirst()) {
                    for (ItemStack input : fluidContainers) {
                        if (target >= grid.length) {
                            return PatternBuildResult.unsupported("ae2_quick_encoding.recipe.fluid_crafting");
                        }
                        grid[target++] = input;
                    }
                }
                for (ItemStack input : itemInputs) {
                    if (target >= grid.length) {
                        return PatternBuildResult.unsupported("ae2_quick_encoding.recipe.crafting_grid");
                    }
                    grid[target++] = input;
                }
                if (!EncodingSettings.isCraftingFluidFirst()) {
                    for (ItemStack input : fluidContainers) {
                        if (target >= grid.length) {
                            return PatternBuildResult.unsupported("ae2_quick_encoding.recipe.fluid_crafting");
                        }
                        grid[target++] = input;
                    }
                }
            }
            if (!hasNonEmpty(grid)) {
                return PatternBuildResult.unsupported("ae2_quick_encoding.recipe.no_item_input");
            }
            return PatternBuildResult.supported(
                    new PatternData(true, EncodingSettings.isCraftingSubstitutionEnabled(), grid,
                            new ItemStack[]{outputs.get(0)}, new FluidStack[0], new FluidStack[0], false,
                            EncodingSettings.isCraftingFluidPatternEnabled()),
                    outputs.get(0),
                    outputs.get(0).getDisplayName()
            );
        }

        List<ItemStack> itemInputs = firstItems(inputSlots);
        List<ItemStack> filteredInputs = new ArrayList<>();
        for (ItemStack input : itemInputs) {
            if (!EncodingSettings.isBlacklisted(input)) {
                filteredInputs.add(EncodingSettings.replaceInput(input));
            }
        }
        if (EncodingSettings.isProcessingCombineEnabled()) {
            filteredInputs = combineItems(filteredInputs);
            outputs = combineItems(outputs);
            fluidInputs = combineFluids(fluidInputs);
            fluidOutputs = combineFluids(fluidOutputs);
        }

        int multiplier = EncodingSettings.getProcessingFurnaceCount();
        scaleItems(filteredInputs, multiplier);
        scaleItems(outputs, multiplier);
        scaleFluids(fluidInputs, multiplier);
        scaleFluids(fluidOutputs, multiplier);

        if (!PatternLimits.fitsProcessingSlots(
                filteredInputs.size(), outputs.size(), fluidInputs.size(), fluidOutputs.size())) {
            return PatternBuildResult.unsupported("ae2_quick_encoding.recipe.processing_limit");
        }

        ItemStack[] processingInputs = filteredInputs.toArray(new ItemStack[filteredInputs.size()]);
        if (!hasNonEmpty(processingInputs) && fluidInputs.isEmpty()) {
            return PatternBuildResult.unsupported("ae2_quick_encoding.recipe.no_item_input");
        }
        if (outputs.isEmpty() && fluidOutputs.isEmpty()) {
            return PatternBuildResult.unsupported("ae2_quick_encoding.recipe.no_output");
        }
        ItemStack displayOutput = outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0);
        String outputName = displayOutput.isEmpty()
                ? fluidOutputs.get(0).getLocalizedName()
                : displayOutput.getDisplayName();
        return PatternBuildResult.supported(
                new PatternData(false, false, processingInputs,
                        outputs.toArray(new ItemStack[outputs.size()]),
                        fluidInputs.toArray(new FluidStack[fluidInputs.size()]),
                        fluidOutputs.toArray(new FluidStack[fluidOutputs.size()]),
                        EncodingSettings.isProcessingFluidFirst()),
                displayOutput,
                outputName
        );
    }

    private static boolean putFluidContainers(ItemStack[] grid, List<FluidStack> fluids) {
        List<ItemStack> containers = filledBuckets(fluids);
        if (containers.size() != fluids.size()) {
            return false;
        }
        for (ItemStack container : containers) {
            int target = -1;
            for (int i = 0; i < grid.length; i++) {
                if (grid[i].isEmpty()) {
                    target = i;
                    break;
                }
            }
            if (target < 0) {
                return false;
            }
            grid[target] = container;
        }
        return true;
    }

    private static List<ItemStack> filledBuckets(List<FluidStack> fluids) {
        List<ItemStack> result = new ArrayList<>();
        for (FluidStack fluid : fluids) {
            ItemStack bucket = FluidUtil.getFilledBucket(fluid);
            if (bucket == null || bucket.isEmpty()) {
                return Collections.emptyList();
            }
            result.add(bucket.copy());
        }
        return result;
    }

    private static List<ItemStack> combineItems(List<ItemStack> source) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : source) {
            boolean merged = false;
            for (ItemStack existing : result) {
                if (sameItem(existing, stack)) {
                    existing.setCount(safeAdd(existing.getCount(), stack.getCount()));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                result.add(stack.copy());
            }
        }
        return result;
    }

    private static List<FluidStack> combineFluids(List<FluidStack> source) {
        List<FluidStack> result = new ArrayList<>();
        for (FluidStack fluid : source) {
            boolean merged = false;
            for (FluidStack existing : result) {
                if (existing.isFluidEqual(fluid)) {
                    existing.amount = safeAdd(existing.amount, fluid.amount);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                result.add(fluid.copy());
            }
        }
        return result;
    }

    private static void scaleItems(List<ItemStack> stacks, int multiplier) {
        for (ItemStack stack : stacks) {
            stack.setCount(safeMultiply(stack.getCount(), multiplier));
        }
    }

    private static void scaleFluids(List<FluidStack> fluids, int multiplier) {
        for (FluidStack fluid : fluids) {
            fluid.amount = safeMultiply(fluid.amount, multiplier);
        }
    }

    private static int safeAdd(int first, int second) {
        long result = (long) first + second;
        return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private static int safeMultiply(int value, int multiplier) {
        long result = (long) value * multiplier;
        return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        ItemStack left = first.copy();
        ItemStack right = second.copy();
        left.setCount(1);
        right.setCount(1);
        return ItemStack.areItemStacksEqual(left, right);
    }

    private static List<ItemStack> firstItems(List<List<ItemStack>> slots) {
        List<ItemStack> result = new ArrayList<>();
        for (List<ItemStack> slot : slots) {
            ItemStack item = firstItem(slot);
            if (!item.isEmpty()) {
                result.add(item);
            }
        }
        return result;
    }

    private static List<ItemStack> replaceItems(List<ItemStack> source) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : source) {
            ItemStack replacement = EncodingSettings.replaceInput(stack);
            if (!replacement.isEmpty()) {
                result.add(replacement);
            }
        }
        return result;
    }

    private static List<FluidStack> firstFluids(List<List<FluidStack>> slots) {
        List<FluidStack> result = new ArrayList<>();
        for (List<FluidStack> slot : slots) {
            if (slot == null) {
                continue;
            }
            for (FluidStack fluid : slot) {
                if (fluid != null && fluid.amount > 0 && fluid.getFluid() != null) {
                    result.add(fluid.copy());
                    break;
                }
            }
        }
        return result;
    }

    private static ItemStack firstItem(List<ItemStack> slot) {
        if (slot == null) {
            return ItemStack.EMPTY;
        }
        for (ItemStack stack : slot) {
            if (stack != null && !stack.isEmpty()) {
                return stack.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean hasAlternatives(List<List<ItemStack>> slots) {
        for (List<ItemStack> slot : slots) {
            int count = 0;
            for (ItemStack stack : slot) {
                if (stack != null && !stack.isEmpty()) {
                    count++;
                }
            }
            if (count > 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNonEmpty(ItemStack[] stacks) {
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack[] emptyArray(int size) {
        ItemStack[] result = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            result[i] = ItemStack.EMPTY;
        }
        return result;
    }

    private static IRecipeWrapper getRecipe(RecipeBookmarkItem<?> bookmark) throws IllegalAccessException {
        return (IRecipeWrapper) RECIPE_FIELD.get(bookmark);
    }

    private static IRecipeCategory<?> getCategory(RecipeBookmarkItem<?> bookmark) throws IllegalAccessException {
        return (IRecipeCategory<?>) CATEGORY_FIELD.get(bookmark);
    }

    private static Field getField(String name) {
        try {
            Field field = RecipeBookmarkItem.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("HEI recipe bookmark API changed: " + name, e);
        }
    }

    private static final class PatternBuildResult {
        private final PatternData data;
        private final ItemStack output;
        private final String reason;
        private final String outputName;

        private PatternBuildResult(PatternData data, ItemStack output, String reason, String outputName) {
            this.data = data;
            this.output = output == null ? ItemStack.EMPTY : output.copy();
            this.reason = reason;
            this.outputName = outputName == null ? "" : outputName;
        }

        private static PatternBuildResult supported(PatternData data, ItemStack output, String outputName) {
            return new PatternBuildResult(data, output, null, outputName);
        }

        private static PatternBuildResult unsupported(String reason) {
            return new PatternBuildResult(null, ItemStack.EMPTY, reason, "");
        }
    }
}
