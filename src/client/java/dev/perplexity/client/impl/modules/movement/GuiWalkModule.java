package dev.perplexity.client.impl.modules.movement;
import dev.perplexity.client.utils.Obf;

import dev.perplexity.client.mixin.movement.KeyMappingAccessor;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;

/**
 * Walk while a container screen is open. Vanilla releases movement keys when a GUI opens, so
 * every tick this re-presses them from their physical state. Text-input screens are excluded:
 * the anvil's rename box would otherwise steer you while you type.
 */
public final class GuiWalkModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    public GuiWalkModule() {
        super(Category.PLAYER);
    }

    @Override
    protected void onTick() {
        if (mc.player == null || mc.level == null || mc.screen == null) {
            return;
        }
        if (!(mc.screen instanceof AbstractContainerScreen) || mc.screen instanceof AnvilScreen) {
            return;
        }
        force(mc.options.keyUp);
        force(mc.options.keyDown);
        force(mc.options.keyLeft);
        force(mc.options.keyRight);
        force(mc.options.keyJump);
        force(mc.options.keyShift);
        force(mc.options.keySprint);
    }

    private static void force(KeyMapping key) {
        int code = ((KeyMappingAccessor) key).perplexity$getKey().getValue();
        key.setDown(InputConstants.isKeyDown(mc.getWindow(), code));
    }
    @Override
    public String getDisplayName() {
        return Obf.s("F1QBdCN2BEt1dXgQdhY=");
    }

}
