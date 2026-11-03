package dev.perplexity.client.impl.modules.combat;
import dev.perplexity.client.utils.Obf;

import dev.perplexity.client.event.PlayerEvents;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.NumberSetting;
import dev.perplexity.client.module.setting.RangeSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashSet;
import java.util.Set;

/**
 * Automatically charges and detonates a respawn anchor the player is looking at. The macro never
 * moves the camera: it waits for the player's crosshair to rest on the anchor and then drives
 * glowstone use followed by the configured detonation slot.
 *
 * <p>Ported from Alpine's anchor macro. The cross-module arbitration that Alpine's
 * combat stack shares (Safe Anchor, totem, wind charge, world-GUI ownership) has no counterpart in
 * this client, so those guards are removed; the sequence, crosshair hold gate, retry accounting and
 * manual-placement tracking are kept intact.</p>
 */
public final class AnchorHelperModule extends Module {
    private static final Minecraft mc = Minecraft.getInstance();

    private final Set<BlockPos> ownAnchors = new HashSet<>();

    private final RangeSetting delay = add(new RangeSetting("Delay",
            0.0, 20.0, 0.0, 200.0, 1.0));
    private final NumberSetting switchSlot = add(new NumberSetting("Switch Slot",
            1.0, 1.0, 9.0, 1.0));
    private final BooleanSetting onlyOwn = add(new BooleanSetting("Only Own Anchors",
            true));

    private static boolean sequenceActive;
    private static AnchorHelperModule instance;

    private int progress;
    private BlockPos anchorPos;
    private BlockPos pendingAnchor;
    private long cooldownUntil;
    private int retries;
    private long stepStartTime;
    private int delayMs;
    private int lastInputTick = Integer.MIN_VALUE;
    private int pendingTick;
    private LocalPlayer sequencePlayer;
    private ClientLevel sequenceWorld;
    private int lastActionTick = Integer.MIN_VALUE;
    private LocalPlayer actionPlayer;
    private ClientLevel actionWorld;

    /** True while the macro is driving, or has just driven, this tick's interactions. */
    public static boolean ownsCombatInput() {
        return instance != null && instance.isEnabled() && (sequenceActive
                || (mc.player != null && instance.actionPlayer == mc.player && instance.actionWorld == mc.level
                    && instance.lastActionTick == mc.player.tickCount));
    }

    public AnchorHelperModule() {
        super(Category.COMBAT);
        instance = this;
    }

    @Override
    protected void onEnable() {
        ownAnchors.clear();
        progress = 0;
        anchorPos = null;
        pendingAnchor = null;
        cooldownUntil = 0;
        retries = 0;
        stepStartTime = 0;
        delayMs = 0;
        sequenceActive = false;
        lastInputTick = Integer.MIN_VALUE;
        lastActionTick = Integer.MIN_VALUE;
        actionPlayer = null;
        actionWorld = null;
        sequencePlayer = null;
        sequenceWorld = null;
    }

    @Override
    protected void onDisable() {
        resetSequence();
        actionPlayer = null;
        actionWorld = null;
    }

    private void resetSequence() {
        progress = 0;
        anchorPos = null;
        pendingAnchor = null;
        sequenceActive = false;
        retries = 0;
        sequencePlayer = null;
        sequenceWorld = null;
    }

    @Override
    protected void onPlayerTick(PlayerEvents.TickEvent event) {
        if (event.phase() != PlayerEvents.Phase.AFTER) {
            return;
        }
        // Lifecycle cleanup only: actions run before vanilla input, never after a use packet.
        if (changedContext()) {
            ownAnchors.clear();
            resetSequence();
            return;
        }
        if (progress == 0) {
            return;
        }
        if (anchorPos == null || mc.level == null
                || !mc.level.getBlockState(anchorPos).is(Blocks.RESPAWN_ANCHOR)) {
            resetSequence();
            return;
        }
        // A held sequence (crosshair off the anchor) survives transient conditions here — GUIs,
        // focus loss, totem swaps — because it owns no input while holding. Only context or target
        // loss ends it.
        if (isHoldingForCrosshair()) {
            return;
        }
        if (!canRun()) {
            resetSequence();
        }
    }

