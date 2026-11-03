package dev.perplexity.client.impl.modules.movement;

import dev.perplexity.client.event.PlayerEvents;
import dev.perplexity.client.event.RenderEvents;
import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.NumberSetting;
import dev.perplexity.client.module.setting.BooleanSetting;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;

/** Delays only outgoing movement updates; keepalives and other protocol packets stay live. */
public final class FakeLagModule extends Module {
    private static final int MAX_QUEUED_PACKETS = 256;
    private static FakeLagModule instance;

    private final NumberSetting delay = add(new NumberSetting("Delay (ms)", 150.0, 10.0, 1000.0, 10.0));
    private final BooleanSetting serverOutline = add(new BooleanSetting("Server Outline", true));
    private final ArrayDeque<DelayedMovement> queued = new ArrayDeque<>();
    private Connection connection;
    private boolean flushing;
    private volatile Vec3 sentPosition;
    private volatile float sentYaw;
    private volatile boolean hasBufferedMovement;

    public FakeLagModule() {
        super(Category.MOVEMENT);
        instance = this;
    }

    @Override
    public String getDisplayName() {
        return "Fake Lag";
    }

    @Override
    protected synchronized void onEnable() {
        queued.clear();
        connection = null;
        flushing = false;
        sentPosition = null;
        hasBufferedMovement = false;
    }

    @Override
    protected synchronized void onDisable() {
        Connection active = currentConnection();
        if (active != null && active == connection && active.isConnected()) {
            flushUntil(Long.MAX_VALUE);
        } else {
            queued.clear();
        }
        connection = null;
        sentPosition = null;
        hasBufferedMovement = false;
    }

    @Override
    protected synchronized void onPlayerTick(PlayerEvents.TickEvent event) {
        if (event.phase() != PlayerEvents.Phase.AFTER) {
            return;
        }
        Connection active = currentConnection();
        if (active == null || !active.isConnected() || active != connection) {
            queued.clear();
            connection = active;
            sentPosition = active != null && Minecraft.getInstance().player != null
                    ? Minecraft.getInstance().player.position() : null;
            sentYaw = Minecraft.getInstance().player == null ? 0.0F : Minecraft.getInstance().player.getYRot();
            hasBufferedMovement = false;
            return;
        }
        if (sentPosition == null && Minecraft.getInstance().player != null) {
            sentPosition = Minecraft.getInstance().player.position();
            sentYaw = Minecraft.getInstance().player.getYRot();
        }
        flushUntil(System.nanoTime());
    }

    /** Called at the network send boundary. Returns true when vanilla should skip this send. */
    public static boolean hold(Connection outgoing, Packet<?> packet) {
        return instance != null && instance.holdMovement(outgoing, packet);
    }

    private synchronized boolean holdMovement(Connection outgoing, Packet<?> packet) {
        if (!isEnabled() || flushing || !(packet instanceof ServerboundMovePlayerPacket)
                || Minecraft.getInstance().player == null
                || outgoing != currentConnection() || !outgoing.isConnected()) {
            return false;
        }
        if (connection != outgoing) {
            queued.clear();
            connection = outgoing;
            sentPosition = Minecraft.getInstance().player.position();
            sentYaw = Minecraft.getInstance().player.getYRot();
        }
        if (queued.size() >= MAX_QUEUED_PACKETS) {
            flushUntil(Long.MAX_VALUE);
        }
        long deadline = System.nanoTime() + (long) delay.get() * 1_000_000L;
        queued.addLast(new DelayedMovement(packet, deadline));
        hasBufferedMovement = true;
        return true;
    }

    private void flushUntil(long now) {
        if (connection == null || !connection.isConnected()) {
            queued.clear();
            hasBufferedMovement = false;
            return;
        }
        flushing = true;
        try {
            while (!queued.isEmpty() && queued.peekFirst().deadlineNanos <= now) {
                Packet<?> packet = queued.removeFirst().packet;
                connection.send(packet);
                if (packet instanceof ServerboundMovePlayerPacket movement && sentPosition != null) {
                    sentPosition = new Vec3(movement.getX(sentPosition.x), movement.getY(sentPosition.y),
                            movement.getZ(sentPosition.z));
                    sentYaw = movement.getYRot(sentYaw);
                }
            }
        } finally {
            flushing = false;
            hasBufferedMovement = !queued.isEmpty();
        }
    }

