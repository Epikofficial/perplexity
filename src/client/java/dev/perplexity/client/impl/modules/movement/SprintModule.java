package dev.perplexity.client.impl.modules.movement;
import dev.perplexity.client.utils.Obf;

import dev.perplexity.client.event.PlayerEvents;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.NumberSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Strafe speed. Drives horizontal velocity from input every tick while leaving vertical
 * motion (jumping, gravity) to vanilla.
 */
public final class SprintModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private static final double DIAGONAL = 0.7071;

    private final NumberSetting speed = add(new NumberSetting("Speed",
            1.0, 0.1, 5.0, 0.1));
    private final BooleanSetting onlyGround = add(new BooleanSetting("Only Ground",
            false));

    public SprintModule() {
        super(Category.MOVEMENT);
    }

    @Override
    protected void onPlayerTick(PlayerEvents.TickEvent event) {
        if (event.phase() != PlayerEvents.Phase.AFTER) {
            return;
        }
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (onlyGround.get() && !mc.player.onGround()) {
            return;
        }

        double forward = 0.0;
        if (mc.options.keyUp.isDown()) {
            forward += 1.0;
        }
        if (mc.options.keyDown.isDown()) {
            forward -= 1.0;
        }
        double strafe = 0.0;
        if (mc.options.keyRight.isDown()) {
            strafe += 1.0;
        }
        if (mc.options.keyLeft.isDown()) {
            strafe -= 1.0;
        }
        if (forward == 0.0 && strafe == 0.0) {
            return;
        }
        if (forward != 0.0 && strafe != 0.0) {
            forward *= DIAGONAL;
            strafe *= DIAGONAL;
        }

        double speedValue = speed.get();
        double yawRad = Math.toRadians(mc.player.getYRot());
        double motionX = (forward * -Math.sin(yawRad) + strafe * -Math.cos(yawRad)) * speedValue;
        double motionZ = (forward * Math.cos(yawRad) + strafe * -Math.sin(yawRad)) * speedValue;

        Vec3 velocity = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(motionX, velocity.y, motionZ);
    }
    @Override
    public String getDisplayName() {
        return Obf.s("DUoSdCk=");
    }

}
