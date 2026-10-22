package dev.perplexity.client.impl.modules.combat;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.NumberSetting;
import dev.perplexity.client.utils.Obf;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Attacks the nearest valid living target inside the configured range. */
public final class KillAuraModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private final NumberSetting range = add(new NumberSetting("Range",
            3.5, 1.0, 6.0, 0.1));
    private final NumberSetting attacksPerSecond = add(new NumberSetting("Attacks Per Second",
            10.0, 1.0, 20.0, 1.0));
    private final BooleanSetting players = add(new BooleanSetting("Players", true));
    private final BooleanSetting mobs = add(new BooleanSetting("Mobs", false));
    private final BooleanSetting throughWalls = add(new BooleanSetting("Through Walls", false));
    private final BooleanSetting attackAllies = add(new BooleanSetting("Attack Allies", false));
    private final BooleanSetting swordOnly = add(new BooleanSetting("Sword Only", false));
    private final BooleanSetting autoCooldown = add(new BooleanSetting("Auto Cooldown", true));

    private long nextAttackAt;

    public KillAuraModule() {
        super(Category.COMBAT);
        attacksPerSecond.setVisibleWhen(() -> !autoCooldown.get());
    }

    @Override
    protected void onEnable() {
        nextAttackAt = 0L;
    }

    @Override
    protected void onDisable() {
        nextAttackAt = 0L;
    }

    @Override
    protected void onTick() {
        if (!canRun()) {
            return;
        }
        if (swordOnly.get() && !mc.player.getMainHandItem().is(ItemTags.SWORDS)) {
            return;
        }
        if (autoCooldown.get() && mc.player.getAttackStrengthScale(0.0F) < 0.9F) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextAttackAt) {
            return;
        }
        LivingEntity target = findTarget();
        if (target == null) {
            return;
        }

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        nextAttackAt = autoCooldown.get()
                ? now
                : now + Math.max(50L, Math.round(1000.0 / attacksPerSecond.get()));
    }

    private LivingEntity findTarget() {
        LivingEntity nearest = null;
        double nearestDistance = range.get() * range.get();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !validTarget(living)) {
                continue;
            }
            double distance = mc.player.distanceToSqr(living);
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
