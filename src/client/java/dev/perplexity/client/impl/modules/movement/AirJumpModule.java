package dev.perplexity.client.impl.modules.movement;

import dev.perplexity.client.event.PlayerEvents;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.utils.Obf;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/** Lets each new jump-key press take Minecraft's normal ground-jump path while airborne. */
public final class AirJumpModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private final BooleanSetting onGroundPackets = add(new BooleanSetting("On Ground Packets",
            true));

    private boolean jumpDown;

    public AirJumpModule() {
        super(Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        jumpDown = mc.options.keyJump.isDown();
    }

    @Override
    protected void onDisable() {
        jumpDown = false;
    }

    /** Runs at the start of LocalPlayer.tick, matching the timing of a vanilla jump. */
    @Override
    protected void onPlayerTick(PlayerEvents.TickEvent event) {
        if (event.phase() != PlayerEvents.Phase.BEFORE) {
            return;
        }
        tryJump();
    }

    private void tryJump() {
        if (mc.player == null || mc.level == null) {
            jumpDown = false;
            return;
        }

        boolean pressed = mc.options.keyJump.isDown();
        if (pressed && !jumpDown && !mc.player.onGround()
                && !mc.player.isPassenger() && !mc.player.getAbilities().flying
                && !mc.player.isFallFlying()) {
            if (onGroundPackets.get() && mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true, false));
            }

            // Give jumpFromGround the same local state it has during a genuine ground jump. This
            // keeps vanilla jump power, effects, sprint impulse and movement hooks intact. Restore
            // airborne state immediately; collision/movement remains fully client-normal.
            mc.player.setOnGround(true);
            try {
                mc.player.jumpFromGround();
            } finally {
                mc.player.setOnGround(false);
            }
            mc.player.fallDistance = 0.0;
        }
        jumpDown = pressed;
    }

    @Override
    public String getDisplayName() {
        return Obf.s("H1MFMQd3Bkk=");
    }
}
