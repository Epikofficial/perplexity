package dev.perplexity.client.impl.modules.player;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.NumberSetting;
import dev.perplexity.client.utils.Obf;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Spins the rendered model and server-facing yaw while leaving the local camera untouched. */
public final class SpinBotModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();
    private static SpinBotModule instance;

    private final NumberSetting speed = add(new NumberSetting("Spin Speed",
            20.0, 1.0, 1000.0, 1.0));

    private float spinYaw;

    public SpinBotModule() {
        super(Category.PLAYER);
        instance = this;
    }

    @Override
    protected void onEnable() {
        spinYaw = mc.player == null ? 0.0F : mc.player.getYRot();
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            float viewYaw = mc.player.getYRot();
            mc.player.yBodyRot = viewYaw;
            mc.player.yBodyRotO = viewYaw;
            mc.player.yHeadRot = viewYaw;
            mc.player.yHeadRotO = viewYaw;
        }
    }

    @Override
    protected void onTick() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        spinYaw = Mth.wrapDegrees(spinYaw + (float) speed.get());

        // Model-only fields: the camera continues to use LocalPlayer#getYRot unchanged.
        mc.player.yBodyRotO = spinYaw;
        mc.player.yBodyRot = spinYaw;
        mc.player.yHeadRotO = spinYaw;
        mc.player.yHeadRot = spinYaw;
    }

    public static float packetYaw(float vanillaYaw) {
        return instance != null && instance.isEnabled() ? instance.spinYaw : vanillaYaw;
    }

    @Override
    public String getDisplayName() {
        return Obf.s("DUoef21ABE0=");
    }
}
