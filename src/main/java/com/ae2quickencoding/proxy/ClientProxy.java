package com.ae2quickencoding.proxy;

import com.ae2quickencoding.client.ClientHandler;
import com.ae2quickencoding.client.ClientEncodeResultHandler;
import com.ae2quickencoding.client.EncodingSettings;
import com.ae2quickencoding.network.EncodeResultMessage;
import com.ae2quickencoding.network.NetworkHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        EncodingSettings.init(event.getSuggestedConfigurationFile());
        NetworkHandler.CHANNEL.registerMessage(
                ClientEncodeResultHandler.class,
                EncodeResultMessage.class,
                1,
                Side.CLIENT
        );
        MinecraftForge.EVENT_BUS.register(new ClientHandler());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        EncodingSettings.reloadItemLists();
    }
}
