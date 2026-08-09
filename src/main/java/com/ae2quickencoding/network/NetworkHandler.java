package com.ae2quickencoding.network;

import com.ae2quickencoding.AE2QuickEncoding;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class NetworkHandler {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(AE2QuickEncoding.MOD_ID);

    private static boolean initialized;

    private NetworkHandler() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        CHANNEL.registerMessage(EncodeRecipeMessage.Handler.class, EncodeRecipeMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(
                EncodeResultMessage.ServerRegistrationHandler.class,
                EncodeResultMessage.class,
                1,
                Side.SERVER
        );
    }

}
