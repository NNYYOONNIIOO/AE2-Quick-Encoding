package com.ae2quickencoding.client;

import com.ae2quickencoding.network.EncodeResultMessage;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientEncodeResultHandler implements IMessageHandler<EncodeResultMessage, IMessage> {
    @Override
    public IMessage onMessage(final EncodeResultMessage message, MessageContext context) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                BatchEncodingController.handleResult(message.getRequestId(), message.isCompleted());
            }
        });
        return null;
    }
}
