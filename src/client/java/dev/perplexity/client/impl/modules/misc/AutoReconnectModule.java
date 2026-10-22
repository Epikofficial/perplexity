package dev.perplexity.client.impl.modules.misc;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.NumberSetting;
import dev.perplexity.client.utils.Obf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/** Rejoins the last multiplayer server after a configurable disconnect-screen delay. */
public final class AutoReconnectModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private final NumberSetting delaySeconds = add(new NumberSetting("Delay Seconds",
            5.0, 1.0, 30.0, 1.0));

    private ServerData lastServer;
    private long disconnectedAt = -1L;

    public AutoReconnectModule() {
        super(Category.MISC);
    }

    @Override
    protected void onEnable() {
        disconnectedAt = -1L;
        rememberCurrentServer();
    }

    @Override
    protected void onDisable() {
        disconnectedAt = -1L;
    }

    @Override
    protected void onTick() {
        if (mc.level != null) {
            rememberCurrentServer();
            disconnectedAt = -1L;
            return;
        }
        if (!(mc.screen instanceof DisconnectedScreen) || lastServer == null) {
            disconnectedAt = -1L;
            return;
        }

        long now = System.currentTimeMillis();
        if (disconnectedAt < 0L) {
            disconnectedAt = now;
            return;
        }
        if (now - disconnectedAt < Math.round(delaySeconds.get() * 1000.0)) {
            return;
        }

        disconnectedAt = -1L;
        JoinMultiplayerScreen parent = new JoinMultiplayerScreen(new TitleScreen());
        ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(lastServer.ip),
                lastServer, false, null);
    }

    private void rememberCurrentServer() {
        ServerData current = mc.getCurrentServer();
        if (current != null && !current.isLan()) {
            lastServer = current;
        }
    }

    @Override
    public String getDisplayName() {
        return Obf.s("H08Dfm1QDlpjO0EUeQk=");
    }
}
