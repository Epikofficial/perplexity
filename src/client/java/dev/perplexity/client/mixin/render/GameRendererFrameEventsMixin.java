package dev.perplexity.client.mixin.render;

import dev.perplexity.client.event.PlayerEvents;
import dev.perplexity.client.event.RenderEvents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publishes exact frame boundaries from GameRenderer.render. */
@Mixin(GameRenderer.class)
public abstract class GameRendererFrameEventsMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "render", at = @At("HEAD"))
    private void perplexity$beforeFrame(DeltaTracker deltaTracker, boolean renderLevel,
                                        CallbackInfo ci) {
        RenderEvents.fireFrame(minecraft, deltaTracker, PlayerEvents.Phase.BEFORE);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void perplexity$afterFrame(DeltaTracker deltaTracker, boolean renderLevel,
                                       CallbackInfo ci) {
        RenderEvents.fireFrame(minecraft, deltaTracker, PlayerEvents.Phase.AFTER);
    }
}
