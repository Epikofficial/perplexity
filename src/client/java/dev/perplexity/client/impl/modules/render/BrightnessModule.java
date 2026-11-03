package dev.perplexity.client.impl.modules.render;
import dev.perplexity.client.utils.Obf;

import dev.perplexity.client.event.PlayerEvents;
import dev.perplexity.client.module.RenderModule;
import dev.perplexity.client.utils.NightVision;

/**
 * Full brightness via Night Vision. The gamma slider caps at 1.0 (the option validates every
 * set), so pushing it higher is a no-op — an invisible, iconless effect is what actually
 * brightens caves.
 */
public final class BrightnessModule extends RenderModule {
    private final NightVision nightVision = new NightVision();

    public BrightnessModule() {
        super();
    }

    @Override
    protected void onEnable() {
        nightVision.enable();
    }

    @Override
    protected void onDisable() {
        nightVision.disable();
    }

    @Override
    protected void onPlayerTick(PlayerEvents.TickEvent event) {
        if (event.phase() != PlayerEvents.Phase.AFTER) {
            return;
        }
        nightVision.tick();
    }
    @Override
    public String getDisplayName() {
        return Obf.s("GE8bfW1AGVBrPVs=");
    }

}
