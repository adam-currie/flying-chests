package com.fjjy.config;

import me.fzzyhmstrs.fzzy_config.api.ConfigApiJava;
import me.fzzyhmstrs.fzzy_config.api.RegisterType;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.util.EnumTranslatable;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.NotNull;

public class FlyingChestTextureConfig extends Config {

    public static final FlyingChestTextureConfig INSTANCE =
        ConfigApiJava.registerAndLoadConfig(FlyingChestTextureConfig::new, RegisterType.CLIENT);

    public FlyingChestTextureConfig() {
        super(Identifier.fromNamespaceAndPath("flying-chests", "texture_config"));
    }

    public boolean useResourcePack = false;
    public Variant variant = Variant.BASIC;

    public enum Variant implements EnumTranslatable {
        // modTexture = flying-chests namespace (not overrideable by resource packs targeting minecraft:)
        // mcTexture  = minecraft namespace (follows whatever the resource pack does to chest textures)
        BASIC     ("textures/entity/chest/normal.png"),
        CHRISTMAS ("textures/entity/chest/christmas.png"),
        ENDER     ("textures/entity/chest/ender.png"),
        TRAPPED   ("textures/entity/chest/trapped.png");

        public final Identifier modTexture;
        public final Identifier mcTexture;

        Variant(String path) {
            this.modTexture = Identifier.fromNamespaceAndPath("flying-chests", path);
            this.mcTexture  = Identifier.withDefaultNamespace(path);
        }

        @NotNull
        @Override
        public String prefix() {
            return "flying-chests.variant";
        }
    }

    /**
     * Resolves which texture identifier to use.
     *
     * useResourcePack = false (default): uses flying-chests-namespaced copies of the vanilla textures.
     *   Resource packs that override minecraft:textures/entity/chest/*.png will NOT affect the flying chest.
     *
     * useResourcePack = true: uses minecraft-namespaced paths, so resource packs that change vanilla
     *   chest textures will also change the flying chest. The base station additionally checks for a
     *   flying-chests:textures/block/flying_chest_base.png resource pack override (passed as resourcePackOverride).
     */
    public static Identifier resolveTexture(Identifier resourcePackOverride) {
        if (INSTANCE.useResourcePack) {
            if (resourcePackOverride != null)
                return resourcePackOverride;
            return INSTANCE.variant.mcTexture;
        }
        return INSTANCE.variant.modTexture;
    }

    public static void init() {}
}
