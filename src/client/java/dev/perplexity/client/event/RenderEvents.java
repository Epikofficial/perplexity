package dev.perplexity.client.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.AbstractWorldRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared frame and world-render lifecycle for render-aware modules. */
public final class RenderEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger("Perplexity/RenderEvents");
    private static final List<Consumer<FrameEvent>> FRAME = new CopyOnWriteArrayList<>();
    private static final List<Consumer<WorldEvent>> WORLD = new CopyOnWriteArrayList<>();
    private static boolean worldBridgeInstalled;

    private RenderEvents() {
    }

    public static PlayerEvents.Subscription onFrame(Consumer<FrameEvent> listener) {
        FRAME.add(listener);
        return () -> FRAME.remove(listener);
    }

    public static PlayerEvents.Subscription onWorld(Consumer<WorldEvent> listener) {
        WORLD.add(listener);
        return () -> WORLD.remove(listener);
    }

    /** Installs the context-rich Fabric bridge once; frame timing itself comes from a mixin. */
    public static synchronized void installWorldBridge() {
        if (worldBridgeInstalled) {
            return;
        }
        worldBridgeInstalled = true;
        WorldRenderEvents.START_MAIN.register(context -> fireWorld(context, WorldPhase.START_MAIN));
        WorldRenderEvents.END_MAIN.register(context -> fireWorld(context, WorldPhase.END_MAIN));
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(
            context -> fireWorld(context, WorldPhase.BEFORE_DEBUG_RENDER));
    }

    public static void fireFrame(Minecraft minecraft, DeltaTracker deltaTracker,
                                 PlayerEvents.Phase phase) {
        publish(FRAME, new FrameEvent(minecraft, deltaTracker, phase), "Frame render");
    }

    private static void fireWorld(AbstractWorldRenderContext context, WorldPhase phase) {
        publish(WORLD, new WorldEvent(context, phase), "World render");
    }

    private static <T> void publish(List<Consumer<T>> listeners, T event, String name) {
        for (Consumer<T> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException exception) {
                LOGGER.error("{} listener failed", name, exception);
            }
        }
    }

    public enum WorldPhase {
        START_MAIN,
        END_MAIN,
        BEFORE_DEBUG_RENDER
    }

    public record FrameEvent(Minecraft minecraft, DeltaTracker deltaTracker,
                             PlayerEvents.Phase phase) {
    }

    public record WorldEvent(AbstractWorldRenderContext context, WorldPhase phase) {
    }
}
