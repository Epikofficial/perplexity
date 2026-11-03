package dev.perplexity.client.impl.modules.combat;
import dev.perplexity.client.utils.Obf;

import dev.perplexity.client.event.PlayerEvents;
import dev.perplexity.client.mixin.combat.MinecraftAttackInvoker;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.DropdownSetting;
import dev.perplexity.client.module.setting.RangeSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Clicks for you on a humanized schedule: every click re-rolls the rate inside CPS, so
 * consecutive intervals never match exactly. Left goes through the real attack path, right
 * through the real use path.
 *
 * <p>All clicks go out at the start of the tick, before the tick's movement packets.
 * Grim's Post check flags action packets that arrive after movement, which is exactly
 * where end-of-tick clicks land; pre-tick clicks arrive the way vanilla's own clicks do.
 * Pre-tick also keeps swings clear of Karhu's Scaffold (B) swing window.</p>
 *
 * <p>Karhu mode additionally irregularizes the intervals: gaussian jitter, rare outlier
 * pauses and a hard floor keep Karhu's AutoClicker (D) statistics (deviation, kurtosis,
 * outlier count) out of flag range, where uniform re-rolls would look mechanical after a
 * couple thousand swings. It also validates the tick-stale crosshair before left-clicking,
 * so edge-range hits that moved out of the box are skipped instead of swinging at where
 * the target was.</p>
 */
public final class ClickHelperModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private final DropdownSetting mode = add(new DropdownSetting("Mode", "Normal",
            "Normal", "Karhu"));
    private final RangeSetting cps = add(new RangeSetting("CPS",
            8.0, 12.0, 1.0, 20.0, 0.5));
    private final BooleanSetting leftClick = add(new BooleanSetting("Left Click",
            true));
    private final BooleanSetting rightClick = add(new BooleanSetting("Right Click",
            false));

    private int ticksSinceClick;

    public ClickHelperModule() {
        super(Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        ticksSinceClick = Integer.MAX_VALUE / 2;
    }

    /** Drives every click from the exact start of LocalPlayer.tick. */
    @Override
    protected void onPlayerTick(PlayerEvents.TickEvent event) {
        if (event.phase() != PlayerEvents.Phase.BEFORE) {
            return;
        }
        doClicks(mode.is("Karhu"));
    }

    private void doClicks(boolean humanized) {
        if (!canRun()) {
            return;
        }
        if (!leftClick.get() && !rightClick.get()) {
            return;
        }
        double intervalTicks;
        if (humanized) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double base = 20.0 / Math.max(0.5, cps.random());
            double jittered = base + random.nextGaussian() * 0.9;
            if (random.nextDouble() < 0.015) {
                jittered += 3 + random.nextInt(3);
            }
            intervalTicks = Math.min(9.0, Math.max(1.0, jittered));
        } else {
            double rate = cps.random();
            if (rate <= 0.0) {
                return;
            }
            intervalTicks = Math.max(1.0, 20.0 / rate);
        }
        if (++ticksSinceClick < Math.round((float) intervalTicks)) {
            return;
        }
        ticksSinceClick = 0;
        MinecraftAttackInvoker invoker = (MinecraftAttackInvoker) mc;
        if (leftClick.get() && (!humanized || karhuTargetValid())) {
            invoker.perplexity$invokeStartAttack();
        }
        if (rightClick.get()) {
            invoker.perplexity$invokeStartUseItem();
        }
    }

    /**
     * The pre-tick crosshair is a tick stale, so only swing at targets still inside close
     * range and the current look direction. Air clicks always pass (a lone swing packet is
     * ignored); anything stale is skipped rather than sent at where the target was.
     */
    private static boolean karhuTargetValid() {
        if (!(mc.hitResult instanceof EntityHitResult entityHit)) {
            return true;
        }
        Entity target = entityHit.getEntity();
        if (target == null || target == mc.player || !target.isAlive()) {
            return false;
        }
        Vec3 eye = mc.player.getEyePosition();
        if (entityHit.getLocation().distanceTo(eye) > 2.5) {
            return false;
        }
        Vec3 toTarget = entityHit.getLocation().subtract(eye).normalize();
        return mc.player.getLookAngle().dot(toTarget) >= Math.cos(Math.toRadians(45.0));
    }

    private static boolean canRun() {
        return mc.player != null && mc.level != null && mc.gameMode != null
                && mc.screen == null && mc.isWindowActive()
                && !mc.player.isDeadOrDying() && !mc.player.isSpectator();
    }
    @Override
    public String getDisplayName() {
        return Obf.s("H08Dfm1BB1BvPkoD");
    }

}
