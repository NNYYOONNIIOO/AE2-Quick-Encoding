package com.ae2quickencoding.network;

import com.ae2quickencoding.model.PatternData;
import com.ae2quickencoding.model.PatternLimits;
import com.ae2quickencoding.server.PatternEncoder;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class EncodeRecipeMessage implements IMessage {
    private int requestId;
    private boolean removeBookmark;
    private boolean crafting;
    private boolean substitute;
    private boolean fluidFirst;
    private boolean craftingFluidPattern;
    private ItemStack[] inputs = new ItemStack[0];
    private ItemStack[] outputs = new ItemStack[0];
    private FluidStack[] fluidInputs = new FluidStack[0];
    private FluidStack[] fluidOutputs = new FluidStack[0];

    public EncodeRecipeMessage() {
    }

    public EncodeRecipeMessage(int requestId, PatternData data, boolean removeBookmark) {
        this.requestId = requestId;
        this.removeBookmark = removeBookmark;
        this.crafting = data.isCrafting();
        this.substitute = data.canSubstitute();
        this.fluidFirst = data.isFluidFirst();
        this.craftingFluidPattern = data.isCraftingFluidPattern();
        this.inputs = data.getInputs();
        this.outputs = data.getOutputs();
        this.fluidInputs = data.getFluidInputs();
        this.fluidOutputs = data.getFluidOutputs();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.requestId = buf.readInt();
        this.removeBookmark = buf.readBoolean();
        this.crafting = buf.readBoolean();
        this.substitute = buf.readBoolean();
        this.fluidFirst = buf.readBoolean();
        this.craftingFluidPattern = buf.readBoolean();
        int inputCount = readCount(buf);
        int outputCount = readCount(buf);
        int fluidInputCount = readCount(buf);
        int fluidOutputCount = readCount(buf);
        validateCountLimits(this.crafting, inputCount, outputCount, fluidInputCount, fluidOutputCount);
        validateCounts(buf, inputCount, outputCount, fluidInputCount, fluidOutputCount);

        this.inputs = readStacks(buf, inputCount);
        this.outputs = readStacks(buf, outputCount);
        this.fluidInputs = readFluids(buf, fluidInputCount);
        this.fluidOutputs = readFluids(buf, fluidOutputCount);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(requestId);
        buf.writeBoolean(removeBookmark);
        buf.writeBoolean(crafting);
        buf.writeBoolean(substitute);
        buf.writeBoolean(fluidFirst);
        buf.writeBoolean(craftingFluidPattern);
        buf.writeInt(inputs.length);
        buf.writeInt(outputs.length);
        buf.writeInt(fluidInputs.length);
        buf.writeInt(fluidOutputs.length);
        writeStacks(buf, inputs);
        writeStacks(buf, outputs);
        writeFluids(buf, fluidInputs);
        writeFluids(buf, fluidOutputs);
    }

    private static ItemStack[] readStacks(ByteBuf buf, int count) {
        ItemStack[] stacks = new ItemStack[count];
        for (int i = 0; i < count; i++) {
            stacks[i] = ByteBufUtils.readItemStack(buf);
        }
        return stacks;
    }

    private static int readCount(ByteBuf buf) {
        int count = buf.readInt();
        if (count < 0) {
            throw new IllegalArgumentException("Negative pattern stack count");
        }
        return count;
    }

    private static void validateCounts(ByteBuf buf, int... counts) {
        long total = 0L;
        for (int count : counts) {
            total += count;
        }
        if (total > buf.readableBytes()) {
            throw new IllegalArgumentException("Malformed pattern request");
        }
    }

    private static void validateCountLimits(boolean crafting, int inputCount, int outputCount,
                                            int fluidInputCount, int fluidOutputCount) {
        if (crafting) {
            if (inputCount != PatternLimits.CRAFTING_INPUTS || outputCount != 1
                    || fluidInputCount != 0 || fluidOutputCount != 0) {
                throw new IllegalArgumentException("Invalid crafting pattern shape");
            }
            return;
        }
        if (!PatternLimits.fitsMaximumProcessingSlots(inputCount, outputCount, fluidInputCount, fluidOutputCount)) {
            throw new IllegalArgumentException("Processing pattern exceeds supported capacity");
        }
    }

    private static void writeStacks(ByteBuf buf, ItemStack[] stacks) {
        for (ItemStack stack : stacks) {
            ByteBufUtils.writeItemStack(buf, stack == null ? ItemStack.EMPTY : stack);
        }
    }

    private static FluidStack[] readFluids(ByteBuf buf, int count) {
        FluidStack[] fluids = new FluidStack[count];
        for (int i = 0; i < count; i++) {
            fluids[i] = FluidStack.loadFluidStackFromNBT(ByteBufUtils.readTag(buf));
        }
        return fluids;
    }

    private static void writeFluids(ByteBuf buf, FluidStack[] fluids) {
        for (FluidStack fluid : fluids) {
            ByteBufUtils.writeTag(buf, fluid == null ? new NBTTagCompound() : fluid.writeToNBT(new NBTTagCompound()));
        }
    }

    public static final class Handler implements IMessageHandler<EncodeRecipeMessage, IMessage> {
        @Override
        public IMessage onMessage(final EncodeRecipeMessage message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    boolean completed = PatternEncoder.encode(player, new PatternData(
                            message.crafting,
                            message.substitute,
                            message.inputs,
                            message.outputs,
                            message.fluidInputs,
                            message.fluidOutputs,
                            message.fluidFirst,
                            message.craftingFluidPattern
                    ));
                    NetworkHandler.CHANNEL.sendTo(new EncodeResultMessage(message.requestId, completed), player);
                }
            });
            return null;
        }
    }
}
