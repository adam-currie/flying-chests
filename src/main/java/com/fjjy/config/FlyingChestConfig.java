package com.fjjy.config;

import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;
import me.fzzyhmstrs.fzzy_config.config.Config;

import net.minecraft.resources.Identifier;

public class FlyingChestConfig extends Config {

    public static final FlyingChestConfig INSTANCE =
        ConfigApiJava.registerAndLoadConfig(FlyingChestConfig::new, RegisterType.SERVER);

    public FlyingChestConfig() {
        super(Identifier.fromNamespaceAndPath("flying-chests", "config"));
    }

    // ---- Wild Chest ----

    public int wildChestAttackRange = 5;

    public static void init() {}
}
