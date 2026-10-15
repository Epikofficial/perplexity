package dev.perplexity.client.mixin.player;

import dev.perplexity.client.impl.modules.player.NoPushModule;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

/** Removes only wind-charge knockback while preserving its sound, particles, and block effects. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerWindChargeMixin {
    @Redirect(
            method = "handleExplosion",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ClientboundExplodePacket;playerKnockback()Ljava/util/Optional;"))
    private Optional<Vec3> perplexity$filterWindChargeKnockback(ClientboundExplodePacket packet) {
        boolean windCharge = packet.explosionSound().equals(SoundEvents.WIND_CHARGE_BURST)
                || packet.explosionSound().equals(SoundEvents.BREEZE_WIND_CHARGE_BURST);
        if (windCharge && NoPushModule.blocksWindCharges()) {
            return Optional.empty();
        }
        return packet.playerKnockback();
    }
}
