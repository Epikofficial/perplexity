package dev.perplexity.client.impl.modules.combat;

import dev.perplexity.client.event.PlayerEvents;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.ModeSetting;
import dev.perplexity.client.module.setting.NumberSetting;
import dev.perplexity.client.utils.Obf;
import dev.perplexity.client.utils.rotation.MovementCorrection;
import dev.perplexity.client.utils.rotation.Rotation;
import dev.perplexity.client.utils.rotation.RotationManager;
import dev.perplexity.client.utils.rotation.RotationMode;
import dev.perplexity.client.utils.rotation.RotationPriority;
import dev.perplexity.client.utils.rotation.RotationTarget;
import dev.perplexity.client.utils.rotation.RotationUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Attacks the nearest valid living target inside the configured range. */
public final class KillAuraModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();
    private static KillAuraModule instance;

    private final ModeSetting mode = add(new ModeSetting("Mode", "Standard",
            "Standard", "Packet Clip"));
    private final NumberSetting range = add(new NumberSetting("Range",
            3.5, 1.0, 6.0, 0.1));
    private final NumberSetting clipRange = add(new NumberSetting("Clip Range",
            32.0, 6.0, 128.0, 1.0));
    private final NumberSetting attacksPerSecond = add(new NumberSetting("Attacks Per Second",
            10.0, 1.0, 20.0, 1.0));
    private final BooleanSetting players = add(new BooleanSetting("Players", true));
    private final BooleanSetting mobs = add(new BooleanSetting("Mobs", false));
    private final BooleanSetting throughWalls = add(new BooleanSetting("Through Walls", false));
    private final BooleanSetting attackAllies = add(new BooleanSetting("Attack Allies", false));
    private final BooleanSetting swordOnly = add(new BooleanSetting("Sword Only", false));
    private final BooleanSetting autoCooldown = add(new BooleanSetting("Auto Cooldown", true));
    private final BooleanSetting silentMode = add(new BooleanSetting("Silent Mode", false));

    private long nextAttackAt;
    private LivingEntity aimedTarget;
    private LivingEntity trackedTarget;
    private int aimedTicks;

    public KillAuraModule() {
        super(Category.COMBAT);
        instance = this;
        range.setVisibleWhen(() -> mode.is("Standard"));
        clipRange.setVisibleWhen(() -> mode.is("Packet Clip"));
        attacksPerSecond.setVisibleWhen(() -> !autoCooldown.get());
    }

    @Override
    protected void onEnable() {
        nextAttackAt = 0L;
        aimedTarget = null;
        trackedTarget = null;
        aimedTicks = 0;
    }

    @Override
    protected void onDisable() {
        nextAttackAt = 0L;
        aimedTarget = null;
        trackedTarget = null;
        aimedTicks = 0;
        RotationManager.clear(this);
    }

    @Override
    protected void onPlayerTick(PlayerEvents.TickEvent event) {
        // Queue combat before LocalPlayer sends this tick's movement packet. Grim's Post check
        // considers attacks sent after movement to be out of order; the following movement packet
        // also carries the already-prepared silent rotation and closes the action boundary cleanly.
        if (event.phase() == PlayerEvents.Phase.BEFORE) {
            if (canRun() && (!swordOnly.get() || mc.player.getMainHandItem().is(ItemTags.SWORDS))) {
                tryAttack();
            }
            return;
        }
        if (!canRun()) {
            trackedTarget = null;
            clearSilentAim();
            return;
        }
        if (swordOnly.get() && !mc.player.getMainHandItem().is(ItemTags.SWORDS)) {
            trackedTarget = null;
            clearSilentAim();
            return;
        }

        LivingEntity target = findTarget();
        trackedTarget = target;
        if (!silentMode.get()) {
            clearSilentAim();
            return;
        }

        if (target == null) {
            clearSilentAim();
            return;
        }

        updateSilentAim(target);
    }

    private void tryAttack() {
        if (!canRun() || (swordOnly.get() && !mc.player.getMainHandItem().is(ItemTags.SWORDS))) {
            return;
        }

        LivingEntity target = silentMode.get() ? aimedTarget : findTarget();
        if (target == null || !validTarget(target)) {
            return;
        }
        double selectedRange = mode.is("Packet Clip") ? clipRange.get() : range.get();
        if (distanceToHitboxSqr(target) > selectedRange * selectedRange) {
            return;
        }

        // The previous AFTER phase chose the look and this tick's movement packet sent it before
        // this attack phase. Require ownership so another rotation module cannot steal that aim.
        if (silentMode.get() && (aimedTicks < 1 || !RotationManager.ownsActiveRequest(this))) {
            return;
        }
        // A moving target can leave the previous tick's server-facing ray. Do not send
        // an attack until the rotation actually sent to the server crosses its hitbox.
        if (silentMode.get() && !serverRayHits(target, selectedRange)) {
            return;
        }

        if (autoCooldown.get() && mc.player.getAttackStrengthScale(0.0F) < 0.9F) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextAttackAt) {
            return;
        }

        if (mode.is("Packet Clip") && !ReachModule.queueClipAttack(target, clipRange.get())) {
            return;
        }
        try {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
        } finally {
            ReachModule.afterAttack();
        }
        nextAttackAt = autoCooldown.get()
                ? now
                : now + Math.max(50L, Math.round(1000.0 / attacksPerSecond.get()));
    }

    private void updateSilentAim(LivingEntity target) {
        if (aimedTarget == target) {
            aimedTicks++;
        } else {
            aimedTarget = target;
            aimedTicks = 0;
        }
        // The nearest AABB point is on its surface and a tiny movement or mouse-GCD
        // rounding can move that ray outside the server's hitbox. Aim well inside it.
        boolean accepted = RotationManager.request(this, RotationTarget.to(RotationUtils.to(target))
                .priority(RotationPriority.HIGH)
                .mode(RotationMode.SILENT)
                // Grim predicts movement using the yaw sent in the movement packet.
                // Remap keyboard input and local physics to that same yaw while the
                // camera keeps its original direction.
                .movementCorrection(MovementCorrection.SILENT)
                .ticksUntilReset(2)
                .build());
        if (!accepted) {
            aimedTicks = 0;
        }
    }

    private boolean serverRayHits(LivingEntity target, double range) {
        Rotation sent = RotationManager.serverRotation();
        Vec3 eyes = mc.player.getEyePosition();
        AABB box = target.getBoundingBox();
        double inset = Math.min(0.08, Math.min(box.getXsize(), box.getZsize()) * 0.1);
        AABB safeBox = new AABB(box.minX + inset, box.minY + Math.min(0.08, box.getYsize() * 0.1),
                box.minZ + inset, box.maxX - inset, box.maxY - Math.min(0.08, box.getYsize() * 0.1),
                box.maxZ - inset);
        return safeBox.clip(eyes, eyes.add(sent.direction().scale(range + 0.25))).isPresent();
    }

    private void clearSilentAim() {
        if (aimedTarget != null || aimedTicks != 0) {
            aimedTarget = null;
            aimedTicks = 0;
            RotationManager.clear(this);
        }
    }

    /** Current aura selection for movement modules such as Target Strafe. */
    public static LivingEntity currentTarget() {
        if (instance == null || !instance.isEnabled() || instance.trackedTarget == null
                || !instance.trackedTarget.isAlive()) {
            return null;
        }
        return instance.trackedTarget;
    }

    private LivingEntity findTarget() {
        LivingEntity nearest = null;
        double selectedRange = mode.is("Packet Clip") ? clipRange.get() : range.get();
        double nearestDistance = selectedRange * selectedRange;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !validTarget(living)) {
                continue;
            }
            double distance = distanceToHitboxSqr(living);
            if (distance <= nearestDistance) {
                nearest = living;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private boolean validTarget(LivingEntity target) {
        if (target == mc.player || !target.isAlive() || target.isSpectator() || !target.isAttackable()) {
            return false;
        }
        boolean isPlayer = target instanceof Player;
        if ((isPlayer && !players.get()) || (!isPlayer && !mobs.get())) {
            return false;
        }
        if (!attackAllies.get() && mc.player.isAlliedTo(target)) {
            return false;
        }
        return throughWalls.get() || mc.player.hasLineOfSight(target);
    }

    /** Actual ray distance from the player's eyes to the nearest point of the target hitbox. */
    private static double distanceToHitboxSqr(LivingEntity target) {
        return target.getBoundingBox().distanceToSqr(mc.player.getEyePosition());
    }

    private static boolean canRun() {
        return mc.player != null && mc.level != null && mc.gameMode != null
                && mc.screen == null && mc.isWindowActive()
                && !mc.player.isDeadOrDying() && !mc.player.isSpectator();
    }

    @Override
    public String getDisplayName() {
        return Obf.s("FVMbfW1DHktt");
    }
}
