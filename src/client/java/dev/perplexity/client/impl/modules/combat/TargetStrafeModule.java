package dev.perplexity.client.impl.modules.combat;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.ModeSetting;
import dev.perplexity.client.module.setting.NumberSetting;
import dev.perplexity.client.utils.Obf;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/** Orbits Kill Aura's selected target while keeping the configured radius. */
public final class TargetStrafeModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private final ModeSetting mode = add(new ModeSetting("Mode", "Switch",
            "Left", "Right", "Switch", "Random"));
    private final NumberSetting radius = add(new NumberSetting("Radius",
            3.0, 0.5, 6.0, 0.1));
    private final NumberSetting speed = add(new NumberSetting("Speed",
            0.35, 0.05, 2.0, 0.05));
    private final BooleanSetting jumping = add(new BooleanSetting("Jumping", false));

    private int direction = 1;
    private int randomTicks;

    public TargetStrafeModule() {
        super(Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        direction = mode.is("Left") ? -1 : 1;
        randomTicks = nextRandomInterval();
    }

    @Override
    protected void onTick() {
        if (mc.player == null || mc.level == null || mc.player.isPassenger()
                || mc.player.getAbilities().flying || mc.player.isFallFlying()) {
            return;
        }
        LivingEntity target = KillAuraModule.currentTarget();
        if (target == null) {
            return;
        }

        updateDirection();

        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance < 1.0E-4) {
            return;
        }

        double towardX = dx / distance;
        double towardZ = dz / distance;
        double tangentX = -towardZ * direction;
        double tangentZ = towardX * direction;

        // Gently pull inward or push outward instead of snapping to the radius.
        double correction = Math.max(-0.75, Math.min(0.75,
                (distance - radius.get()) / Math.max(0.5, radius.get())));
        double moveSpeed = speed.get();
        double motionX = (tangentX + towardX * correction) * moveSpeed;
        double motionZ = (tangentZ + towardZ * correction) * moveSpeed;
        Vec3 current = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(motionX, current.y, motionZ);

        if (jumping.get() && mc.player.onGround()) {
            mc.player.jumpFromGround();
        }
    }

    private void updateDirection() {
        if (mode.is("Left")) {
            direction = -1;
            return;
        }
        if (mode.is("Right")) {
            direction = 1;
            return;
        }
        if (mc.player.horizontalCollision) {
            direction = -direction;
            randomTicks = nextRandomInterval();
            return;
        }
        if (mode.is("Random") && --randomTicks <= 0) {
            direction = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
            randomTicks = nextRandomInterval();
        }
    }

    private static int nextRandomInterval() {
        return ThreadLocalRandom.current().nextInt(20, 61);
    }

    @Override
    public String getDisplayName() {
        return Obf.s("ClsFdih2S2p4J04Xfw==");
    }
}
