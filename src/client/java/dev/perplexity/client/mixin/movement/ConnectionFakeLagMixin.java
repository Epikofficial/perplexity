package dev.perplexity.client.mixin.movement;

import dev.perplexity.client.impl.modules.movement.FakeLagModule;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionFakeLagMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void perplexity$delayMovement(Packet<?> packet, CallbackInfo callbackInfo) {
        if (FakeLagModule.hold((Connection) (Object) this, packet)) {
            callbackInfo.cancel();
        }
    }
}
