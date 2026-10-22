package dev.perplexity.client.mixin.misc;

import dev.perplexity.client.impl.modules.misc.AutoGgModule;

import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds server-authored chat notifications to Auto GG without altering displayed chat. */
@Mixin(ChatListener.class)
public abstract class ChatListenerAutoGgMixin {
    @Inject(method = "handleSystemMessage", at = @At("HEAD"))
    private void perplexity$inspectSystemMessage(Component message, boolean overlay, CallbackInfo ci) {
        if (!overlay) {
            AutoGgModule.inspect(message);
        }
    }

    @Inject(method = "handleDisguisedChatMessage", at = @At("HEAD"))
    private void perplexity$inspectDisguisedMessage(Component message, ChatType.Bound bound,
            CallbackInfo ci) {
        AutoGgModule.inspect(message);
    }
}
