package dev.perplexity.client.mixin.player;

import dev.perplexity.client.impl.modules.player.NoPushModule;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies No Push only to the local player, leaving world entity physics untouched. */
@Mixin(Entity.class)
public abstract class LocalPlayerPushMixin {
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void perplexity$blockEntityPush(Entity entity, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && NoPushModule.blocksEntityPush()) {
            ci.cancel();
        }
    }

    @Inject(method = "isPushedByFluid", at = @At("HEAD"), cancellable = true)
    private void perplexity$blockLiquidPush(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == Minecraft.getInstance().player && NoPushModule.blocksLiquidPush()) {
            cir.setReturnValue(false);
        }
    }
}
