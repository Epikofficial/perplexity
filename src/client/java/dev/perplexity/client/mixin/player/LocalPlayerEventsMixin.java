package dev.perplexity.client.mixin.player;

import dev.perplexity.client.event.PlayerEvents;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publishes local-player state without changing vanilla movement or input. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerEventsMixin {
    @Unique
    private Vec3 perplexity$tickStart = Vec3.ZERO;

    @Unique
    private Vec3 perplexity$moveStart = Vec3.ZERO;

    @Inject(method = "tick", at = @At("HEAD"))
    private void perplexity$beforeTick(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        perplexity$tickStart = player.position();
        PlayerEvents.fireTick(player, PlayerEvents.Phase.BEFORE, perplexity$tickStart);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void perplexity$afterTick(CallbackInfo ci) {
        PlayerEvents.fireTick((LocalPlayer) (Object) this, PlayerEvents.Phase.AFTER, perplexity$tickStart);
    }

    @Inject(method = "move", at = @At("HEAD"))
    private void perplexity$beforeMove(MoverType type, Vec3 requested, CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        perplexity$moveStart = player.position();
        PlayerEvents.fireMove(player, PlayerEvents.Phase.BEFORE, type, requested, perplexity$moveStart);
    }

    @Inject(method = "move", at = @At("TAIL"))
    private void perplexity$afterMove(MoverType type, Vec3 requested, CallbackInfo ci) {
        PlayerEvents.fireMove((LocalPlayer) (Object) this, PlayerEvents.Phase.AFTER,
            type, requested, perplexity$moveStart);
    }

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"))
    private void perplexity$onSwing(InteractionHand hand, CallbackInfo ci) {
        PlayerEvents.fireSwing((LocalPlayer) (Object) this, hand);
    }
}
