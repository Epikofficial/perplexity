package dev.perplexity.client.impl.modules.misc;
import dev.perplexity.client.utils.Obf;

import dev.perplexity.client.event.PlayerEvents;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.NumberSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/**
 * Throw-rate control for XP bottles. Nothing is automatic: hold a bottle in your main hand
 * and hold use, and throws go out at exactly Throws Per Second. Vanilla's own repeat-use is
 * suppressed while throwing (first press still throws instantly) so the configured rate is
 * the actual rate with no gaps or doubles.
 */
public final class BottleHelperModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();
    private static BottleHelperModule instance;

    private final NumberSetting throwsPerSecond = add(new NumberSetting("Throws Per Second",
            8.0, 1.0, 20.0, 1.0));

    private int ticksSinceThrow;
    private boolean wasActive;

    public BottleHelperModule() {
        super(Category.MISC);
        instance = this;
    }

    @Override
    protected void onPlayerTick(PlayerEvents.TickEvent event) {
        if (event.phase() != PlayerEvents.Phase.BEFORE) {
            return;
        }
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            wasActive = false;
            return;
        }
        if (!throwing()) {
            wasActive = false;
            return;
        }
        // Fresh press fires at once instead of waiting out a full interval.
        if (!wasActive) {
            ticksSinceThrow = interval();
        }
        wasActive = true;
        if (++ticksSinceThrow < interval()) {
            return;
        }
        ticksSinceThrow = 0;
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /** True while vanilla's repeat-use must stay out of the way. */
    public static boolean suppressVanillaUse() {
        if (instance == null || !instance.isEnabled()) {
            return false;
        }
        return mc.player != null && mc.level != null && throwing();
    }

    private static boolean throwing() {
        return mc.screen == null
                && mc.player.getMainHandItem().is(Items.EXPERIENCE_BOTTLE)
                && mc.options.keyUse.isDown();
    }

    private int interval() {
        return Math.max(1, Math.round(20.0F / (float) throwsPerSecond.get()));
    }
    @Override
    public String getDisplayName() {
        return Obf.s("GFsEZW1aOw==");
    }

}
