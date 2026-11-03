package dev.perplexity.client.utils.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.util.Mth;

import java.util.Objects;
import java.util.Optional;

public final class RotationManager {
    private static Request activeRequest;
    private static RotationTarget returnTarget;
    private static Rotation currentRotation;
    private static Rotation previousRotation;
    private static Rotation playerRotation;
    private static Rotation serverRotation = Rotation.ZERO;
    private static boolean serverRotationInitialized;
    private static Rotation actionRotation;
    private static RotationTarget actionTarget;
    private static LocalPlayer actionPlayer;
    private static Object actionLevel;
    private static int actionTick;
    private static Rotation tickStartRotation;
    private static LocalPlayer steppedPlayer;
    private static int steppedTick;

    public static synchronized boolean request(Object owner, RotationTarget target) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(target, "target");

        if (hasActionHold()) return false;

        if (activeRequest != null
            && activeRequest.owner != owner
            && activeRequest.target.priority().value() > target.priority().value()) {
            return false;
        }

        activeRequest = new Request(owner, target, target.ticksUntilReset());
        returnTarget = null;
        return true;
    }

    public static synchronized boolean request(Object owner, Rotation rotation) {
        return request(owner, RotationTarget.to(rotation).build());
    }

    public static synchronized void clear(Object owner) {
        if (activeRequest != null && activeRequest.owner == owner) beginReset(activeRequest.target);
    }

    public static synchronized void reset() {
        clearActionHold();
        tickStartRotation = null;
        steppedPlayer = null;
        activeRequest = null;
        returnTarget = null;
        currentRotation = null;
        previousRotation = null;
        playerRotation = null;
        serverRotation = Rotation.ZERO;
        serverRotationInitialized = false;
    }

    public static synchronized void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            reset();
            return;
        }
        if (hasActionHold()) return;

        Rotation viewRotation = new Rotation(player.getYRot(), player.getXRot()).normalized();
        if (!serverRotationInitialized) {
            serverRotation = viewRotation;
            serverRotationInitialized = true;
        }
        Rotation continuousViewRotation = viewRotation.nearestTo(serverRotation);
        playerRotation = continuousViewRotation;

        previousRotation = currentRotation;
        tickStartRotation = currentRotation == null ? serverRotation : currentRotation;
        steppedPlayer = player;
        steppedTick = player.tickCount;

        if (activeRequest != null) {
            RotationTarget target = activeRequest.target;
            currentRotation = step(currentRotation == null ? serverRotation : currentRotation, target.rotation(), target);

            activeRequest.ticksRemaining--;
            if (activeRequest.ticksRemaining <= 0) beginReset(target);
            return;
        }

        if (currentRotation != null && returnTarget != null) {
            currentRotation = step(currentRotation, continuousViewRotation, returnTarget);

            if (currentRotation.distanceTo(continuousViewRotation) <= returnTarget.resetThreshold()) {
                currentRotation = null;
                previousRotation = null;
                returnTarget = null;
            }
        }
    }

    public static synchronized Optional<Rotation> packetRotation() {
        if (hasActionHold()) return Optional.of(actionRotation);
        if (currentRotation == null) return Optional.empty();

        RotationMode mode = mode();
        if (mode != RotationMode.SILENT && mode != RotationMode.CHANGE_LOOK) return Optional.empty();

        return Optional.of(serverRotationInitialized ? currentRotation.nearestTo(serverRotation) : currentRotation);
    }

    /** Visual-only aim for the local player model; never changes the camera rotation. */
    public static synchronized Optional<Rotation> silentModelRotation(float partialTick) {
        if (hasActionHold()) {
            return actionTarget.mode() == RotationMode.SILENT
                ? Optional.of(actionRotation) : Optional.empty();
        }
        if (currentRotation == null || mode() != RotationMode.SILENT) return Optional.empty();
        Rotation from = previousRotation == null ? currentRotation : previousRotation;
        return Optional.of(from.interpolate(currentRotation, partialTick, partialTick));
    }

    public static synchronized float packetYaw(float vanillaYaw) {
        Rotation rotation = packetRotation().orElse(new Rotation(vanillaYaw, 0f));
        return serverRotationInitialized ? rotation.nearestTo(serverRotation).yaw() : rotation.yaw();
    }

    public static synchronized float packetPitch(float vanillaPitch) {
        return packetRotation()
            .map(Rotation::pitch)
            .orElse(Mth.clamp(vanillaPitch, -90f, 90f));
    }

    public static synchronized Rotation currentRotation() {
        return currentRotation;
    }

    public static synchronized Rotation previousRotation() {
        return previousRotation;
    }

    public static synchronized Rotation playerRotation() {
        return playerRotation;
    }

    public static synchronized Rotation serverRotation() {
        return serverRotation;
    }

    public static synchronized Rotation targetingRotation() {
        return currentRotation == null ? RotationUtils.playerRotation() : currentRotation;
    }

    public static synchronized RotationTarget activeTarget() {
        return activeRequest == null ? null : activeRequest.target;
    }

    public static synchronized boolean ownsActiveRequest(Object owner) {
        return activeRequest != null && activeRequest.owner == owner;
    }

    public static synchronized boolean isRotating() {
        return currentRotation != null;
    }

    public static synchronized RotationMode mode() {
        if (hasActionHold()) return actionTarget.mode();
        if (activeRequest != null) return activeRequest.target.mode();
        return returnTarget == null ? RotationMode.SILENT : returnTarget.mode();
    }

    public static synchronized MovementCorrection movementCorrection() {
        if (hasActionHold()) return actionTarget.movementCorrection();
        if (activeRequest != null) return activeRequest.target.movementCorrection();
        return returnTarget == null ? MovementCorrection.OFF : returnTarget.movementCorrection();
    }

    public static synchronized float movementYaw(float playerYaw) {
        MovementCorrection correction = movementCorrection();
        if (hasActionHold() && correction != MovementCorrection.OFF) return actionRotation.yaw();
        return currentRotation != null && correction != MovementCorrection.OFF ? currentRotation.yaw() : playerYaw;
    }

    public static synchronized Input correctKeyboardInput(Input input) {
        LocalPlayer player = Minecraft.getInstance().player;
        MovementCorrection correction = movementCorrection();
        if (player == null || player.isPassenger()
                || (correction != MovementCorrection.STRICT && correction != MovementCorrection.SILENT)) return input;
        // Use the live camera yaw, including mouse turns since START_CLIENT_TICK. movementYaw
        // also accounts for a held action rotation and the return to the normal camera angle.
        return RotationUtils.correctKeyboardInput(input, player.getYRot(), movementYaw(player.getYRot()));
    }

    public static synchronized void handleMouseTurn(double cursorDeltaX, double cursorDeltaY) {
        if (hasActionHold()) return;
        RotationTarget target = activeRequest != null ? activeRequest.target : returnTarget;
        if (target == null || target.movementCorrection() != MovementCorrection.CHANGE_LOOK) return;

        if (playerRotation != null) {
            playerRotation = RotationUtils.applyMouseTurnDelta(playerRotation, cursorDeltaX, cursorDeltaY);
        }

        if (currentRotation != null) {
            currentRotation = RotationUtils.applyMouseTurnDelta(currentRotation, cursorDeltaX, cursorDeltaY);
        }
    }

    public static synchronized void recordMovementPacket(float vanillaYaw, float vanillaPitch) {
        Rotation fallback = new Rotation(vanillaYaw, vanillaPitch).normalized();
        if (serverRotationInitialized) fallback = fallback.nearestTo(serverRotation);

        Rotation sent = packetRotation().orElse(fallback);
        serverRotation = sent.normalized();
        serverRotationInitialized = true;
        clearActionHold();
    }

    /** Keep a validated input-phase look unchanged through its normal movement packet. */
    public static synchronized boolean holdForAction(Object owner, RotationTarget expectedTarget, Rotation expectedRotation) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || activeRequest == null
                || activeRequest.owner != owner || activeRequest.target != expectedTarget
                || expectedRotation == null || !packetRotation().filter(expectedRotation::equals).isPresent()) return false;
        actionRotation = expectedRotation;
        actionTarget = expectedTarget;
        actionPlayer = client.player;
        actionLevel = client.level;
        actionTick = client.player.tickCount;
        return true;
    }

    /** Preview the exact input-phase look without changing rotation ownership or its baseline. */
    public static synchronized Rotation previewForAction(Object owner, RotationTarget expectedTarget, RotationTarget replacement) {
        Minecraft client = Minecraft.getInstance();
        if (actionHoldActive(client) || client.player == null || steppedPlayer != client.player
                || steppedTick != client.player.tickCount || tickStartRotation == null
                || activeRequest == null || activeRequest.owner != owner || activeRequest.target != expectedTarget
                || replacement == null) return null;
        return step(tickStartRotation, replacement.rotation(), replacement);
    }

    /** Re-aim from this tick's original baseline without applying the turn limit twice. */
    public static synchronized boolean retargetForAction(Object owner, RotationTarget expectedTarget, RotationTarget replacement) {
        Rotation preview = previewForAction(owner, expectedTarget, replacement);
        if (preview == null) return false;
        if (!request(owner, replacement)) return false;
        currentRotation = preview;
        return true;
    }

    private static boolean hasActionHold() {
        if (actionRotation == null) return false;
        if (!actionHoldActive(Minecraft.getInstance())) {
            clearActionHold();
            return false;
        }
        return true;
    }

    private static boolean actionHoldActive(Minecraft client) {
        return actionRotation != null && actionPlayer != null && client.player == actionPlayer && client.level == actionLevel
                && actionPlayer.tickCount >= actionTick && actionPlayer.tickCount <= actionTick + 1;
    }

    private static void clearActionHold() {
        actionRotation = null;
        actionTarget = null;
        actionPlayer = null;
        actionLevel = null;
    }

    public static synchronized void applyChangeLookRotation(float partialTicks) {
        RotationTarget target = activeRequest != null ? activeRequest.target : returnTarget;
        if (target == null || currentRotation == null || playerRotation == null) return;
        if (target.mode() != RotationMode.CHANGE_LOOK
            && target.movementCorrection() != MovementCorrection.CHANGE_LOOK) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Rotation desired = target.lookRotation() != null ? target.lookRotation().at(partialTicks) : currentRotation;
        Rotation interpolated = target.instantLook()
            ? desired.nearestTo(playerRotation)
            : playerRotation.interpolate(desired, partialTicks, partialTicks);
        // Keep the renderer's previous-view state in step with the per-frame Change Look
        // update. This is the same order LiquidBounce uses in LocalPlayer.setRotation.
        // Without it, the renderer can interpolate from a stale tick rotation to this frame.
        Rotation normalized = interpolated.normalized();
        float previousYaw = player.getYRot();
        player.xRotO = player.getXRot();
        player.yRotO = previousYaw;
        player.yBob = previousYaw;
        player.yBobO = previousYaw;
        player.setYRot(normalized.yaw());
        player.setXRot(normalized.pitch());
    }

    public static synchronized void acceptServerRotation(float yaw, float pitch, boolean relativeYaw, boolean relativePitch) {
        clearActionHold();
        float resolvedYaw = yaw + (relativeYaw ? serverRotation.yaw() : 0f);
        float resolvedPitch = pitch + (relativePitch ? serverRotation.pitch() : 0f);
        Rotation resolved = new Rotation(resolvedYaw, Mth.clamp(Mth.wrapDegrees(resolvedPitch), -90f, 90f));
        serverRotation = serverRotationInitialized ? resolved.nearestTo(serverRotation) : resolved.normalized();
        serverRotationInitialized = true;
    }

    private static Rotation step(Rotation from, Rotation to, RotationTarget target) {
        Rotation next = target.smoother().smooth(from, to.nearestTo(from)).normalized();
        return target.sensitivityFix() ? RotationUtils.snapToSensitivity(next, from) : next;
    }

    private static void beginReset(RotationTarget target) {
        activeRequest = null;
        returnTarget = target;
    }

    private static final class Request {
        private final Object owner;
        private final RotationTarget target;
        private int ticksRemaining;

        private Request(Object owner, RotationTarget target, int ticksRemaining) {
            this.owner = owner;
            this.target = target;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private RotationManager() {
    }
}
