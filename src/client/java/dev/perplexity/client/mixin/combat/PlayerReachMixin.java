package dev.perplexity.client.mixin.combat;

import dev.perplexity.client.impl.modules.combat.ReachModule;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends only the local player's entity targeting range. */
@Mixin(Player.class)
public abstract class PlayerReachMixin {
    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void perplexity$extendEntityRange(CallbackInfoReturnable<Double> cir) {
        if ((Object) this == Minecraft.getInstance().player) {
            cir.setReturnValue(ReachModule.targetingRange(cir.getReturnValue()));
        }
    }
}
