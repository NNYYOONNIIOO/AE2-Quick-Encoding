package com.ae2quickencoding.client;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.JsonToNBT;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@SideOnly(Side.CLIENT)
public final class EncodingSettings {
    private static final String CATEGORY_GENERAL = Configuration.CATEGORY_GENERAL;
    private static final String CATEGORY_BLACKLIST = "blacklist";
    private static final String CATEGORY_REPLACEMENTS = "replacements";
    private static final String BLACKLIST_KEY = "items";
    private static final String REPLACEMENTS_KEY = "items";

    private static final List<ItemStack> BLACKLIST = new ArrayList<>();
    private static final List<ItemStack> REPLACEMENTS = new ArrayList<>();
    private static Configuration configuration;
    private static boolean craftingSubstitution;
    private static boolean craftingFluidFirst;
    private static boolean craftingFluidPattern;
    private static int processingFurnaceCount = 1;
    private static boolean processingCombine;
    private static boolean processingFluidFirst;
    private static boolean replacementMode;

    private EncodingSettings() {
    }

    public static void init(File file) {
        configuration = new Configuration(file);
        load();
    }

    /**
     * Item registries from other mods may not exist during this mod's pre-init.
     * Reloading here resolves persisted item NBT after all pre-init handlers finish.
     */
    public static void reloadItemLists() {
        if (configuration == null) {
            return;
        }
        loadBlacklist();
        loadReplacements();
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static List<ItemStack> getBlacklist() {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : BLACKLIST) {
            result.add(stack.copy());
        }
        return result;
    }

    public static boolean addBlacklist(ItemStack stack) {
        ItemStack normalized = normalize(stack);
        if (normalized.isEmpty()) {
            return false;
        }
        for (ItemStack existing : BLACKLIST) {
            if (sameItem(existing, normalized)) {
                return false;
            }
        }
        BLACKLIST.add(normalized);
        saveBlacklist();
        return true;
    }

    public static boolean removeBlacklist(ItemStack stack) {
        for (int i = 0; i < BLACKLIST.size(); i++) {
            if (sameItem(BLACKLIST.get(i), stack)) {
                BLACKLIST.remove(i);
                saveBlacklist();
                return true;
            }
        }
        return false;
    }

    public static void clearBlacklist() {
        if (BLACKLIST.isEmpty()) {
            return;
        }
        BLACKLIST.clear();
        saveBlacklist();
    }

