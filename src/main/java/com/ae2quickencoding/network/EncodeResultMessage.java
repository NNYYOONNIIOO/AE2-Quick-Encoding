package com.ae2quickencoding.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class EncodeResultMessage implements IMessage {
    private int requestId;
    private boolean completed;

    public EncodeResultMessage() {
    }

    public EncodeResultMessage(int requestId, boolean completed) {
        this.requestId = requestId;
        this.completed = completed;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.requestId = buf.readInt();
        this.completed = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(requestId);
        buf.writeBoolean(completed);
    }

    public int getRequestId() {
        return requestId;
    }

    public boolean isCompleted() {
        return completed;
    }

    public static final class ServerRegistrationHandler implements IMessageHandler<EncodeResultMessage, IMessage> {
        @Override
        public IMessage onMessage(EncodeResultMessage message, MessageContext context) {
            return null;
        }
    }
}
