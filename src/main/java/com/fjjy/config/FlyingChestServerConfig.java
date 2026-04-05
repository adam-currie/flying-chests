package com.fjjy.config;

import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;
import me.fzzyhmstrs.fzzy_config.config.Config;

import net.minecraft.resources.Identifier;

public class FlyingChestServerConfig extends Config {

    public static final FlyingChestServerConfig INSTANCE =
        ConfigApiJava.registerAndLoadConfig(FlyingChestServerConfig::new, RegisterType.SERVER);

    public FlyingChestServerConfig() {
        super(Identifier.fromNamespaceAndPath("flying-chests", "config"));
    }

    // ---- Wild Chest ----

    public int wildChestAttackRange = 5;

    public static void init() {}
}
