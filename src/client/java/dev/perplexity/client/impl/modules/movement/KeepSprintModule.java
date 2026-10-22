package dev.perplexity.client.impl.modules.movement;
import dev.perplexity.client.utils.Obf;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;

import net.minecraft.client.Minecraft;

/**
 * Sprints whenever movement input is held, in any direction. Backs off while sneaking,
 * starving, or recharging an attack, mirroring vanilla's own sprint rules: forcing sprint
 * through the cooldown moves faster than prediction allows and flags speed checks.
 */
public final class KeepSprintModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    public KeepSprintModule() {
        super(Category.PLAYER);
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

    @Override
    protected void onTick() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.options.keyShift.isDown() || mc.player.isSprinting()) {
            return;
        }
        if (mc.player.getFoodData().getFoodLevel() <= 6) {
            return;
        }
        if (mc.player.getAttackStrengthScale(0.0F) < 1.0F) {
            return;
        }
        if (mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()) {
            mc.player.setSprinting(true);
        }
    }
    @Override
    public String getDisplayName() {
        return Obf.s("H08Dfm1RG0tlO1s=");
    }

}
