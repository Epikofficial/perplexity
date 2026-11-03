package dev.perplexity.client.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared, client-side player hooks for modules that need precise lifecycle data.
 * Listener failures are isolated so an optional module cannot break vanilla input.
 */
public final class PlayerEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger("Perplexity/PlayerEvents");

    private static final List<Consumer<TickEvent>> TICK = new CopyOnWriteArrayList<>();
    private static final List<Consumer<MoveEvent>> MOVE = new CopyOnWriteArrayList<>();
    private static final List<Consumer<AttackEvent>> ATTACK = new CopyOnWriteArrayList<>();
    private static final List<Consumer<SwingEvent>> SWING = new CopyOnWriteArrayList<>();
    private static final List<Consumer<JumpEvent>> JUMP = new CopyOnWriteArrayList<>();
    private static final List<Consumer<DamageEvent>> DAMAGE = new CopyOnWriteArrayList<>();
    private static final List<Consumer<CorrectionEvent>> CORRECTION = new CopyOnWriteArrayList<>();

    private static volatile PlayerSnapshot latestSnapshot;

    private PlayerEvents() {
    }

    public static Subscription onTick(Consumer<TickEvent> listener) {
        return subscribe(TICK, listener);
    }

    public static Subscription onMove(Consumer<MoveEvent> listener) {
        return subscribe(MOVE, listener);
    }

    public static Subscription onAttack(Consumer<AttackEvent> listener) {
        return subscribe(ATTACK, listener);
    }

    public static Subscription onSwing(Consumer<SwingEvent> listener) {
        return subscribe(SWING, listener);
    }

    public static Subscription onJump(Consumer<JumpEvent> listener) {
        return subscribe(JUMP, listener);
    }

    public static Subscription onDamage(Consumer<DamageEvent> listener) {
        return subscribe(DAMAGE, listener);
    }

    public static Subscription onCorrection(Consumer<CorrectionEvent> listener) {
        return subscribe(CORRECTION, listener);
    }

    public static PlayerSnapshot latestSnapshot() {
        return latestSnapshot;
    }

    public static void fireTick(LocalPlayer player, Phase phase, Vec3 previousPosition) {
        Vec3 position = player.position();
        Vec3 delta = position.subtract(previousPosition);
        PlayerSnapshot snapshot = snapshot(player, position, delta);
        latestSnapshot = snapshot;
        publish(TICK, new TickEvent(snapshot, phase));
    }

    public static void fireMove(LocalPlayer player, Phase phase, MoverType type,
                                Vec3 requested, Vec3 previousPosition) {
        Vec3 position = player.position();
        publish(MOVE, new MoveEvent(snapshot(player, position, position.subtract(previousPosition)),
            phase, type, requested));
    }

    public static AttackEvent fireAttack(Player player, Entity target, Phase phase) {
        AttackEvent event = new AttackEvent(player, target, phase, System.nanoTime());
        publish(ATTACK, event);
        return event;
    }

    public static void fireSwing(LocalPlayer player, InteractionHand hand) {
        publish(SWING, new SwingEvent(snapshot(player, player.position(), player.getDeltaMovement()),
            hand, System.nanoTime()));
    }

    public static void fireJump(LocalPlayer player, Phase phase) {
        publish(JUMP, new JumpEvent(snapshot(player, player.position(), player.getDeltaMovement()), phase));
    }

    public static void fireDamage(LocalPlayer player, DamageSource source, float healthBefore) {
        publish(DAMAGE, new DamageEvent(snapshot(player, player.position(), player.getDeltaMovement()),
            source, healthBefore, player.getHealth()));
    }

    public static void fireCorrection(LocalPlayer player, Vec3 oldPosition, float oldYaw, float oldPitch) {
        publish(CORRECTION, new CorrectionEvent(
            snapshot(player, player.position(), player.position().subtract(oldPosition)),
            oldPosition, player.position(), oldYaw, oldPitch, player.getYRot(), player.getXRot()));
    }

    private static PlayerSnapshot snapshot(LocalPlayer player, Vec3 position, Vec3 delta) {
        return new PlayerSnapshot(player, position, delta, player.getYRot(), player.getXRot(),
            player.onGround(), player.tickCount);
    }

    private static <T> Subscription subscribe(List<Consumer<T>> listeners, Consumer<T> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private static <T> void publish(List<Consumer<T>> listeners, T event) {
        for (Consumer<T> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException exception) {
                LOGGER.error("Player event listener failed", exception);
            }
        }
    }

    public enum Phase {
        BEFORE,
        AFTER
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    public record PlayerSnapshot(LocalPlayer player, Vec3 position, Vec3 delta, float yaw,
                                 float pitch, boolean onGround, int tick) {
    }

    public record TickEvent(PlayerSnapshot snapshot, Phase phase) {
    }

    public record MoveEvent(PlayerSnapshot snapshot, Phase phase, MoverType type, Vec3 requested) {
    }

    public static final class AttackEvent {
        private final Player player;
        private final Entity target;
        private final Phase phase;
        private final long nanoTime;
        private boolean cancelled;

        private AttackEvent(Player player, Entity target, Phase phase, long nanoTime) {
            this.player = player;
            this.target = target;
            this.phase = phase;
            this.nanoTime = nanoTime;
        }

        public Player player() {
            return player;
        }

        public Entity target() {
            return target;
        }

        public Phase phase() {
            return phase;
        }

        public long nanoTime() {
            return nanoTime;
        }

        public boolean cancelled() {
            return cancelled;
        }

        /** Cancellation is honored only during the BEFORE phase. */
        public void cancel() {
            if (phase == Phase.BEFORE) {
                cancelled = true;
            }
        }
    }

    public record SwingEvent(PlayerSnapshot snapshot, InteractionHand hand, long nanoTime) {
    }

    public record JumpEvent(PlayerSnapshot snapshot, Phase phase) {
    }

    public record DamageEvent(PlayerSnapshot snapshot, DamageSource source,
                              float healthBefore, float healthAfter) {
    }

    public record CorrectionEvent(PlayerSnapshot snapshot, Vec3 oldPosition, Vec3 newPosition,
                                  float oldYaw, float oldPitch, float newYaw, float newPitch) {
    }
}
