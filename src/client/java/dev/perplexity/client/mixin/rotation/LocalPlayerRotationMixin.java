package dev.perplexity.client.mixin.rotation;

import dev.perplexity.client.utils.rotation.RotationManager;
import dev.perplexity.client.impl.modules.player.SpinBotModule;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sends the managed rotation instead of the camera rotation in movement packets. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerRotationMixin {
    @ModifyExpressionValue(
            method = {"sendPosition", "tick"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float perplexity$packetYaw(float original) {
        return SpinBotModule.packetYaw(RotationManager.packetYaw(original));
    }

    @ModifyExpressionValue(
            method = {"sendPosition", "tick"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float perplexity$packetPitch(float original) {
        return RotationManager.packetPitch(original);
    }

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void perplexity$recordPacketRotation(CallbackInfo callbackInfo) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        RotationManager.recordMovementPacket(SpinBotModule.packetYaw(player.getYRot()), player.getXRot());
    }
}