    public static boolean isBlacklisted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (ItemStack blocked : BLACKLIST) {
            if (sameItem(blocked, stack)) {
                return true;
            }
        }
        return false;
    }

    public static List<ItemStack> getReplacements() {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : REPLACEMENTS) {
            result.add(stack.copy());
        }
        return result;
    }

    public static boolean addReplacement(ItemStack stack) {
        ItemStack normalized = normalizeReplacement(stack);
        if (normalized.isEmpty()) {
            return false;
        }
        for (int i = 0; i < REPLACEMENTS.size(); i++) {
            ItemStack existing = REPLACEMENTS.get(i);
            if (!sameItemType(existing, normalized)) {
                continue;
            }
            if (sameItem(existing, normalized)) {
                return false;
            }
            REPLACEMENTS.set(i, normalized);
            saveReplacements();
            return true;
        }
        REPLACEMENTS.add(normalized);
        saveReplacements();
        return true;
    }

    public static boolean removeReplacement(ItemStack stack) {
        for (int i = 0; i < REPLACEMENTS.size(); i++) {
            if (sameItem(REPLACEMENTS.get(i), stack)) {
                REPLACEMENTS.remove(i);
                saveReplacements();
                return true;
            }
        }
        return false;
    }

    public static ItemStack replaceInput(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack fallback = ItemStack.EMPTY;
        for (ItemStack replacement : REPLACEMENTS) {
            if (sameItemType(replacement, stack)) {
                return withInputCount(replacement, stack.getCount());
            }
            if (fallback.isEmpty() && sameRegisteredItem(replacement, stack)) {
                fallback = replacement;
            }
        }
        if (!fallback.isEmpty()) {
            return withInputCount(fallback, stack.getCount());
        }
        return stack.copy();
    }

    public static boolean isReplacementMode() {
        return replacementMode;
    }

    public static void toggleReplacementMode() {
        replacementMode = !replacementMode;
        saveBoolean("replacementMode", replacementMode,
                "Select replacement-list mode when opening Quick Encoding settings.");
    }

    public static boolean isCraftingSubstitutionEnabled() {
        return craftingSubstitution;
    }

    public static void setCraftingSubstitutionEnabled(boolean value) {
        craftingSubstitution = value;
        saveBoolean("craftingSubstitution", value, "Enable ore dictionary substitution for crafting patterns.");
    }

    public static boolean isCraftingFluidFirst() {
        return craftingFluidFirst;
    }

    public static void setCraftingFluidFirst(boolean value) {
        craftingFluidFirst = value;
        saveBoolean("craftingFluidFirst", value, "Put fluid ingredients before item ingredients in crafting imports.");
    }

    public static boolean isCraftingFluidPatternEnabled() {
        return craftingFluidPattern;
    }

    public static void setCraftingFluidPatternEnabled(boolean value) {
        craftingFluidPattern = value;
        saveBoolean("craftingFluidPattern", value, "Use the AE2FC fluid crafting pattern for crafting imports.");
    }

    public static int getProcessingFurnaceCount() {
        return processingFurnaceCount;
    }

    public static void multiplyProcessingFurnace(int factor) {
        if (factor <= 1) {
            return;
        }
        processingFurnaceCount = saturatingMultiply(processingFurnaceCount, factor);
        saveInt("processingFurnaceCount", processingFurnaceCount, "Multiplier applied to processing pattern inputs and outputs.");
    }

    public static void divideProcessingFurnace(int divisor) {
        if (divisor <= 1) {
            return;
        }
        processingFurnaceCount = Math.max(1, processingFurnaceCount / divisor);
        saveInt("processingFurnaceCount", processingFurnaceCount, "Multiplier applied to processing pattern inputs and outputs.");
    }

    public static void resetProcessingFurnace() {
        if (processingFurnaceCount == 1) {
            return;
        }
        processingFurnaceCount = 1;
        saveInt("processingFurnaceCount", processingFurnaceCount, "Multiplier applied to processing pattern inputs and outputs.");
    }

    public static boolean isProcessingCombineEnabled() {
        return processingCombine;
    }

    public static void setProcessingCombineEnabled(boolean value) {
        processingCombine = value;
        saveBoolean("processingCombine", value, "Combine equal item and fluid stacks in processing imports.");
    }

    public static boolean isProcessingFluidFirst() {
        return processingFluidFirst;
    }

    public static void setProcessingFluidFirst(boolean value) {
        processingFluidFirst = value;
        saveBoolean("processingFluidFirst", value, "Put fluid inputs and outputs before item stacks in processing imports.");
    }

    private static void load() {
        loadBlacklist();
        loadReplacements();

        craftingSubstitution = configuration.getBoolean(
                "craftingSubstitution", CATEGORY_GENERAL, false,
                "Enable ore dictionary substitution for crafting patterns.");
        craftingFluidFirst = configuration.getBoolean(
                "craftingFluidFirst", CATEGORY_GENERAL, false,
                "Put fluid ingredients before item ingredients in crafting imports.");
        craftingFluidPattern = configuration.getBoolean(
                "craftingFluidPattern", CATEGORY_GENERAL, false,
                "Use the AE2FC fluid crafting pattern for crafting imports.");
        processingFurnaceCount = Math.max(1, configuration.getInt(
                "processingFurnaceCount", CATEGORY_GENERAL, 1, 1, Integer.MAX_VALUE,
                "Multiplier applied to processing pattern inputs and outputs."));
        processingCombine = configuration.getBoolean(
                "processingCombine", CATEGORY_GENERAL, false,
                "Combine equal item and fluid stacks in processing imports.");
        processingFluidFirst = configuration.getBoolean(
                "processingFluidFirst", CATEGORY_GENERAL, false,
                "Put fluid inputs and outputs before item stacks in processing imports.");
        replacementMode = configuration.getBoolean(
                "replacementMode", CATEGORY_GENERAL, false,
                "Select replacement-list mode when opening Quick Encoding settings.");
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    private static void loadBlacklist() {
        BLACKLIST.clear();
        String[] serialized = configuration.getStringList(
                BLACKLIST_KEY,
                CATEGORY_BLACKLIST,
                new String[0],
                "Item stacks excluded from imported processing pattern inputs.");
        for (String value : serialized) {
            ItemStack stack = deserialize(value);
            if (!stack.isEmpty() && !isBlacklisted(stack)) {
                BLACKLIST.add(normalize(stack));
            }
        }
    }

    private static void loadReplacements() {
        REPLACEMENTS.clear();
        String[] serialized = configuration.getStringList(
                REPLACEMENTS_KEY,
                CATEGORY_REPLACEMENTS,
                new String[0],
                "Item stacks that replace matching imported input items.");
        for (String value : serialized) {
            ItemStack stack = deserialize(value);
            if (stack.isEmpty()) {
                continue;
            }
            boolean replaced = false;
            for (int i = 0; i < REPLACEMENTS.size(); i++) {
                if (sameItemType(REPLACEMENTS.get(i), stack)) {
                    REPLACEMENTS.set(i, normalizeReplacement(stack));
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                REPLACEMENTS.add(normalizeReplacement(stack));
            }
        }
    }

    private static void saveBlacklist() {
        saveItemList(CATEGORY_BLACKLIST, BLACKLIST_KEY, BLACKLIST,
                "Item stacks excluded from imported processing pattern inputs.");
    }

    private static void saveReplacements() {
        saveItemList(CATEGORY_REPLACEMENTS, REPLACEMENTS_KEY, REPLACEMENTS,
                "Item stacks that replace matching imported input items.");
    }

    private static void saveItemList(String category, String key, List<ItemStack> stacks, String comment) {
        if (configuration == null) {
            return;
        }
        Property property = configuration.get(category, key, new String[0]);
        property.setComment(comment);
        String[] serialized = new String[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) {
            serialized[i] = serialize(stacks.get(i));
        }
        property.set(serialized);
        configuration.save();
    }

    private static void saveBoolean(String key, boolean value, String comment) {
        if (configuration == null) {
            return;
        }
        Property property = configuration.get(CATEGORY_GENERAL, key, value);
        property.setComment(comment);
        property.set(value);
        configuration.save();
    }

    private static void saveInt(String key, int value, String comment) {
        if (configuration == null) {
            return;
        }
        Property property = configuration.get(CATEGORY_GENERAL, key, value);
        property.setComment(comment);
        property.set(value);
        configuration.save();
    }

    private static ItemStack normalize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private static ItemStack normalizeReplacement(ItemStack stack) {
        ItemStack copy = normalize(stack);
        if (!copy.isEmpty() && copy.hasTagCompound() && copy.getTagCompound().hasNoTags()) {
            copy.setTagCompound(null);
        }
        return copy;
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        ItemStack left = normalize(first);
        ItemStack right = normalize(second);
        return !left.isEmpty()
                && !right.isEmpty()
                && ItemStack.areItemsEqual(left, right)
                && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static boolean sameItemType(ItemStack first, ItemStack second) {
        ItemStack left = normalize(first);
        ItemStack right = normalize(second);
        return !left.isEmpty() && !right.isEmpty() && ItemStack.areItemsEqual(left, right);
    }

    private static boolean sameRegisteredItem(ItemStack first, ItemStack second) {
        ItemStack left = normalize(first);
        ItemStack right = normalize(second);
        return !left.isEmpty() && !right.isEmpty() && left.getItem() == right.getItem();
    }

    private static ItemStack withInputCount(ItemStack replacement, int count) {
        // AE2 treats an empty tag compound as a distinct item variant.
        // A replacement captured from a ghost slot can still carry that empty compound.
        ItemStack result = normalizeReplacement(replacement);
        result.setCount(count);
        return result;
    }

    private static String serialize(ItemStack stack) {
        String value = normalize(stack).writeToNBT(new NBTTagCompound()).toString();
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static ItemStack deserialize(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            NBTBase tag = JsonToNBT.getTagFromJson(new String(bytes, StandardCharsets.UTF_8));
            return tag instanceof NBTTagCompound ? normalize(new ItemStack((NBTTagCompound) tag)) : ItemStack.EMPTY;
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static int saturatingMultiply(int value, int factor) {
        long result = (long) value * factor;
        return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }
}
