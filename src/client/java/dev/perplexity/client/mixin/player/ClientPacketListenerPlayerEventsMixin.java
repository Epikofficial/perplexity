package dev.perplexity.client.mixin.player;

import dev.perplexity.client.event.PlayerEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reports authoritative server corrections and local damage after vanilla applies them. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerPlayerEventsMixin {
    @Unique
    private Vec3 perplexity$positionBeforeCorrection = Vec3.ZERO;
    @Unique
    private float perplexity$yawBeforeCorrection;
    @Unique
    private float perplexity$pitchBeforeCorrection;
    @Unique
    private float perplexity$healthBeforeDamage;

    @Inject(method = {"handleMovePlayer", "handleRotatePlayer"}, at = @At("HEAD"))
    private void perplexity$beforeCorrection(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            perplexity$positionBeforeCorrection = player.position();
            perplexity$yawBeforeCorrection = player.getYRot();
            perplexity$pitchBeforeCorrection = player.getXRot();
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void perplexity$afterPositionCorrection(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        perplexity$publishCorrection();
    }

    @Inject(method = "handleRotatePlayer", at = @At("TAIL"))
    private void perplexity$afterRotationCorrection(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
        perplexity$publishCorrection();
    }

    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void perplexity$beforeDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && packet.entityId() == player.getId()) {
            perplexity$healthBeforeDamage = player.getHealth();
        }
    }

    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void perplexity$afterDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.level() != null && packet.entityId() == player.getId()) {
            PlayerEvents.fireDamage(player, packet.getSource(player.level()), perplexity$healthBeforeDamage);
        }
    }

    @Unique
    private void perplexity$publishCorrection() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            PlayerEvents.fireCorrection(player, perplexity$positionBeforeCorrection,
                perplexity$yawBeforeCorrection, perplexity$pitchBeforeCorrection);
        }
    }
}
