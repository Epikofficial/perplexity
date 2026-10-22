package dev.perplexity.client.impl.modules.combat;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.ModeSetting;
import dev.perplexity.client.module.setting.NumberSetting;
import dev.perplexity.client.utils.Obf;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/** Extends targeting range, with an opt-in packet-clip mode for distant attacks. */
public final class ReachModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final double VANILLA_REACH = 3.0;
    private static final double CLIP_STEP = 9.0;
    private static final long CLIP_COOLDOWN_MS = 250L;
    private static ReachModule instance;

    private final ModeSetting mode = add(new ModeSetting("Mode", "Standard",
            "Standard", "Packet Clip"));
    private final NumberSetting distance = add(new NumberSetting("Distance",
            4.0, VANILLA_REACH, 6.0, 0.1));
    private final NumberSetting clipDistance = add(new NumberSetting("Clip Distance",
            32.0, 6.0, 128.0, 1.0));
    private final NumberSetting chance = add(new NumberSetting("Reach Chance",
            100.0, 0.0, 100.0, 1.0));
    private Vec3 clipOrigin;
    private Vec3 clipDestination;
    private Entity queuedClipTarget;
    private double queuedClipRange;
    private long nextClipAt;

    public ReachModule() {
        super(Category.COMBAT);
        instance = this;
        distance.setVisibleWhen(() -> mode.is("Standard"));
        clipDistance.setVisibleWhen(() -> mode.is("Packet Clip"));
    }

    public static double targetingRange(double vanilla) {
        if (instance == null || !instance.isEnabled()) {
            return vanilla;
        }
        double configured = instance.mode.is("Packet Clip")
                ? instance.clipDistance.get() : instance.distance.get();
        return Math.max(vanilla, configured);
    }

    /** Returns false only when an attempted hit needs extended reach and loses its chance roll. */
    public static boolean allowAttack(Entity target) {
        if (instance == null || !instance.isEnabled() || mc.player == null || target == null) {
            return true;
        }
        if (instance.queuedClipTarget == target) {
            return true;
        }
        double distanceToBox = target.getBoundingBox().distanceToSqr(mc.player.getEyePosition());
        if (distanceToBox <= VANILLA_REACH * VANILLA_REACH) {
            return true;
        }
        if (instance.mode.is("Packet Clip")
                && distanceToBox > instance.clipDistance.get() * instance.clipDistance.get()) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble(100.0) < instance.chance.get();
    }

    /** Lets KillAura request one clip attack without enabling Reach's own mode. */
    public static boolean queueClipAttack(Entity target, double maxRange) {
        if (instance == null || mc.player == null || mc.getConnection() == null || target == null) {
            return false;
        }
        double distanceToBox = target.getBoundingBox().distanceToSqr(mc.player.getEyePosition());
        if (distanceToBox <= 6.0 * 6.0) {
            return true;
        }
        if (distanceToBox > maxRange * maxRange || mc.player.isPassenger()
                || System.currentTimeMillis() < instance.nextClipAt) {
            return false;
        }
        Vec3 origin = mc.player.position();
        Vec3 destination = destination(origin, target);
        int steps = (int) Math.ceil(origin.distanceTo(destination) / CLIP_STEP);
        if (steps < 1 || steps > 16) {
            return false;
        }
        instance.queuedClipTarget = target;
        instance.queuedClipRange = maxRange;
        return true;
    }

    /** Sends a bounded path before the normal attack packet; the return hook sends it back. */
    public static boolean beforeAttack(Player attacker, Entity target) {
        if (instance == null || attacker != mc.player || target == null || mc.getConnection() == null) {
            return true;
        }
        boolean queued = instance.queuedClipTarget == target;
        if (!queued && (!instance.isEnabled() || !instance.mode.is("Packet Clip"))) {
            return true;
        }
        double distanceToBox = target.getBoundingBox().distanceToSqr(attacker.getEyePosition());
        if (distanceToBox <= 6.0 * 6.0) {
            return true;
        }
        double maxRange = queued ? instance.queuedClipRange : instance.clipDistance.get();
        if (distanceToBox > maxRange * maxRange) {
            return false;
        }
        if (attacker.isPassenger() || System.currentTimeMillis() < instance.nextClipAt) {
            return false;
        }
        Vec3 origin = attacker.position();
        Vec3 destination = destination(origin, target);
        int steps = (int) Math.ceil(origin.distanceTo(destination) / CLIP_STEP);
        if (steps < 1 || steps > 16) {
            return false;
        }
        instance.clipOrigin = origin;
        instance.clipDestination = destination;
        instance.nextClipAt = System.currentTimeMillis() + CLIP_COOLDOWN_MS;
        sendPath(origin, destination, steps, attacker.horizontalCollision);
        return true;
    }

    private static Vec3 destination(Vec3 origin, Entity target) {
        Vec3 towardTarget = target.position().subtract(origin);
        return target.position().subtract(towardTarget.normalize().scale(2.0));
    }

    public static boolean isClippingAttack() {
        return instance != null && instance.clipOrigin != null;
    }

    public static void afterAttack() {
        if (instance == null) {
            return;
        }
        instance.queuedClipTarget = null;
        instance.queuedClipRange = 0.0;
        if (instance.clipOrigin == null) {
            return;
        }
        Vec3 origin = instance.clipOrigin;
        Vec3 destination = instance.clipDestination;
        instance.clipOrigin = null;
        instance.clipDestination = null;
        if (mc.getConnection() != null && mc.player != null) {
            int steps = (int) Math.ceil(origin.distanceTo(destination) / CLIP_STEP);
            sendPath(destination, origin, steps, mc.player.horizontalCollision);
        }
    }

    private static void sendPath(Vec3 from, Vec3 to, int steps, boolean horizontalCollision) {
        for (int i = 1; i <= steps; i++) {
            Vec3 point = from.lerp(to, (double) i / steps);
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                    point.x, point.y, point.z, false, horizontalCollision));
        }
    }

    @Override
    protected void onDisable() {
        afterAttack();
    }

    @Override
    public String getDisplayName() {
        return Obf.s("DF8WciU=");
    }
}
