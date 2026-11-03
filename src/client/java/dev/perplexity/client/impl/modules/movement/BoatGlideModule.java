package dev.perplexity.client.impl.modules.movement;
import dev.perplexity.client.utils.Obf;

import dev.perplexity.client.event.PlayerEvents;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.DropdownSetting;
import dev.perplexity.client.module.setting.NumberSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;

/**
 * Fly while riding a boat. Sneak would dismount in vanilla, so vertical control is Jump for up
 * and look-pitch while moving for the rest (look down to descend). Packet mode additionally
 * reports the boat as on ground every tick.
 */
public final class BoatGlideModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private static final double DIAGONAL = 0.7071;
    private static final double MOMENTUM = 0.94;

    private final DropdownSetting mode = add(new DropdownSetting("Mode", "Normal",
            "Normal", "Velocity", "Packet"));
    private final NumberSetting speed = add(new NumberSetting("Speed",
            1.0, 0.1, 5.0, 0.1));

    public BoatGlideModule() {
        super(Category.MOVEMENT);
    }

    @Override
    protected void onDisable() {
        if (mc.player != null && mc.player.getVehicle() instanceof AbstractBoat boat) {
            boat.setNoGravity(false);
        }
    }

    @Override
    protected void onPlayerTick(PlayerEvents.TickEvent event) {
        if (event.phase() != PlayerEvents.Phase.AFTER) {
            return;
        }
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!(mc.player.getVehicle() instanceof AbstractBoat boat)) {
            return;
        }
        boat.setNoGravity(true);

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
        if (forward != 0.0 && strafe != 0.0) {
            forward *= DIAGONAL;
            strafe *= DIAGONAL;
        }

        double speedValue = speed.get();
        double yawRad = Math.toRadians(mc.player.getYRot());
        double targetX = (forward * -Math.sin(yawRad) + strafe * -Math.cos(yawRad)) * speedValue;
        double targetZ = (forward * Math.cos(yawRad) + strafe * -Math.sin(yawRad)) * speedValue;
        boolean moving = forward != 0.0 || strafe != 0.0;

        double targetY;
        if (mc.options.keyJump.isDown()) {
            targetY = speedValue;
        } else if (moving) {
            targetY = -Math.sin(Math.toRadians(mc.player.getXRot())) * speedValue;
        } else {
            targetY = 0.0;
        }

        double motionX;
        double motionY;
        double motionZ;
        if (mode.is("Velocity") && !moving) {
            Vec3 current = boat.getDeltaMovement();
            motionX = current.x * MOMENTUM;
            motionY = mc.options.keyJump.isDown() ? targetY : current.y * MOMENTUM;
            motionZ = current.z * MOMENTUM;
        } else {
            motionX = moving ? targetX : 0.0;
            motionY = targetY;
            motionZ = moving ? targetZ : 0.0;
        }

        boat.setDeltaMovement(motionX, motionY, motionZ);
        boat.fallDistance = 0.0;

        if (mode.is("Packet") && mc.getConnection() != null) {
            Vec3 pos = boat.position();
            mc.getConnection().send(new ServerboundMoveVehiclePacket(
                    new Vec3(pos.x + motionX, pos.y + motionY, pos.z + motionZ),
                    boat.getYRot(), boat.getXRot(), true));
        }
    }
    @Override
    public String getDisplayName() {
        return Obf.s("HFUWZW1EB0A=");
    }

}
