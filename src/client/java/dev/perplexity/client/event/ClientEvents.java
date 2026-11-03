package dev.perplexity.client.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Global Minecraft-client lifecycle published by the client tick mixin. */
public final class ClientEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger("Perplexity/ClientEvents");
    private static final List<Consumer<TickEvent>> TICK = new CopyOnWriteArrayList<>();

    private ClientEvents() {
    }

    public static PlayerEvents.Subscription onTick(Consumer<TickEvent> listener) {
        TICK.add(listener);
        return () -> TICK.remove(listener);
    }

    public static void fireTick(Minecraft minecraft, PlayerEvents.Phase phase) {
        TickEvent event = new TickEvent(minecraft, phase);
        for (Consumer<TickEvent> listener : TICK) {
            try {
                listener.accept(event);
            } catch (RuntimeException exception) {
                LOGGER.error("Client tick listener failed", exception);
            }
        }
    }

    public record TickEvent(Minecraft minecraft, PlayerEvents.Phase phase) {
    }
}
