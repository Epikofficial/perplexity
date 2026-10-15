package dev.perplexity.client.impl.modules.movement;
import dev.perplexity.client.utils.Obf;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.DropdownSetting;
import dev.perplexity.client.module.setting.NumberSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;

/**
 * Flight with two drives:
 *
 * <ul>
 *   <li>Normal: full motion control, velocity hard-set from input every tick. For vanilla
 *   servers and no-anticheat play; any motion check will see superhuman velocity.</li>
 *   <li>Creative: spoofs the flying ability and lets vanilla's own creative flight move the
 *   player. Untouched when genuinely in creative, so nothing fights the real ability.</li>
 * </ul>
 */
public final class FlyModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private static final double DIAGONAL = 0.7071;

    private final DropdownSetting mode = add(new DropdownSetting("Mode", "Normal",
            "Normal", "Creative"));
    private final NumberSetting speed = add(new NumberSetting("Speed",
            0.8, 0.1, 10.0, 0.1));
    private final BooleanSetting noGravity = add(new BooleanSetting("No Gravity",
            true));
    private final BooleanSetting antiKick = add(new BooleanSetting("Anti Kick",
            false));

    private int ticks;
    private boolean flyingApplied;

    public FlyModule() {
        super(Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        ticks = 0;
        flyingApplied = false;
        if (mc.player != null && noGravity.get()) {
            mc.player.setNoGravity(true);
        }
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) {
            mc.player.setNoGravity(false);
            if (flyingApplied && !mc.player.hasInfiniteMaterials()) {
                mc.player.getAbilities().flying = false;
            }
            Vec3 velocity = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(velocity.x, Math.min(velocity.y, 0.0), velocity.z);
        }
        flyingApplied = false;
    }

    @Override
    protected void onTick() {
        if (mc.player == null || mc.level == null) {
            return;
        }
        ticks++;
        if (mode.is("Creative")) {
            tickCreative();
            return;
        }
        tickNormal();
    }

    private void tickNormal() {
        mc.player.setNoGravity(noGravity.get());

        double forward = inputForward();
        double strafe = inputStrafe();
        double vertical = inputVertical();
        if (forward != 0.0 && strafe != 0.0) {
            forward *= DIAGONAL;
            strafe *= DIAGONAL;
        }

        double speedValue = speed.get();
        double yawRad = Math.toRadians(mc.player.getYRot());
        double motionX = (forward * -Math.sin(yawRad) + strafe * -Math.cos(yawRad)) * speedValue;
        double motionZ = (forward * Math.cos(yawRad) + strafe * -Math.sin(yawRad)) * speedValue;
        double motionY = vertical * speedValue;

        if (antiKick.get() && vertical == 0.0 && ticks % 30 == 0) {
            motionY = -0.04;
        }

        mc.player.setDeltaMovement(motionX, motionY, motionZ);
        mc.player.fallDistance = 0.0;
    }

    private void tickCreative() {
        if (mc.player.hasInfiniteMaterials()) {
            flyingApplied = false;
            return;
        }
        Abilities abilities = mc.player.getAbilities();
        abilities.flying = true;
        abilities.setFlyingSpeed((float) (speed.get() * 0.05));
        flyingApplied = true;
        mc.player.fallDistance = 0.0;
    }

    private static double inputForward() {
        double forward = 0.0;
        if (mc.options.keyUp.isDown()) {
            forward += 1.0;
        }
        if (mc.options.keyDown.isDown()) {
            forward -= 1.0;
        }
        return forward;
    }

    private static double inputStrafe() {
        double strafe = 0.0;
        if (mc.options.keyRight.isDown()) {
            strafe += 1.0;
        }
        if (mc.options.keyLeft.isDown()) {
            strafe -= 1.0;
        }
        return strafe;
    }

    private static double inputVertical() {
        double vertical = 0.0;
        if (mc.options.keyJump.isDown()) {
            vertical += 1.0;
        }
        if (mc.options.keyShift.isDown()) {
            vertical -= 1.0;
        }
        return vertical;
    }
    @Override
    public String getDisplayName() {
        return Obf.s("GFYediV2");
    }

}
