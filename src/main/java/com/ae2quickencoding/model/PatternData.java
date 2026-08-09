package com.ae2quickencoding.model;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public final class PatternData {
    private final boolean crafting;
    private final boolean substitute;
    private final ItemStack[] itemInputs;
    private final ItemStack[] itemOutputs;
    private final FluidStack[] fluidInputs;
    private final FluidStack[] fluidOutputs;
    private final boolean fluidFirst;
    private final boolean craftingFluidPattern;

    public PatternData(boolean crafting, boolean substitute, ItemStack[] inputs, ItemStack[] outputs) {
        this(crafting, substitute, inputs, outputs, new FluidStack[0], new FluidStack[0], false, false);
    }

    public PatternData(boolean crafting, boolean substitute, ItemStack[] itemInputs, ItemStack[] itemOutputs,
                       FluidStack[] fluidInputs, FluidStack[] fluidOutputs) {
        this(crafting, substitute, itemInputs, itemOutputs, fluidInputs, fluidOutputs, false, false);
    }

    public PatternData(boolean crafting, boolean substitute, ItemStack[] itemInputs, ItemStack[] itemOutputs,
                       FluidStack[] fluidInputs, FluidStack[] fluidOutputs, boolean fluidFirst) {
        this(crafting, substitute, itemInputs, itemOutputs, fluidInputs, fluidOutputs, fluidFirst, false);
    }

    public PatternData(boolean crafting, boolean substitute, ItemStack[] itemInputs, ItemStack[] itemOutputs,
                       FluidStack[] fluidInputs, FluidStack[] fluidOutputs, boolean fluidFirst,
                       boolean craftingFluidPattern) {
        this.crafting = crafting;
        this.substitute = substitute;
        this.itemInputs = copyItems(itemInputs);
        this.itemOutputs = copyItems(itemOutputs);
        this.fluidInputs = copyFluids(fluidInputs);
        this.fluidOutputs = copyFluids(fluidOutputs);
        this.fluidFirst = fluidFirst;
        this.craftingFluidPattern = craftingFluidPattern;
    }

    public boolean isCrafting() {
        return crafting;
    }

    public boolean canSubstitute() {
        return substitute;
    }

    public ItemStack[] getInputs() {
        return getItemInputs();
    }

    public ItemStack[] getOutputs() {
        return getItemOutputs();
    }

    public ItemStack[] getItemInputs() {
        return copyItems(itemInputs);
    }

    public ItemStack[] getItemOutputs() {
        return copyItems(itemOutputs);
    }

    public FluidStack[] getFluidInputs() {
        return copyFluids(fluidInputs);
    }

    public FluidStack[] getFluidOutputs() {
        return copyFluids(fluidOutputs);
    }

    public boolean hasFluids() {
        return fluidInputs.length > 0 || fluidOutputs.length > 0;
    }

    public boolean isFluidFirst() {
        return fluidFirst;
    }

    public boolean isCraftingFluidPattern() {
        return craftingFluidPattern;
    }

    private static ItemStack[] copyItems(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i] == null ? ItemStack.EMPTY : source[i].copy();
        }
        return result;
    }

    private static FluidStack[] copyFluids(FluidStack[] source) {
        if (source == null) {
            return new FluidStack[0];
        }
        FluidStack[] result = new FluidStack[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i] == null ? null : source[i].copy();
        }
        return result;
    }
}
