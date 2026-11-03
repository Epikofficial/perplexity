package dev.perplexity.client.module;

import dev.perplexity.client.event.PlayerEvents;
import dev.perplexity.client.event.ClientEvents;
import dev.perplexity.client.event.RenderEvents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for the client's features. Currently empty: register your own modules here and the
 * ClickGUI will pick them up per {@link Category} automatically.
 */
public final class ModuleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Perplexity/Modules");
    private final List<Module> modules = new ArrayList<>();

    private ModuleManager() {
        ClientEvents.onTick(event -> dispatch(module -> module.clientTick(event)));
        RenderEvents.onFrame(event -> dispatch(module -> module.frame(event)));
        RenderEvents.onWorld(event -> dispatch(module -> module.worldRender(event)));
        PlayerEvents.onTick(event -> dispatch(module -> module.playerTick(event)));
        PlayerEvents.onMove(event -> dispatch(module -> module.playerMove(event)));
        PlayerEvents.onAttack(event -> dispatch(module -> module.playerAttack(event)));
        PlayerEvents.onSwing(event -> dispatch(module -> module.playerSwing(event)));
        PlayerEvents.onJump(event -> dispatch(module -> module.playerJump(event)));
        PlayerEvents.onDamage(event -> dispatch(module -> module.playerDamage(event)));
        PlayerEvents.onCorrection(event -> dispatch(module -> module.playerCorrection(event)));
    }

    private static final class Holder {
        private static final ModuleManager INSTANCE = new ModuleManager();
    }

    public static ModuleManager get() {
        return Holder.INSTANCE;
    }

    public void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    /** Ticks every enabled module once; disabled modules are skipped inside {@link Module#tick()}. */
    public void tick() {
        for (Module module : modules) {
            module.tick();
        }
    }

    public List<Module> byCategory(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.getCategory() == category) {
                result.add(module);
            }
        }
        return result;
    }

    private void dispatch(Consumer<Module> action) {
        for (Module module : modules) {
            try {
                action.accept(module);
            } catch (RuntimeException exception) {
                LOGGER.error("Module event failed for {}", module.getClass().getSimpleName(), exception);
            }
        }
    }
}
