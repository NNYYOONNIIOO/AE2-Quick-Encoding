package com.ae2quickencoding.server;

import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.slot.SlotRestrictedInput;
import appeng.util.item.AEItemStack;
import com.ae2quickencoding.model.PatternData;
import com.ae2quickencoding.model.PatternLimits;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PatternEncoder {
    private static final ResourceLocation AE2FC_DENSE_PATTERN =
            new ResourceLocation("ae2fc", "dense_encoded_pattern");
    private static final ResourceLocation AE2FC_DENSE_CRAFT_PATTERN =
            new ResourceLocation("ae2fc", "dense_craft_encoded_pattern");
    private static final ResourceLocation AE2FC_FLUID_DROP =
            new ResourceLocation("ae2fc", "fluid_drop");

    private PatternEncoder() {
    }

    public static boolean encode(EntityPlayerMP player, PatternData data) {
        if (!hasValidShape(data)) {
            send(player, "ae2_quick_encoding.message.invalid_shape");
            return false;
        }

        ItemStack[] itemInputs = data.getItemInputs();
        ItemStack[] itemOutputs = data.getItemOutputs();
        FluidStack[] fluidInputs = data.getFluidInputs();
        FluidStack[] fluidOutputs = data.getFluidOutputs();
        ItemStack primaryItemOutput = firstNonEmpty(itemOutputs);
        FluidStack primaryFluidOutput = primaryItemOutput.isEmpty() ? firstFluid(fluidOutputs) : null;
        if ((primaryItemOutput.isEmpty() && primaryFluidOutput == null)
                || (!hasNonEmptyInput(itemInputs) && !hasFluid(fluidInputs))) {
            send(player, "ae2_quick_encoding.message.empty_recipe");
            return false;
        }

        boolean fluidProcessingPattern = data.hasFluids() && !data.isCrafting();
        boolean fluidCraftingPattern = data.isCrafting()
                && data.isCraftingFluidPattern()
                && isFluidCraftingPattern(player.world, itemInputs);
        boolean ae2fcPattern = fluidProcessingPattern || fluidCraftingPattern;
        if (ae2fcPattern && !Loader.isModLoaded("ae2fc")) {
            send(player, "ae2_quick_encoding.message.missing_ae2fc");
            return false;
        }

        IItemDefinition blankDefinition = AEApi.instance().definitions().materials().blankPattern();
        Optional<ItemStack> encodedStack = getEncodedPattern(fluidProcessingPattern, fluidCraftingPattern);
        if (!encodedStack.isPresent()) {
            send(player, ae2fcPattern
                    ? "ae2_quick_encoding.message.missing_ae2fc"
                    : "ae2_quick_encoding.message.missing_encoded_pattern");
            return false;
        }

        if (hasEncodedOutput(player, primaryItemOutput, primaryFluidOutput)) {
            send(player, "ae2_quick_encoding.message.already_encoded", outputName(primaryItemOutput, primaryFluidOutput));
            return true;
        }

        int blankSlot = findBlankPattern(player, blankDefinition);
        SlotRestrictedInput terminalBlankSlot = blankSlot < 0
                ? findTerminalBlankPattern(player, blankDefinition)
                : null;
        if (terminalBlankSlot == null && blankSlot < 0) {
            send(player, "ae2_quick_encoding.message.no_blank_pattern");
            return false;
        }

        ItemStack result = encodedStack.get();
        result.setTagCompound(createPatternTag(data, fluidProcessingPattern));
        int destination = findDestination(player, result, blankSlot);
        if (destination < 0) {
            send(player, "ae2_quick_encoding.message.no_inventory_space");
            return false;
        }

        if (terminalBlankSlot == null) {
            storePattern(player, blankSlot, destination, result);
        } else {
            consumeTerminalBlank(terminalBlankSlot);
            storePatternInPlayer(player, destination, result);
        }
        player.inventory.markDirty();
        if (player.openContainer != null) {
            player.openContainer.detectAndSendChanges();
        }
        if (player.inventoryContainer != null && player.inventoryContainer != player.openContainer) {
            player.inventoryContainer.detectAndSendChanges();
        }
        send(player, "ae2_quick_encoding.message.encoded", outputName(primaryItemOutput, primaryFluidOutput));
        return true;
    }

    private static Optional<ItemStack> getEncodedPattern(boolean fluidProcessingPattern, boolean fluidCraftingPattern) {
        if (!fluidProcessingPattern && !fluidCraftingPattern) {
            return AEApi.instance().definitions().items().encodedPattern().maybeStack(1);
        }
        ResourceLocation registryName = fluidCraftingPattern
                ? AE2FC_DENSE_CRAFT_PATTERN
                : AE2FC_DENSE_PATTERN;
        Item item = ForgeRegistries.ITEMS.getValue(registryName);
        return item == null ? Optional.<ItemStack>empty() : Optional.of(new ItemStack(item, 1));
    }

    private static boolean hasValidShape(PatternData data) {
        if (data == null) {
            return false;
        }
        ItemStack[] itemInputs = data.getItemInputs();
        ItemStack[] itemOutputs = data.getItemOutputs();
        FluidStack[] fluidInputs = data.getFluidInputs();
        FluidStack[] fluidOutputs = data.getFluidOutputs();
        if (data.isCrafting()) {
            return itemInputs.length == PatternLimits.CRAFTING_INPUTS
                    && itemOutputs.length == 1
                    && fluidInputs.length == 0
                    && fluidOutputs.length == 0;
        }
        if (data.hasFluids() && !Loader.isModLoaded("ae2fc")) {
            return false;
        }
        if (!PatternLimits.fitsProcessingSlots(
                itemInputs.length, itemOutputs.length, fluidInputs.length, fluidOutputs.length)) {
            return false;
        }
        if (!data.hasFluids() && itemOutputs.length == 0) {
            return false;
        }
        for (FluidStack fluid : fluidInputs) {
            if (!validFluid(fluid)) {
                return false;
            }
        }
        for (FluidStack fluid : fluidOutputs) {
            if (!validFluid(fluid)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasNonEmptyInput(ItemStack[] inputs) {
        for (ItemStack input : inputs) {
            if (input != null && !input.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFluid(FluidStack[] fluids) {
        for (FluidStack fluid : fluids) {
            if (validFluid(fluid)) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack firstNonEmpty(ItemStack[] stacks) {
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static FluidStack firstFluid(FluidStack[] fluids) {
        for (FluidStack fluid : fluids) {
            if (validFluid(fluid)) {
                return fluid;
            }
        }
        return null;
    }

    private static boolean isFluidCraftingPattern(World world, ItemStack[] inputs) {
        if (!Loader.isModLoaded("ae2fc")) {
            return false;
        }
        InventoryCrafting inventory = createCraftingInventory(inputs);
        IRecipe recipe = CraftingManager.findMatchingRecipe(inventory, world);
        if (recipe == null) {
            return false;
        }
        List<ItemStack> remaining = recipe.getRemainingItems(inventory);
        for (int i = 0; i < inputs.length && i < remaining.size(); i++) {
            ItemStack input = inputs[i];
            if (getFluidFromItem(input) == null) {
                continue;
            }
            if (sameStack(remaining.get(i), getEmptiedContainer(input))) {
                return true;
            }
        }
        return false;
    }

    private static InventoryCrafting createCraftingInventory(ItemStack[] inputs) {
        InventoryCrafting inventory = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer playerIn) {
                return false;
            }
        }, 3, 3);
        for (int i = 0; i < inputs.length; i++) {
            inventory.setInventorySlotContents(i, inputs[i] == null ? ItemStack.EMPTY : inputs[i].copy());
        }
        return inventory;
    }

    private static boolean hasEncodedOutput(EntityPlayerMP player, ItemStack targetItem, FluidStack targetFluid) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof ICraftingPatternItem)) {
                continue;
            }
            try {
                ICraftingPatternDetails details = ((ICraftingPatternItem) stack.getItem())
                        .getPatternForItem(stack, player.world);
                if (details == null) {
                    continue;
                }
                for (IAEItemStack output : details.getCondensedOutputs()) {
                    if (output == null) {
                        continue;
                    }
                    ItemStack outputStack = output.createItemStack();
                    if (!targetItem.isEmpty() && sameStack(outputStack, targetItem)) {
                        return true;
                    }
                    IAEItemStack fluidDrop = targetFluid == null ? null : fluidAsAEItem(targetFluid);
                    if (fluidDrop != null && sameStack(outputStack, fluidDrop.createItemStack())) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
                // Invalid patterns are left untouched and do not block a valid recipe.
            }
        }
        return false;
    }

    private static int findBlankPattern(EntityPlayerMP player, IItemDefinition blankDefinition) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && blankDefinition.isSameAs(stack)) {
                return i;
            }
        }
        return -1;
    }

    private static SlotRestrictedInput findTerminalBlankPattern(EntityPlayerMP player, IItemDefinition blankDefinition) {
        if (!(player.openContainer instanceof ContainerPatternEncoder)) {
            return null;
        }
        ContainerPatternEncoder container = (ContainerPatternEncoder) player.openContainer;
        for (Slot slot : container.inventorySlots) {
            if (!(slot instanceof SlotRestrictedInput)) {
                continue;
            }
            SlotRestrictedInput restricted = (SlotRestrictedInput) slot;
            if (restricted.getPlaceableItemType() == SlotRestrictedInput.PlacableItemType.BLANK_PATTERN
                    && blankDefinition.isSameAs(restricted.getStack())) {
                return restricted;
            }
        }
        return null;
    }

    private static int findDestination(EntityPlayerMP player, ItemStack result, int blankSlot) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack existing = player.inventory.getStackInSlot(i);
            if (!existing.isEmpty()
                    && existing.getItem() == result.getItem()
                    && ItemStack.areItemStacksEqual(existing, result)
                    && existing.getCount() < Math.min(existing.getMaxStackSize(), result.getMaxStackSize())) {
                return i;
            }
        }
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            if (player.inventory.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        if (blankSlot >= 0) {
            ItemStack blank = player.inventory.getStackInSlot(blankSlot);
            return blank.getCount() == 1 ? blankSlot : -1;
        }
        return -1;
    }

    private static void storePattern(EntityPlayerMP player, int blankSlot, int destination, ItemStack result) {
        ItemStack blank = player.inventory.getStackInSlot(blankSlot);
        if (destination == blankSlot && blank.getCount() == 1) {
            player.inventory.setInventorySlotContents(destination, result);
            return;
        }

        blank.shrink(1);
        if (blank.isEmpty()) {
            player.inventory.setInventorySlotContents(blankSlot, ItemStack.EMPTY);
        }

        ItemStack existing = player.inventory.getStackInSlot(destination);
        if (!existing.isEmpty() && existing.getItem() == result.getItem()
                && ItemStack.areItemStacksEqual(existing, result)) {
            existing.grow(1);
        } else {
            player.inventory.setInventorySlotContents(destination, result);
        }
    }

    private static void storePatternInPlayer(EntityPlayerMP player, int destination, ItemStack result) {
        ItemStack existing = player.inventory.getStackInSlot(destination);
        if (!existing.isEmpty() && existing.getItem() == result.getItem()
                && ItemStack.areItemStacksEqual(existing, result)) {
            existing.grow(1);
        } else {
            player.inventory.setInventorySlotContents(destination, result);
        }
    }

    private static void consumeTerminalBlank(SlotRestrictedInput slot) {
        ItemStack stack = slot.getStack();
        if (stack.getCount() <= 1) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            stack.shrink(1);
            slot.onSlotChanged();
        }
    }

    private static NBTTagCompound createPatternTag(PatternData data, boolean fluidPattern) {
        if (fluidPattern) {
            List<IAEItemStack> inputs = toAEItems(data.getItemInputs(), data.getFluidInputs(), data.isFluidFirst());
            List<IAEItemStack> outputs = toAEItems(data.getItemOutputs(), data.getFluidOutputs(), data.isFluidFirst());
            NBTTagCompound encodedValue = new NBTTagCompound();
            encodedValue.setTag("Inputs", writeAEStackArray(inputs));
            encodedValue.setTag("Outputs", writeAEStackArray(outputs));
            encodedValue.setTag("in", writeAEStackArray(inputs));
            encodedValue.setTag("out", writeAEStackArray(outputs));
            encodedValue.setBoolean("crafting", false);
            encodedValue.setBoolean("substitute", data.canSubstitute());
            return encodedValue;
        }

        NBTTagCompound encodedValue = new NBTTagCompound();
        NBTTagList inputTags = new NBTTagList();
        NBTTagList outputTags = new NBTTagList();
        for (ItemStack input : data.getItemInputs()) {
            inputTags.appendTag(stackTag(input));
        }
        for (ItemStack output : data.getItemOutputs()) {
            outputTags.appendTag(stackTag(output));
        }
        encodedValue.setTag("in", inputTags);
        encodedValue.setTag("out", outputTags);
        encodedValue.setBoolean("crafting", data.isCrafting());
        encodedValue.setBoolean("substitute", data.canSubstitute());
        return encodedValue;
    }

    private static List<IAEItemStack> toAEItems(ItemStack[] items, FluidStack[] fluids, boolean fluidFirst) {
        List<IAEItemStack> result = new ArrayList<>();
        if (fluidFirst) {
            addFluids(result, fluids);
            addItems(result, items);
        } else {
            addItems(result, items);
            addFluids(result, fluids);
        }
        return result;
    }

    private static void addItems(List<IAEItemStack> result, ItemStack[] items) {
        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            IAEItemStack aeItem = AEItemStack.fromItemStack(canonicalizePatternStack(item));
            if (aeItem != null) {
                result.add(aeItem);
            }
        }
    }

    private static void addFluids(List<IAEItemStack> result, FluidStack[] fluids) {
        for (FluidStack fluid : fluids) {
            IAEItemStack aeFluid = fluidAsAEItem(fluid);
            if (aeFluid != null) {
                result.add(aeFluid);
            }
        }
    }

    private static NBTTagList writeAEStackArray(List<IAEItemStack> stacks) {
        NBTTagList result = new NBTTagList();
        for (IAEItemStack stack : stacks) {
            NBTTagCompound tag = new NBTTagCompound();
            stack.writeToNBT(tag);
            result.appendTag(tag);
        }
        return result;
    }

    private static IAEItemStack fluidAsAEItem(FluidStack fluid) {
        if (!validFluid(fluid) || !Loader.isModLoaded("ae2fc")) {
            return null;
        }
        Item fluidDrop = ForgeRegistries.ITEMS.getValue(AE2FC_FLUID_DROP);
        if (fluidDrop == null) {
            return null;
        }
        ItemStack drop = new ItemStack(fluidDrop, fluid.amount);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Fluid", fluid.getFluid().getName());
        if (fluid.tag != null) {
            tag.setTag("FluidTag", fluid.tag.copy());
        }
        drop.setTagCompound(tag);
        return AEItemStack.fromItemStack(drop);
    }

    private static NBTTagCompound stackTag(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? new NBTTagCompound()
                : canonicalizePatternStack(stack).writeToNBT(new NBTTagCompound());
    }

    private static ItemStack canonicalizePatternStack(ItemStack stack) {
        ItemStack result = stack.copy();
        if (result.hasTagCompound() && result.getTagCompound().hasNoTags()) {
            result.setTagCompound(null);
        }
        return result;
    }

    private static FluidStack getFluidFromItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || !stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            return null;
        }
        IFluidHandlerItem handler = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        if (handler == null) {
            return null;
        }
        IFluidTankProperties[] tanks = handler.getTankProperties();
        for (IFluidTankProperties tank : tanks) {
            if (tank != null && tank.getContents() != null && tank.getContents().amount > 0) {
                return tank.getContents().copy();
            }
        }
        return null;
    }

    private static ItemStack getEmptiedContainer(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || !stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            return stack == null ? ItemStack.EMPTY : stack;
        }
        ItemStack copy = stack.copy();
        IFluidHandlerItem handler = copy.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        if (handler == null) {
            return stack;
        }
        handler.drain(Integer.MAX_VALUE, true);
        return handler.getContainer();
    }

    private static boolean validFluid(FluidStack fluid) {
        return fluid != null && fluid.amount > 0 && fluid.getFluid() != null;
    }

    private static String outputName(ItemStack item, FluidStack fluid) {
        return item.isEmpty() ? fluid.getLocalizedName() : item.getDisplayName();
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) {
            return false;
        }
        ItemStack left = first.copy();
        ItemStack right = second.copy();
        left.setCount(1);
        right.setCount(1);
        return ItemStack.areItemStacksEqual(left, right);
    }

    private static void send(EntityPlayerMP player, String key, Object... args) {
        player.sendMessage(new TextComponentTranslation(key, args));
    }
}
