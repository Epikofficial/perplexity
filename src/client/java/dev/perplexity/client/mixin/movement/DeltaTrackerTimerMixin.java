package dev.perplexity.client.mixin.movement;

import dev.perplexity.client.impl.modules.movement.TimerModule;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.DeltaTracker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Applies the Timer multiplier only to game ticks, leaving UI time untouched. */
@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerTimerMixin {
    @ModifyExpressionValue(
            method = "advanceGameTime",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/floats/FloatUnaryOperator;apply(F)F"
            )
    )
    private float perplexity$scaleMillisecondsPerTick(float millisecondsPerTick) {
        return millisecondsPerTick / TimerModule.multiplier();
    }
}