    @Override
    protected void onWorldRender(RenderEvents.WorldEvent event) {
        if (event.phase() == RenderEvents.WorldPhase.BEFORE_DEBUG_RENDER
                && event.context() instanceof WorldRenderContext context) {
            renderServerOutline(context);
        }
    }

    /** A client-side estimate: the latest position packet sent, not a server acknowledgement. */
    private void renderServerOutline(WorldRenderContext context) {
        if (!serverOutline.get() || !hasBufferedMovement
                || Minecraft.getInstance().player == null || Minecraft.getInstance().level == null
                || context.worldState().cameraRenderState == null
                || context.worldState().cameraRenderState.pos == null) {
            return;
        }
        Vec3 position = sentPosition;
        if (position == null) return;
        Vec3 camera = context.worldState().cameraRenderState.pos;
        VertexConsumer lines = context.consumers().getBuffer(RenderTypes.linesTranslucent());
        float yaw = sentYaw;

        // A player-shaped wireframe, not a duplicate entity or a hitbox modification.
        box(lines, position, camera, yaw, -0.25, 1.38, -0.25, 0.25, 1.88, 0.25, 105, 235, 255, 215); // head
        box(lines, position, camera, yaw, -0.31, 0.76, -0.16, 0.31, 1.43, 0.16, 95, 190, 255, 190); // torso
        box(lines, position, camera, yaw, -0.53, 0.75, -0.13, -0.32, 1.41, 0.13, 168, 115, 255, 170); // left arm
        box(lines, position, camera, yaw, 0.32, 0.75, -0.13, 0.53, 1.41, 0.13, 168, 115, 255, 170); // right arm
        box(lines, position, camera, yaw, -0.29, 0.02, -0.14, -0.02, 0.76, 0.14, 80, 200, 255, 170); // left leg
        box(lines, position, camera, yaw, 0.02, 0.02, -0.14, 0.29, 0.76, 0.14, 80, 200, 255, 170); // right leg

        // Low-cost ground marker makes the held-back position readable at a glance.
        for (int segment = 0; segment < 16; segment++) {
            double first = segment * Math.PI / 8.0;
            double second = (segment + 1) * Math.PI / 8.0;
            line(lines, position, camera, 0.0F,
                    Math.cos(first) * 0.5, 0.025, Math.sin(first) * 0.5,
                    Math.cos(second) * 0.5, 0.025, Math.sin(second) * 0.5,
                    105, 235, 255, 135);
        }
    }

    private static void box(VertexConsumer lines, Vec3 position, Vec3 camera, float yaw,
                            double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                            int red, int green, int blue, int alpha) {
        double[][] corners = {
                {minX, minY, minZ}, {maxX, minY, minZ}, {maxX, minY, maxZ}, {minX, minY, maxZ},
                {minX, maxY, minZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}, {minX, maxY, maxZ}
        };
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            double[] a = corners[edge[0]];
            double[] b = corners[edge[1]];
            line(lines, position, camera, yaw, a[0], a[1], a[2], b[0], b[1], b[2], red, green, blue, alpha);
        }
    }

    private static void line(VertexConsumer lines, Vec3 position, Vec3 camera, float yaw,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             int red, int green, int blue, int alpha) {
        double sin = Math.sin(Math.toRadians(yaw));
        double cos = Math.cos(Math.toRadians(yaw));
        float ax = (float) (position.x + x1 * cos - z1 * sin - camera.x);
        float ay = (float) (position.y + y1 - camera.y);
        float az = (float) (position.z + x1 * sin + z1 * cos - camera.z);
        float bx = (float) (position.x + x2 * cos - z2 * sin - camera.x);
        float by = (float) (position.y + y2 - camera.y);
        float bz = (float) (position.z + x2 * sin + z2 * cos - camera.z);
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0.0F) return;
        lines.addVertex(ax, ay, az).setColor(red, green, blue, alpha)
                .setNormal(dx / length, dy / length, dz / length).setLineWidth(2.0F);
        lines.addVertex(bx, by, bz).setColor(red, green, blue, alpha)
                .setNormal(dx / length, dy / length, dz / length).setLineWidth(2.0F);
    }

    private static Connection currentConnection() {
        var listener = Minecraft.getInstance().getConnection();
        return listener == null ? null : listener.getConnection();
    }

    private record DelayedMovement(Packet<?> packet, long deadlineNanos) {}
}
