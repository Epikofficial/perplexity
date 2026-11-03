package dev.perplexity.client.impl.modules.movement;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.NumberSetting;
import dev.perplexity.client.utils.Obf;

/** Speeds up or slows down the local client tick clock. */
public final class TimerModule extends Module {
    private static volatile float multiplier = 1.0F;

    private final NumberSetting speed = add(new NumberSetting("Speed",
            1.0, 0.1, 10.0, 0.1));

    public TimerModule() {
        super(Category.MOVEMENT);
    }

    /** Used by the delta-tracker mixin without looking up modules every frame. */
    public static float multiplier() {
        return multiplier;
    }

    @Override
    protected void onEnable() {
        updateMultiplier();
    }

    @Override
    protected void onTick() {
        updateMultiplier();
    }

    @Override
    protected void onDisable() {
        multiplier = 1.0F;
    }

    private void updateMultiplier() {
        multiplier = (float) speed.get();
    }

    @Override
    public String getDisplayName() {
        return Obf.s("ClMadD8=");
    }
}
