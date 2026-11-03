package dev.perplexity.client.command;

import com.mojang.blaze3d.platform.InputConstants;

import dev.perplexity.client.config.ConfigManager;
import dev.perplexity.client.impl.modules.ui.MenuKeyModule;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Small client-only chat command layer. Commands are never sent to the server. */
public final class ClientCommandHandler {
    private static final String PREFIX = "*bind";
    private static final Map<String, String> KEY_ALIASES = aliases();

    private ClientCommandHandler() {
    }

    public static void install() {
        ClientSendMessageEvents.ALLOW_CHAT.register(ClientCommandHandler::handleChat);
    }

    private static boolean handleChat(String message) {
        String trimmed = message.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
            return true;
        }

        String[] parts = trimmed.split("\\s+");
        if (!parts[0].equalsIgnoreCase(PREFIX)) {
            return true;
        }
        if (parts.length != 2) {
            feedback("Usage: *bind <key>  (example: *bind rshift)");
            return false;
        }

        Integer keyCode = parseKey(parts[1]);
        if (keyCode == null) {
            feedback("Unknown key '" + parts[1] + "'. Try B, RSHIFT, F4, SPACE, or DELETE.");
            return false;
        }

        MenuKeyModule.setOpenKey(keyCode);
        ConfigManager.flush();
        feedback("ClickGUI bound to " + MenuKeyModule.openKeyName() + ".");
        return false;
    }

    private static Integer parseKey(String input) {
        String normalized = input.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "");
        String minecraftName = KEY_ALIASES.getOrDefault(normalized, normalized);
        try {
            InputConstants.Key key = InputConstants.getKey("key.keyboard." + minecraftName);
            return key == InputConstants.UNKNOWN ? null : key.getValue();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void feedback(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("[Perplexity] " + text), false);
        }
    }

    private static Map<String, String> aliases() {
        Map<String, String> keys = new HashMap<>();
        keys.put("rshift", "right.shift");
        keys.put("lshift", "left.shift");
        keys.put("rctrl", "right.control");
        keys.put("rightctrl", "right.control");
        keys.put("lctrl", "left.control");
        keys.put("leftctrl", "left.control");
        keys.put("ralt", "right.alt");
        keys.put("lalt", "left.alt");
        keys.put("enter", "enter");
        keys.put("return", "enter");
        keys.put("esc", "escape");
        keys.put("pgup", "page.up");
        keys.put("pgdn", "page.down");
        keys.put("caps", "caps.lock");
        keys.put("printscreen", "print.screen");
        keys.put("scrolllock", "scroll.lock");
        keys.put("backspace", "backspace");
        keys.put("spacebar", "space");
        return Map.copyOf(keys);
    }
}
