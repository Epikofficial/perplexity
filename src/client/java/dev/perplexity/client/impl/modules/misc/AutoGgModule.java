package dev.perplexity.client.impl.modules.misc;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.DropdownSetting;
import dev.perplexity.client.module.setting.NumberSetting;
import dev.perplexity.client.utils.Obf;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Sends one friendly message after a common victory notification is received. */
public final class AutoGgModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();
    private static AutoGgModule instance;

    private final DropdownSetting message = add(new DropdownSetting("Message", "gg",
            "gg", "GG", "Good game!"));
    private final NumberSetting delaySeconds = add(new NumberSetting("Delay Seconds",
            1.0, 0.0, 5.0, 0.5));

    private long sendAt = -1L;
    private long lastTriggerAt;

    public AutoGgModule() {
        super(Category.MISC);
        instance = this;
    }

    public static void inspect(Component component) {
        if (instance == null || !instance.isEnabled() || component == null) {
            return;
        }
        String text = component.getString().toLowerCase(Locale.ROOT);
        boolean victory = text.contains("victory") || text.contains("you won")
                || text.contains("you win") || text.contains("winner: you");
        if (!victory) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - instance.lastTriggerAt < 10_000L) {
            return;
        }
        instance.lastTriggerAt = now;
        instance.sendAt = now + Math.round(instance.delaySeconds.get() * 1000.0);
    }

    @Override
    protected void onDisable() {
        sendAt = -1L;
    }

    @Override
    protected void onTick() {
        if (sendAt < 0L || System.currentTimeMillis() < sendAt) {
            return;
        }
        sendAt = -1L;
        if (mc.player != null && mc.getConnection() != null) {
            mc.getConnection().sendChat(message.get());
        }
    }

    @Override
    public String getDisplayName() {
        return Obf.s("H08Dfm1FLA==");
    }
}
