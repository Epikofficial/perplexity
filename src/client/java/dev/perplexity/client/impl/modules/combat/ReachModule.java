package dev.perplexity.client.impl.modules.combat;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.NumberSetting;
import dev.perplexity.client.utils.Obf;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.concurrent.ThreadLocalRandom;

/** Extends client targeting range and rolls extended attacks against a configurable chance. */
public final class ReachModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final double VANILLA_REACH = 3.0;
    private static ReachModule instance;

    private final NumberSetting distance = add(new NumberSetting("Distance",
            4.0, VANILLA_REACH, 6.0, 0.1));
    private final NumberSetting chance = add(new NumberSetting("Reach Chance",
            100.0, 0.0, 100.0, 1.0));

    public ReachModule() {
        super(Category.COMBAT);
        instance = this;
    }

    public static double targetingRange(double vanilla) {
        if (instance == null || !instance.isEnabled()) {
            return vanilla;
        }
        return Math.max(vanilla, instance.distance.get());
    }

    /** Returns false only when an attempted hit needs extended reach and loses its chance roll. */
    public static boolean allowAttack(Entity target) {
        if (instance == null || !instance.isEnabled() || mc.player == null || target == null) {
            return true;
        }
        double distanceToBox = target.getBoundingBox().distanceToSqr(mc.player.getEyePosition());
        if (distanceToBox <= VANILLA_REACH * VANILLA_REACH) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble(100.0) < instance.chance.get();
    }

    @Override
    public String getDisplayName() {
        return Obf.s("DF8WciU=");
    }
}
