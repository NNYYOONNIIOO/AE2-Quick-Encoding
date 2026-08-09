package com.ae2quickencoding.mixin;

import zone.rong.mixinbooter.ILateMixinLoader;
import zone.rong.mixinbooter.MixinLoader;

import java.util.Collections;
import java.util.List;

@MixinLoader
public final class AE2QuickEncodingMixinLoader implements ILateMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.ae2_quick_encoding.json");
    }
}
