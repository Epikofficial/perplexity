package dev.perplexity.client.impl.modules.combat;
import dev.perplexity.client.utils.Obf;

import dev.perplexity.client.mixin.combat.MinecraftAttackInvoker;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.ModeSetting;
import dev.perplexity.client.module.setting.RangeSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Attacks whatever the crosshair rests on. Ticks detection runs once per tick at tick start
 * so hits land before the tick's movement packets, the way vanilla clicks do; Frames
 * detection spots the target every render frame but holds the swing for the next pre-tick,
 * since a frame-time hit can never precede movement and Grim's Post check flags exactly
 * that. OS Clicks goes through the same path as a real left click (cooldown, swing, miss
 * handling); with it off the hit is sent directly.
 */
public final class AttackHelperModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();
    private static AttackHelperModule instance;

    private final ModeSetting detection = add(new ModeSetting("Detection", "Ticks",
            "Ticks", "Frames"));
    private final BooleanSetting osClicks = add(new BooleanSetting("OS Clicks",
            true));
    private final BooleanSetting swordOnly = add(new BooleanSetting("Sword Only",
            false));
    private final BooleanSetting autoCooldown = add(new BooleanSetting("Auto Cooldown",
            true));
    private final RangeSetting hitDelay = add(new RangeSetting("Hit Delay",
            0.5, 2.0, 0.0, 5.0, 0.1));

    private long nextHitAtMs;
    private boolean pendingFrameAttack;

    public AttackHelperModule() {
        super(Category.COMBAT);
        instance = this;
        hitDelay.setVisibleWhen(() -> !autoCooldown.get());
    }

    @Override
    protected void onDisable() {
        nextHitAtMs = 0L;
    }

    /** Per-frame entry point: only arms a swing, the next pre-tick fires it. */
    public static void onFrame() {
        if (instance != null && instance.isEnabled() && instance.detection.is("Frames")) {
            instance.pendingFrameAttack = true;
        }
    }

    /** Pre-tick entry point, driving Ticks detection before the tick's movement packets. */
    public static void preTick() {
        if (instance == null || !instance.isEnabled()) {
            return;
        }
        if (instance.detection.is("Ticks") || instance.pendingFrameAttack) {
            instance.pendingFrameAttack = false;
            instance.tryAttack();
        }
    }

    private void tryAttack() {
        if (!canRun()) {
            return;
        }
        if (swordOnly.get() && !mc.player.getMainHandItem().is(ItemTags.SWORDS)) {
            return;
        }
        if (!(mc.hitResult instanceof EntityHitResult entityHit)) {
            return;
        }
        if (!(entityHit.getEntity() instanceof LivingEntity target)
                || target == mc.player || !target.isAlive()) {
            return;
        }
        if (autoCooldown.get() && mc.player.getAttackStrengthScale(0.0F) < 0.9F) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextHitAtMs) {
            return;
        }
        if (osClicks.get()) {
            ((MinecraftAttackInvoker) mc).perplexity$invokeStartAttack();
        } else {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        nextHitAtMs = autoCooldown.get()
                ? now
                : now + (long) (hitDelay.random() * 1000.0);
    }

    private static boolean canRun() {
        return mc.player != null && mc.level != null && mc.gameMode != null
                && mc.screen == null && mc.isWindowActive()
                && !mc.player.isDeadOrDying() && !mc.player.isSpectator();
    }
    @Override
    public String getDisplayName() {
        return Obf.s("CkgedipnGVtjIQ==");
    }

}
