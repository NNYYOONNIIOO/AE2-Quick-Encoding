package com.ae2quickencoding;

import com.ae2quickencoding.network.NetworkHandler;
import com.ae2quickencoding.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(
        modid = AE2QuickEncoding.MOD_ID,
        name = AE2QuickEncoding.NAME,
        version = AE2QuickEncoding.VERSION,
        dependencies = "required-after:appliedenergistics2;required-after:jei;after:ae2fc"
)
public final class AE2QuickEncoding {
    public static final String MOD_ID = "ae2_quick_encoding";
    public static final String NAME = "AE2 Quick Encoding";
    public static final String VERSION = "1.0.2";

    @Mod.Instance(MOD_ID)
    public static AE2QuickEncoding instance;

    @SidedProxy(
            clientSide = "com.ae2quickencoding.proxy.ClientProxy",
            serverSide = "com.ae2quickencoding.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        NetworkHandler.init();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
