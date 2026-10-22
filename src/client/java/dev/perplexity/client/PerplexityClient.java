package dev.perplexity.client;

import com.mojang.blaze3d.platform.InputConstants;

import dev.perplexity.client.gui.auth.AuthScreen;
import dev.perplexity.client.gui.clickgui.ClickGuiScreen;
import dev.perplexity.client.auth.AuthGate;
import dev.perplexity.client.config.ConfigManager;
import dev.perplexity.client.module.ModuleManager;
import dev.perplexity.client.impl.modules.combat.AnchorHelperModule;
import dev.perplexity.client.impl.modules.combat.KnockbackModule;
import dev.perplexity.client.impl.modules.combat.ClickHelperModule;
import dev.perplexity.client.impl.modules.combat.AttackHelperModule;
import dev.perplexity.client.impl.modules.combat.KillAuraModule;
import dev.perplexity.client.impl.modules.combat.CriticalsModule;
import dev.perplexity.client.impl.modules.combat.ReachModule;
import dev.perplexity.client.impl.modules.misc.BottleHelperModule;
import dev.perplexity.client.impl.modules.misc.ProtectionModule;
import dev.perplexity.client.impl.modules.misc.AutoGgModule;
import dev.perplexity.client.impl.modules.misc.AutoReconnectModule;
import dev.perplexity.client.impl.modules.movement.KeepSprintModule;
import dev.perplexity.client.impl.modules.movement.AirJumpModule;
import dev.perplexity.client.impl.modules.movement.BoatGlideModule;
import dev.perplexity.client.impl.modules.movement.FlyModule;
import dev.perplexity.client.impl.modules.movement.GuiWalkModule;
import dev.perplexity.client.impl.modules.movement.SprintModule;
import dev.perplexity.client.impl.modules.player.QuickRespawnModule;
import dev.perplexity.client.impl.modules.player.FeatherFallModule;
import dev.perplexity.client.impl.modules.player.NoPushModule;
import dev.perplexity.client.impl.modules.player.SpinBotModule;
import dev.perplexity.client.impl.modules.render.BrightnessModule;
import dev.perplexity.client.impl.modules.render.CameraModule;
import dev.perplexity.client.impl.modules.render.ClearSightModule;
import dev.perplexity.client.impl.modules.render.OreViewModule;
import dev.perplexity.client.impl.modules.ui.MenuKeyModule;
import dev.perplexity.client.impl.modules.ui.PanicModule;
import dev.perplexity.client.impl.modules.ui.PopupsModule;
import dev.perplexity.client.impl.modules.ui.TabControlModule;
import dev.perplexity.client.utils.rotation.RotationManager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;

public class PerplexityClient implements ClientModInitializer {
    private boolean clickGuiDown;
    private boolean panicDown;

    @Override
    public void onInitializeClient() {
        ModuleManager.get().register(new AnchorHelperModule());
        ModuleManager.get().register(new KnockbackModule());
        ModuleManager.get().register(new ClickHelperModule());
        ModuleManager.get().register(new AttackHelperModule());
        ModuleManager.get().register(new KillAuraModule());
        ModuleManager.get().register(new ReachModule());
        ModuleManager.get().register(new CriticalsModule());
        ModuleManager.get().register(new FlyModule());
        ModuleManager.get().register(new AirJumpModule());
        ModuleManager.get().register(new SprintModule());
        ModuleManager.get().register(new GuiWalkModule());
        ModuleManager.get().register(new KeepSprintModule());
        ModuleManager.get().register(new BoatGlideModule());
        ModuleManager.get().register(new FeatherFallModule());
        ModuleManager.get().register(new QuickRespawnModule());
        ModuleManager.get().register(new NoPushModule());
        ModuleManager.get().register(new SpinBotModule());
        ModuleManager.get().register(new ProtectionModule());
        ModuleManager.get().register(new BottleHelperModule());
        ModuleManager.get().register(new AutoReconnectModule());
        ModuleManager.get().register(new AutoGgModule());
        ModuleManager.get().register(new ClearSightModule());
        ModuleManager.get().register(new BrightnessModule());
        ModuleManager.get().register(new CameraModule());
        ModuleManager.get().register(new OreViewModule());
        ModuleManager.get().register(new PopupsModule());
        ModuleManager.get().register(new MenuKeyModule());
        TabControlModule tabs = new TabControlModule();
        tabs.setEnabled(true);
        ModuleManager.get().register(tabs);
        ModuleManager.get().register(new PanicModule());
        ConfigManager.loadDefault();

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.flush());
        ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
            AirJumpModule.preTick();
            ClickHelperModule.preTick();
            AttackHelperModule.preTick();
        });
        // CHANGE_LOOK rotations have to be applied to the camera every frame, after the tick
        // rotation has been committed, with the frame's partial tick.
        WorldRenderEvents.START_MAIN.register(context -> RotationManager.applyChangeLookRotation(
            Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)));
        WorldRenderEvents.END_MAIN.register(context -> AttackHelperModule.onFrame());
    }

    private void onEndTick(Minecraft minecraft) {
        ModuleManager.get().tick();
        RotationManager.tick(minecraft);
        ConfigManager.tick();

        int key = MenuKeyModule.openKey();
        if (key == 0 || minecraft.getWindow() == null) {
            return;
        }

        boolean down = InputConstants.isKeyDown(minecraft.getWindow(), key);
        if (!PanicModule.panicked() && down && !clickGuiDown) {
            if (minecraft.screen instanceof ClickGuiScreen) {
                minecraft.setScreen(null);
            } else if (minecraft.screen == null) {
                minecraft.setScreen(AuthGate.passed() ? new ClickGuiScreen() : new AuthScreen());
            }
        }
        clickGuiDown = down;

        int panicKey = PanicModule.panicKey();
        boolean panicPressed = panicKey != 0 && InputConstants.isKeyDown(minecraft.getWindow(), panicKey);
        if (panicPressed && !panicDown) {
            PanicModule.togglePanic();
            if (panicKey == key) {
                clickGuiDown = true;
            }
        }
        panicDown = panicPressed;
    }
}
