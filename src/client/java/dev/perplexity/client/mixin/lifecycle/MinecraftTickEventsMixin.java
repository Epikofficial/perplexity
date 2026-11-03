package dev.perplexity.client.mixin.lifecycle;

import dev.perplexity.client.event.ClientEvents;
import dev.perplexity.client.event.PlayerEvents;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publishes one before/after event around Minecraft's real client tick. */
@Mixin(Minecraft.class)
public abstract class MinecraftTickEventsMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void perplexity$beforeClientTick(CallbackInfo ci) {
        ClientEvents.fireTick((Minecraft) (Object) this, PlayerEvents.Phase.BEFORE);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void perplexity$afterClientTick(CallbackInfo ci) {
        ClientEvents.fireTick((Minecraft) (Object) this, PlayerEvents.Phase.AFTER);
    }
}