    public static void onHandleInputEarly() {
        if (instance != null && instance.isEnabled()) {
            instance.handleInput();
        }
    }

    private boolean changedContext() {
        return sequencePlayer != null && (sequencePlayer != mc.player || sequenceWorld != mc.level);
    }

    private boolean canRun() {
        return isEnabled() && mc.player != null && mc.level != null && mc.gameMode != null
                && mc.screen == null && mc.isWindowActive()
                && !mc.player.isDeadOrDying() && !mc.player.isSpectator();
    }

    /** Snapshot before BlockItem consumes the stack (including the last anchor). */
    public static BlockPos placementCandidate(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        if (instance == null || !instance.canRun() || player != mc.player || sequenceActive
                || !player.getItemInHand(hand).is(Items.RESPAWN_ANCHOR)) {
            return null;
        }
        BlockPlaceContext context = new BlockPlaceContext(player, hand, player.getItemInHand(hand), hit);
        if (!context.canPlace()) {
            return null;
        }
        BlockPos pos = context.getClickedPos();
        return mc.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR) ? null : pos.immutable();
    }

    /** Called only after an accepted vanilla block interaction; never places anything itself. */
    public static void onAnchorPlaced(LocalPlayer player, BlockPos pos) {
        if (instance == null || pos == null || player != mc.player || !instance.canRun()) {
            return;
        }
        // A fresh manual placement supersedes a held sequence that was only waiting for its old
        // anchor — the player has moved on to a new target.
        if (instance.progress != 0 && !sequenceActive) {
            instance.resetSequence();
        }
        if (instance.ownAnchors.size() >= 256) {
            instance.ownAnchors.clear();
        }
        instance.ownAnchors.add(pos);
        instance.pendingAnchor = pos;
        instance.pendingTick = player.tickCount;
        instance.sequencePlayer = player;
        instance.sequenceWorld = mc.level;
    }

    private void handleInput() {
        if (changedContext()) {
            ownAnchors.clear();
            resetSequence();
            return;
        }
        // Crosshair left the anchor mid-sequence: hold instead of resetting. The hold owns no input,
        // so the player keeps full control while away; the sequence resumes the moment the crosshair
        // returns to the anchor.
        if (isHoldingForCrosshair()) {
            if (mc.level == null
                    || !mc.level.getBlockState(anchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                resetSequence();
                return;
            }
            sequenceActive = false;
            // Re-arm the resume timer every hold tick: looking back only continues after ~100ms of
            // continuous aim, so a flick across the anchor never fires a stray charge/detonate.
            delayMs = 100;
            stepStartTime = System.currentTimeMillis();
            return;
        }
        if (!canRun()) {
            resetSequence();
            return;
        }
        if (lastInputTick == mc.player.tickCount) {
            return;
        }
        lastInputTick = mc.player.tickCount;
        if (isShieldOrFoodActive()) {
            resetSequence();
            return;
        }

        if (progress == 0) {
            if (System.currentTimeMillis() < cooldownUntil) {
                return;
            }
            sequenceActive = false;
            if (!tryProcessPendingAnchor()) {
                detectAnchorPlacement();
            }
        }
        if (progress != 0) {
            if (!mc.level.getBlockState(anchorPos).is(Blocks.RESPAWN_ANCHOR)) {
                resetSequence();
                return;
            }
            sequencePlayer = mc.player;
            sequenceWorld = mc.level;
            sequenceActive = true;
            if (hasElapsed(delayMs)) {
                lastActionTick = mc.player.tickCount;
                actionPlayer = mc.player;
                actionWorld = mc.level;
                try {
                    runSequence();
                } catch (RuntimeException failedInteraction) {
                    resetSequence();
                }
            }
        }
    }

    /** Crosshair-gated steps (charge/detonate) hold while the anchor is not under the crosshair. */
    private boolean isHoldingForCrosshair() {
        if (progress != 2 && progress != 4) {
            return false;
        }
        if (mc.player == null || mc.level == null) {
            return false;
        }
        return !isLookingAtAnchor();
    }

    private boolean isShieldOrFoodActive() {
        if (!mc.player.isUsingItem()) {
            return false;
        }
        var active = mc.player.getActiveItem();
        return active.getItem() instanceof ShieldItem || active.has(DataComponents.FOOD);
    }

    private void detectAnchorPlacement() {
        if (isShieldOrFoodActive()) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult blockHitResult)) {
            return;
        }

        BlockPos targetPos = blockHitResult.getBlockPos();
        BlockState targetState = mc.level.getBlockState(targetPos);
        if (targetState.isAir()) {
            return;
        }

        Direction side = blockHitResult.getDirection();
        BlockPos placePos = targetState.getBlock() == Blocks.RESPAWN_ANCHOR
                ? targetPos
                : targetPos.relative(side);

        BlockState placeState = mc.level.getBlockState(placePos);
        if (placeState.getBlock() != Blocks.RESPAWN_ANCHOR) {
            return;
        }
        if (placeState.hasProperty(BlockStateProperties.RESPAWN_ANCHOR_CHARGES)
                && placeState.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES) > 0) {
            // Already charged — skip straight to the explode phase (switch to the explode slot, then
            // detonate). Only pop anchors the macro placed itself ("your" anchors), never arbitrary
            // charged anchors lying around.
            if (!ownAnchors.contains(placePos)) {
                return;
            }
            if (placePos.equals(anchorPos) || placePos.equals(pendingAnchor)) {
                return;
            }
            anchorPos = placePos;
            pendingAnchor = null;
            progress = 3;
            setRandomDelay();
            stepStartTime = 0;
            return;
        }

        // Own placement is recorded before consumption, not inferred from whatever is held now.
        if (onlyOwn.get() && !ownAnchors.contains(placePos)) {
            return;
        }
        if (placePos.equals(anchorPos) || placePos.equals(pendingAnchor)) {
            return;
        }

        anchorPos = placePos;
        pendingAnchor = null;
        progress = 1;
        setRandomDelay();
        stepStartTime = 0;
    }

    private void runSequence() {
        switch (progress) {
            case 1 -> {
                int glowSlot = findItemInHotbar(Items.GLOWSTONE);
                if (glowSlot != -1) {
                    mc.player.getInventory().setSelectedSlot(glowSlot);
                    increaseProgress();
                } else {
                    resetSequence();
                }
            }
            case 2 -> {
                if (isLookingAtAnchor() && mc.hitResult instanceof BlockHitResult blockHitResult) {
                    // Re-assert glowstone even if the player scrolled away and back mid-sequence, so
                    // the charge lands on the right item.
                    if (!mc.player.getMainHandItem().is(Items.GLOWSTONE)) {
                        int slot = findItemInHotbar(Items.GLOWSTONE);
                        if (slot != -1) {
                            mc.player.getInventory().setSelectedSlot(slot);
                        } else {
                            resetSequence();
                            return;
                        }
                    }
                    BlockState state = mc.level.getBlockState(blockHitResult.getBlockPos());
                    if (state.getBlock() == Blocks.RESPAWN_ANCHOR
                            && state.hasProperty(BlockStateProperties.RESPAWN_ANCHOR_CHARGES)
                            && state.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES) == 0) {
                        if (!interact(blockHitResult)) {
                            retryWait();
                            return;
                        }
                    }
                    retries = 0;
                    increaseProgress();
                } else {
                    // Defensive: the hold gate in handleInput() normally catches this first.
                    retryWait();
                }
            }
            case 3 -> {
                mc.player.getInventory().setSelectedSlot((int) switchSlot.get() - 1);
                increaseProgress();
            }
            case 4 -> {
                if (isLookingAtAnchor() && mc.hitResult instanceof BlockHitResult blockHitResult) {
                    int expectedSlot = (int) switchSlot.get() - 1;
                    if (mc.player.getInventory().getSelectedSlot() != expectedSlot) {
                        mc.player.getInventory().setSelectedSlot(expectedSlot);
                    }
                    BlockState state = mc.level.getBlockState(blockHitResult.getBlockPos());
                    if (state.getBlock() == Blocks.RESPAWN_ANCHOR
                            && state.hasProperty(BlockStateProperties.RESPAWN_ANCHOR_CHARGES)
                            && state.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES) != 0) {
                        if (!interact(blockHitResult)) {
                            retryWait();
                            return;
                        }
                    } else {
                        // Charges still read 0 — the charge packet was likely dropped by the server.
                        // Wait one tick for prediction to catch up; if it's still uncharged, reset so
                        // the macro cleanly re-acquires (re-charge, then detonate) instead of idling
                        // on a dead sequence.
                        if (retries >= 2) {
                            resetSequence();
                            return;
                        }
                        retryWait();
                        return;
                    }
                    retries = 0;
                    resetSequence();
                    cooldownUntil = System.currentTimeMillis() + 50;
                } else {
                    // Defensive: the hold gate in handleInput() normally catches this first.
                    retryWait();
                }
            }
            default -> resetSequence();
        }
    }

    private void increaseProgress() {
        setRandomDelay();
        stepStartTime = System.currentTimeMillis();
        progress += 1;
    }

    private boolean interact(BlockHitResult hit) {
        // The real crosshair already hits this anchor; do not mutate camera angles.
        if (!mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit).consumesAction()) {
            return false;
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private boolean isLookingAtAnchor() {
        if (!(mc.hitResult instanceof BlockHitResult blockHitResult)) {
            return false;
        }
        return blockHitResult.getBlockPos().equals(anchorPos)
                && mc.level.getBlockState(blockHitResult.getBlockPos()).getBlock() == Blocks.RESPAWN_ANCHOR;
    }

    private boolean tryProcessPendingAnchor() {
        if (pendingAnchor == null) {
            return false;
        }
        // A manual placement must be followed by the ordinary movement boundary before changing
        // slots. Wait briefly for prediction/server confirmation.
        int age = mc.player.tickCount - pendingTick;
        if (age == 0) {
            return true;
        }
        if (age < 0 || age > 8) {
            pendingAnchor = null;
            return false;
        }
        BlockState state = mc.level.getBlockState(pendingAnchor);
        if (state.getBlock() == Blocks.RESPAWN_ANCHOR
                && state.hasProperty(BlockStateProperties.RESPAWN_ANCHOR_CHARGES)) {
            anchorPos = pendingAnchor;
            pendingAnchor = null;
            progress = state.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES) > 0 ? 3 : 1;
            setRandomDelay();
            stepStartTime = 0;
            return true;
        }
        return true;
    }

    /**
     * A concrete action was rejected (server lag, prediction mismatch). Wait out the configured
     * delay and retry the same step; only give up after several consecutive rejections. If the anchor
     * disappears from the world or the crosshair moves away, the hold/target checks release the
     * sequence instead, so retries never outlive a stale target.
     */
    private void retryWait() {
        retries++;
        if (retries > 5) {
            resetSequence();
        } else {
            setRandomDelay();
        }
    }

    private void setRandomDelay() {
        delayMs = (int) delay.random();
        stepStartTime = System.currentTimeMillis();
    }

    private boolean hasElapsed(int ms) {
        return System.currentTimeMillis() - stepStartTime >= ms;
    }

    private int findItemInHotbar(net.minecraft.world.item.Item item) {
        if (mc.player == null) {
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == item) {
                return i;
            }
        }
        return -1;
    }
    @Override
    public String getDisplayName() {
        return Obf.s("H1QUeSJwS3RtNl0e");
    }

}
