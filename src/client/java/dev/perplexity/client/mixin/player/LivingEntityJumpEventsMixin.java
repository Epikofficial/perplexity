package dev.perplexity.client.mixin.player;

import dev.perplexity.client.event.PlayerEvents;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Narrows the shared living-entity jump path to the local player. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityJumpEventsMixin {
    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void perplexity$beforeJump(CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player) {
            PlayerEvents.fireJump(player, PlayerEvents.Phase.BEFORE);
        }
    }

    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void perplexity$afterJump(CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player) {
            PlayerEvents.fireJump(player, PlayerEvents.Phase.AFTER);
        }
    }
}
