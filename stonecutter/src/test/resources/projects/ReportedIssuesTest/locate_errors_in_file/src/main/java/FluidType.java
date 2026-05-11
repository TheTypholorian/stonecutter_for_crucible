// Sourced from https://codeberg.org/Infinitychances/fluidlib for testing
// Copyright (c) 2026 Infinitychances
package com.infinitychances.fluidlib.util.data;

import com.infinitychances.fluidlib.util.fluid.FluidUtils;
import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function5;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
//? if >=26.1
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class FluidType {

    private final Identifier fluidID;
    private final Function</*? if >= 26.1 {*/ ServerLevel /*?} elif 1.21.1 {*/ /*Level *//*?} */, Boolean> canConvertToSource;
    private final int slopeFindDistance;
    private final int decreasePerBlock;
    private final int viscosity; //tickDelay
    private final float explosionResistance;
    private final boolean canSwim;
    private final float walkingSlowDown;
    private final float swimmingSlowDown;
    private final boolean canUseDolphinsGrace;
    private final float dolphinSlowDown;
    private final boolean canBottle;
    private final Holder<Potion> potionContent;
    private final Function3<Level, ItemStack, Player, /*? if >= 26.1 {*/ InteractionResult /*?}
 elif 1.21.1 {*/ /*InteractionResultHolder<ItemStack> *//*?} */> bottleInteraction;
    //? if >=26.1
    private final Consumer4<Level, BlockPos, Entity, InsideBlockEffectApplier> entityInsideEffect;
    private final boolean canBurn;
    private final float burnAmount;
    private final boolean canHydrate;

    public FluidType(Properties properties) {
        this.fluidID = properties.fluidID;
        this.canConvertToSource = properties.canConvertToSource;
        this.slopeFindDistance = properties.slopeFindDistance;
        this.decreasePerBlock = properties.decreasePerBlock;
        this.viscosity = properties.viscosity;
        this.explosionResistance = properties.explosionResistance;
        this.canSwim = properties.canSwim;
        this.walkingSlowDown = properties.walkingSlowDown;
        this.swimmingSlowDown = properties.swimmingSlowDown;
        this.canUseDolphinsGrace = properties.canUseDolphinsGrace;
        this.dolphinSlowDown = properties.dolphinSlowDown;
        this.canBottle = properties.canBottle;
        this.potionContent = properties.potionContent;
        this.bottleInteraction = properties.bottleInteraction;
        //? if >=26.1
        this.entityInsideEffect = properties.entityInsideEffect;
        this.canBurn = properties.canBurn;
        this.burnAmount = properties.burnAmount;
        this.canHydrate = properties.canHydrate;
    }


    public static class Properties {

        private final Identifier fluidID;
        private Function</*? if >= 26.1 {*/ ServerLevel /*?} elif 1.21.1 {*/ /*Level *//*?} */, Boolean> canConvertToSource = (level) -> false;
        private int slopeFindDistance = 4;
        private int decreasePerBlock = 1;
        private int viscosity = 5; //tickDelay
        private float explosionResistance = 100f;
        private boolean canSwim = false;
        private float walkingSlowDown = 0.8f;
        private float swimmingSlowDown = 0.9f;
        private boolean canUseDolphinsGrace = false;
        private float dolphinSlowDown = 0.96f;
        private boolean canBottle = false;
        private Holder<Potion> potionContent = Potions.WATER;
        private Function3<Level, ItemStack, Player, /*? if >= 26.1 {*/ InteractionResult /*?}
 elif 1.21.1 {*/ /*InteractionResultHolder<ItemStack> *//*?} */> bottleInteraction = ((level, emptyBottle, player) -> FluidUtils.getBottleResult(level, emptyBottle, player, PotionContents.createItemStack(Items.POTION, potionContent)));
        //? if >=26.1
        @Nullable
        private Consumer4<Level, BlockPos, Entity, InsideBlockEffectApplier> entityInsideEffect = null;
        private boolean canBurn = false;
        private float burnAmount = 4.0f;
        private boolean canHydrate = false;

        public Properties(Identifier fluidID) {
            this.fluidID = fluidID;
        }

        public Properties canConvertToSource(Function</*? if >= 26.1 {*/ ServerLevel /*?} elif 1.21.1 {*/ /*Level *//*?} */, Boolean> rule) {
            this.canConvertToSource = rule;
            return this;
        }

        public Properties slopeFindDistance(int rule) {
            this.slopeFindDistance = rule;
            return this;
        }

        public Properties decreasePerBlock(int rule) {
            this.decreasePerBlock = rule;
            return this;
        }

        public Properties viscosity(int rule) {
            this.viscosity = rule;
            return this;
        }

        public Properties explosionResistance(float rule) {
            this.explosionResistance = rule;
            return this;
        }

        public Properties swimmable() {
            this.canSwim = true;
            return this;
        }

        public Properties walkingSlowDown(float rule) {
            this.walkingSlowDown = rule;
            return this;
        }

        public Properties swimmingSlowDown(float rule) {
            this.swimmingSlowDown = rule;
            return this;
        }

        public Properties canUseDolphinsGrace() {
            this.canUseDolphinsGrace = true;
            return this;
        }

        public Properties dolphinSlowDown(float rule) {
            this.canUseDolphinsGrace = true;
            this.dolphinSlowDown = rule;
            return this;
        }

        public Properties bottleable(Holder<Potion> rule) {
            this.canBottle = true;
            this.potionContent = rule;
            return this;
        }

        public Properties customBottleInteraction(Function3<Level, ItemStack, Player, /*? if >= 26.1 {*/ InteractionResult /*?}
 elif 1.21.1 {*/ /*InteractionResultHolder<ItemStack> *//*?} */> rule) {
            this.bottleInteraction = rule;
            return this;
        }

        public Properties burnable() {
            this.canBurn = true;
            return this;
        }

        public Properties burnAmount(float rule) {
            this.burnAmount = rule;
            return this;
        }


    }
}
