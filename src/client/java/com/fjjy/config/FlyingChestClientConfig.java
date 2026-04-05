package com.fjjy.config;

import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigGroup;
import me.fzzyhmstrs.fzzy_config.util.EnumTranslatable;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.NotNull;

public class FlyingChestClientConfig extends Config {

    public static final FlyingChestClientConfig INSTANCE =
        ConfigApiJava.registerAndLoadConfig(FlyingChestClientConfig::new, RegisterType.CLIENT);

    public FlyingChestClientConfig() {
        super(Identifier.fromNamespaceAndPath("flying-chests", "client_config"));
    }

    // ---- Behaviour ----

    public boolean allowRightClickWhileFlying = false;

    // ---- Flying Chest group ----

    public ConfigGroup chestGroup = new ConfigGroup("chest");
    public boolean chestUseResourcePack = false;
    @ConfigGroup.Pop
    public ChestVariant chestVariant = ChestVariant.BASIC;

    // ---- Base Station group ----

    public ConfigGroup baseGroup = new ConfigGroup("base");
    public boolean baseUseResourcePack = false;
    @ConfigGroup.Pop
    public ChestVariant baseVariant = ChestVariant.BASIC;

    // ---- Wings group ----

    public ConfigGroup wingsGroup = new ConfigGroup("wings");
    public boolean wingsUseResourcePack = false;
    public WingVariant wingsVariant = WingVariant.BEE;
    @ConfigGroup.Pop
    public float flapSpeed = 1.0F;

    // ---- Variants ----

    public enum ChestVariant implements EnumTranslatable {
        BASIC     ("textures/entity/chest/normal.png"),
        CHRISTMAS ("textures/entity/chest/christmas.png"),
        ENDER     ("textures/entity/chest/ender.png"),
        TRAPPED   ("textures/entity/chest/trapped.png");

        public final Identifier modTexture;
        public final Identifier mcTexture;

        ChestVariant(String path) {
            this.modTexture = Identifier.fromNamespaceAndPath("flying-chests", path);
            this.mcTexture  = Identifier.withDefaultNamespace(path);
        }

        @NotNull
        @Override
        public String prefix() {
            return "flying-chests.chest_variant";
        }
    }

    public enum WingVariant implements EnumTranslatable {
        BEE   ("textures/entity/bee/bee.png"),
        ALLAY ("textures/entity/allay/allay.png"),
        BAT   ("textures/entity/bat.png");

        public final Identifier modTexture;
        public final Identifier mcTexture;

        WingVariant(String path) {
            this.modTexture = Identifier.fromNamespaceAndPath("flying-chests", path);
            this.mcTexture  = Identifier.withDefaultNamespace(path);
        }

        @NotNull
        @Override
        public String prefix() {
            return "flying-chests.wing_variant";
        }
    }

    // ---- Resolve methods ----

    public static Identifier resolveChestTexture() {
        return INSTANCE.chestUseResourcePack ? INSTANCE.chestVariant.mcTexture : INSTANCE.chestVariant.modTexture;
    }

    public static Identifier resolveBaseTexture(Identifier resourcePackOverride) {
        if (INSTANCE.baseUseResourcePack) {
            if (resourcePackOverride != null)
                return resourcePackOverride;
            return INSTANCE.baseVariant.mcTexture;
        }
        return INSTANCE.baseVariant.modTexture;
    }

    public static Identifier resolveWingsTexture() {
        return INSTANCE.wingsUseResourcePack ? INSTANCE.wingsVariant.mcTexture : INSTANCE.wingsVariant.modTexture;
    }

    public static void init() {}
}

