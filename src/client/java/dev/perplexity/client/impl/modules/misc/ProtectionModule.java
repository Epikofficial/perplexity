package dev.perplexity.client.impl.modules.misc;

import dev.perplexity.client.event.PlayerEvents;
import dev.perplexity.client.utils.Obf;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.NumberSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Identity protection, all client-side: other players show up under random names in the tab
 * list and over their heads, re-rolled every {@code Name Interval} seconds, and optionally
 * your own skin is swapped for a random default one on the same timer.
 *
 * <p>Nothing here leaves the client: the server still knows exactly who everyone is, so this
 * hides identities from <em>your</em> screen (streaming, screenshots) rather than from the
 * server or other players.</p>
 */
public final class ProtectionModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();
    private static ProtectionModule instance;

    private final NumberSetting nameInterval = add(new NumberSetting("Name Interval",
            30.0, 5.0, 300.0, 5.0));
    private final BooleanSetting randomizeSkin = add(new BooleanSetting("Randomize Skin",
            false));
    private final BooleanSetting hideSelf = add(new BooleanSetting("Hide Self",
            true));

    private final Map<UUID, String> fakeNames = new HashMap<>();
    private long lastShuffle;
    private UUID skinSeed = UUID.randomUUID();

    public ProtectionModule() {
        super(Category.MISC);
        instance = this;
    }

    @Override
    protected void onEnable() {
        // Zero forces a reshuffle on the first tick so names change the moment it turns on.
        lastShuffle = 0L;
    }

    @Override
    protected void onDisable() {
        fakeNames.clear();
    }

    @Override
    protected void onPlayerTick(PlayerEvents.TickEvent event) {
        if (event.phase() != PlayerEvents.Phase.AFTER) {
            return;
        }
        if (mc.level == null) {
            return;
        }
        long intervalMs = (long) (nameInterval.get() * 1000.0);
        long now = System.currentTimeMillis();
        if (now - lastShuffle >= intervalMs) {
            lastShuffle = now;
            fakeNames.clear();
            skinSeed = UUID.randomUUID();
        }
    }

    /** Fake tab-list name for an entry, or null to leave it alone. */
    public static Component tabNameFor(PlayerInfo entry) {
        if (entry == null || entry.getProfile() == null) {
            return null;
        }
        ProtectionModule self = active();
        if (self == null) {
            return null;
        }
        UUID id = entry.getProfile().id();
        if (isSelf(id) && !self.hideSelf.get()) {
            return null;
        }
        Component base = entry.getTabListDisplayName();
        Style style = base != null ? base.getStyle() : Style.EMPTY;
        return Component.literal(self.fakeName(entry.getProfile().id())).withStyle(style);
    }

    /** Fake overhead nametag for a player entity, or null to leave it alone. */
    public static Component nametagFor(Entity entity) {
        if (!(entity instanceof AbstractClientPlayer)) {
            return null;
        }
        ProtectionModule self = active();
        if (self == null) {
            return null;
        }
        if (isSelf(entity.getUUID()) && !self.hideSelf.get()) {
            return null;
        }
        Style style = entity.getDisplayName().getStyle();
        return Component.literal(self.fakeName(entity.getUUID())).withStyle(style);
    }

    /** Random default skin for the local player while the option is on, else null. */
    public static PlayerSkin skinFor(AbstractClientPlayer player) {
        ProtectionModule self = active();
        if (self == null || !self.randomizeSkin.get()) {
            return null;
        }
        if (mc.player == null || player != mc.player) {
            return null;
        }
        return DefaultPlayerSkin.get(self.skinSeed);
    }

    private static ProtectionModule active() {
        return instance != null && instance.isEnabled() ? instance : null;
    }

    private static boolean isSelf(UUID id) {
        return id != null && mc.player != null && mc.player.getUUID().equals(id);
    }

    private String fakeName(UUID id) {
        String existing = fakeNames.get(id);
        if (existing != null) {
            return existing;
        }
        String candidate;
        do {
            candidate = "Player-" + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        } while (fakeNames.containsValue(candidate));
        fakeNames.put(id, candidate);
        return candidate;
    }
    @Override
    public String getDisplayName() {
        return Obf.s("DkgYZShhHw==");
    }

}
