package dev.perplexity.client.mixin.combat;

import dev.perplexity.client.impl.modules.combat.CriticalsModule;
import dev.perplexity.client.impl.modules.combat.ReachModule;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Places the critical movement sequence immediately before every shared attack path. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeCriticalsMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void perplexity$criticalBeforeAttack(Player player, Entity target, CallbackInfo ci) {
        if (!ReachModule.allowAttack(target)) {
            ci.cancel();
            return;
        }
        CriticalsModule.beforeAttack(player, target);
    }
}
