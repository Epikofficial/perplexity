package dev.perplexity.client.impl.modules.combat;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.utils.Obf;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Sends the short airborne movement sequence required for an eligible critical attack. */
public final class CriticalsModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();
    private static CriticalsModule instance;

    private final BooleanSetting playersOnly = add(new BooleanSetting("Players Only", false));

    public CriticalsModule() {
        super(Category.COMBAT);
        instance = this;
    }

    public static void beforeAttack(Player attacker, Entity target) {
        if (instance == null || !instance.isEnabled() || attacker != mc.player
                || mc.getConnection() == null || target == null) {
            return;
        }
        if (instance.playersOnly.get() && !(target instanceof Player)) {
            return;
        }
        if (!attacker.onGround() || attacker.isInWater() || attacker.isInLava()
                || attacker.onClimbable() || attacker.isPassenger()
                || attacker.getAbilities().flying || attacker.isFallFlying()) {
            return;
        }

        double x = attacker.getX();
        double y = attacker.getY();
        double z = attacker.getZ();
        boolean horizontalCollision = attacker.horizontalCollision;
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                x, y + 0.0625, z, false, horizontalCollision));
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                x, y, z, false, horizontalCollision));
    }

    @Override
    public String getDisplayName() {
        return Obf.s("HUgeZSRhClV/");
    }
}
