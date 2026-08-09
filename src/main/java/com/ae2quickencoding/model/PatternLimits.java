package com.ae2quickencoding.model;

import net.minecraftforge.fml.common.Loader;

/**
 * Limits shared by the client recipe reader, packet decoder and server encoder.
 */
public final class PatternLimits {
    public static final int CRAFTING_INPUTS = 9;
    public static final int BASE_PROCESSING_INPUTS = 16;
    public static final int BASE_PROCESSING_OUTPUTS = 6;
    public static final int EXTENDED_PROCESSING_INPUTS = 81;
    public static final int EXTENDED_PROCESSING_OUTPUTS = 27;
    public static final int FLUID_INPUTS = 100;
    public static final int FLUID_OUTPUTS = 100;
    private static final String RANDOM_COMPLEMENT_MOD_ID = "random_complement";
    private static final String RANDOM_COMPLEMENT_CONFIG = "com.circulation.random_complement.RCConfig";

    private PatternLimits() {
    }

    public static boolean fitsProcessingSlots(int itemInputs, int itemOutputs, int fluidInputs, int fluidOutputs) {
        int inputLimit = isExtendedProcessingEnabled()
                ? EXTENDED_PROCESSING_INPUTS
                : BASE_PROCESSING_INPUTS;
        int outputLimit = isExtendedProcessingEnabled()
                ? EXTENDED_PROCESSING_OUTPUTS
                : BASE_PROCESSING_OUTPUTS;
        return fitsProcessingSlots(itemInputs, itemOutputs, fluidInputs, fluidOutputs, inputLimit, outputLimit);
    }

    public static boolean fitsMaximumProcessingSlots(int itemInputs, int itemOutputs, int fluidInputs,
                                                      int fluidOutputs) {
        return fitsProcessingSlots(itemInputs, itemOutputs, fluidInputs, fluidOutputs,
                EXTENDED_PROCESSING_INPUTS, EXTENDED_PROCESSING_OUTPUTS);
    }

    public static boolean isExtendedProcessingEnabled() {
        if (!Loader.isModLoaded(RANDOM_COMPLEMENT_MOD_ID)) {
            return false;
        }
        try {
            Class<?> configClass = Class.forName(RANDOM_COMPLEMENT_CONFIG);
            Object ae2 = configClass.getField("AE2").get(null);
            Class<?> ae2Class = ae2.getClass();
            return ae2Class.getField("Enable").getBoolean(ae2)
                    && ae2Class.getField("newPattenGui").getBoolean(ae2);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean fitsProcessingSlots(int itemInputs, int itemOutputs, int fluidInputs, int fluidOutputs,
                                               int inputLimit, int outputLimit) {
        return itemInputs <= inputLimit
                && itemOutputs <= outputLimit
                && fluidInputs <= FLUID_INPUTS
                && fluidOutputs <= FLUID_OUTPUTS;
    }
}
