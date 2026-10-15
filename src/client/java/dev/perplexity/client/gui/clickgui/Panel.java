package dev.perplexity.client.gui.clickgui;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.module.Module;
import dev.perplexity.client.module.setting.BooleanSetting;
import dev.perplexity.client.module.setting.DropdownSetting;
import dev.perplexity.client.module.setting.KeybindSetting;
import dev.perplexity.client.module.setting.ToggleListSetting;
import dev.perplexity.client.module.setting.ModeSetting;
import dev.perplexity.client.module.setting.NumberSetting;
import dev.perplexity.client.module.setting.RangeSetting;
import dev.perplexity.client.module.setting.Setting;
import dev.perplexity.client.render.vg.VGFont;
import dev.perplexity.client.render.vg.VGRenderer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single glass panel for one {@link Category}: one translucent surface with a slightly rounded
 * corner, a semibold header in the category's own colour, module names as plain text that turn
 * accent-coloured when enabled, and indented settings rows underneath.
 */
public final class Panel {
    private static final int TYPE_MODULE = 0;
    private static final int TYPE_BOOL = 1;
    private static final int TYPE_NUMBER = 2;
    private static final int TYPE_MODE = 3;
    private static final int TYPE_RANGE = 4;
    private static final int TYPE_DROPDOWN = 5;
    private static final int TYPE_KEYBIND = 6;
    private static final int TYPE_TOGGLE_LIST = 7;

    /** Clickable width of the edit-menu ellipsis at a panel header's right edge. */
    private static final float EDIT_BUTTON_WIDTH = 24.0F;
    private static final float SHAKE_DURATION = 0.30F;
    private static final float SHAKE_AMPLITUDE = 2.4F;

    /** Distance a settings row travels while it slides into place, in GUI units. */
    private static final float SETTINGS_SLIDE = 9.0F;
    /** Extra distance the whole content block drifts while a panel collapses. */
    private static final float COLLAPSE_SLIDE = 8.0F;

    // Per-frame easing factors: fraction of the remaining distance covered each frame.
    private static final float EXPAND_SPEED = 0.26F;
    private static final float HOVER_SPEED = 0.35F;
    private static final float ENABLE_SPEED = 0.30F;
    private static final float COLLAPSE_SPEED = 0.22F;
    private static final float SLIDER_SPEED = 0.45F;
    private static final float TOGGLE_SPEED = 0.35F;
    /** Tracking applied to the category name so it reads as a header without a rule under it. */
    private static final float HEADER_TRACKING = 0.45F;
    /** Extra delay before each successive settings row starts its slide, as a fraction of expand. */
    private static final float STAGGER = 0.12F;

    // Toggle ripple: an accent bloom that spreads out from the click point when a module is switched
    // on (and drains back into it when switched off), then settles into the steady enabled wash.
    private static final float RIPPLE_DURATION = 0.50F;
    /** Peak alpha of the crest: the ring that travels out of the click point. */
    private static final float RIPPLE_ALPHA = 0.60F;
    /** Peak alpha of the row-wide lift, which is what makes the toggle read at a glance. */
    private static final float RIPPLE_FLASH = 0.24F;
    /** Fraction of the crest's alpha carried by the filled disc trailing behind it. */
    private static final float RIPPLE_WASH = 0.45F;
    /** Width of the leading ring, tapering as it spreads so the crest thins out over the distance. */
    private static final float RIPPLE_STROKE = 4.5F;
    /**
     * How far past the row's far corner the crest travels. Beyond the row only the trailing wash and
     * the row-wide lift show, which is what makes the wave feel like it passes through rather than
     * stopping dead at the edge.
     */
    private static final float RIPPLE_REACH = 1.35F;

    /**
     * How far the settings container's edge reaches up while its module is open. This is the full
     * module row, so a fully open container's top edge lands exactly on the row's own top edge — the
     * same place the row's highlight starts, with the same corner radius — and the two read as one
     * shape instead of the highlight poking out above the outline.
     */
    private static final float MODULE_OUTLINE_REACH = GlassTheme.MODULE_HEIGHT;

    // Drag sway: a spring on the panel's tilt, driven by horizontal drag speed and aimed the same way
    // as the drag, so a panel dragged right tips its top to the right and swings upright when released.
    /** Tilt per unit of smoothed drag speed, and the cap it is clamped to. */
    private static final float SWAY_PER_UNIT = 1.1F;
    private static final float SWAY_LIMIT = 10.0F;

    // Both stages are time-based rather than per-frame, so the motion is identical at any framerate.
    /** Seconds for the drag input to catch up to the panel's real speed. */
    private static final float SWAY_INPUT_TAU = 0.09F;
    /** Seconds for the tilt to reach the input; sets how long the release takes to settle. */
    private static final float SWAY_FOLLOW_TAU = 0.16F;
    /**
     * Damping ratio. 1.0 approaches the target without ever passing it, which reads as dead; below
     * that it eases slightly past and swings back, which is the sway. The shudder was never caused by
     * this — it came from sampling the drag per input event, which the per-frame read above fixes —
     * so a little under-damping buys the character back without the jitter.
     */
    private static final float SWAY_DAMPING_RATIO = 0.55F;
    /** Longest physics step. Substeps keep a slow frame from overshooting the spring. */
    private static final float SWAY_MAX_STEP = 1.0F / 120.0F;
    /** Longest frame the sway will integrate, so a pause cannot fling the panel. */
    private static final float SWAY_MAX_FRAME = 0.05F;

    private final Category category;
    private final List<Module> modules;

    private float x;
    private float y;
    private float width = GlassTheme.PANEL_WIDTH;

    private boolean collapsed;
    private boolean editMode;
    /** Set once the user drags the panel somewhere, so the grid stops managing its position. */
    private boolean placed;
    /** Set while the current press is actually relocating the panel, so a plain click does not pin it. */
    private boolean moved;
    private float scroll;

    private final List<Row> rows = new ArrayList<>();
    private final Map<Module, Float> expandAnimations = new IdentityHashMap<>();
    private final Map<Module, Float> hoverAnimations = new IdentityHashMap<>();
    private final Map<Module, Float> enabledAnimations = new IdentityHashMap<>();
    private final Map<NumberSetting, Float> sliderAnimations = new IdentityHashMap<>();
    private final Map<BooleanSetting, Float> toggleAnimations = new IdentityHashMap<>();
    private final Map<DropdownSetting, Float> dropdownAnimations = new IdentityHashMap<>();
    private final Map<Module, Float> shakeAnimations = new IdentityHashMap<>();

    private float sway;
    private float swayVelocity;
    private float dragSway;
    private float swaySampleX;
    private long lastFrameNanos;

    /** One in-flight toggle ripple, or nothing when {@code rippleProgress} has reached 1. */
    private Module rippleModule;
    private float rippleProgress = 1.0F;
    private float rippleX;
    private float rippleY;
    /** Distance from the click point to the far corner of the row; the crest expires as it gets there. */
    private float rippleRadius;
    private boolean rippleEnabling;
    private final Map<RangeSetting, Float> rangeLowAnimations = new IdentityHashMap<>();
    private final Map<RangeSetting, Float> rangeHighAnimations = new IdentityHashMap<>();

    private float collapse;

    private boolean draggingPanel;
    private float dragOffsetX;
    private float dragOffsetY;

    private NumberSetting draggingSlider;
    private RangeSetting draggingRange;
    private boolean draggingRangeHigh;

    public Panel(Category category, List<Module> modules, float x, float y) {
        this.category = category;
        this.modules = modules;
        this.x = x;
        this.y = y;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isEditing() {
        return editMode;
    }

    public boolean isEditButton(double mouseX, double mouseY) {
        return GlassTheme.inside(mouseX, mouseY,
                x + width - EDIT_BUTTON_WIDTH, y,
                EDIT_BUTTON_WIDTH, GlassTheme.PANEL_HEADER_HEIGHT);
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    /** True once this panel has been dragged to a spot of its own and no longer follows the grid. */
    public boolean isPlaced() {
        return placed;
    }

    public void setPlaced(boolean value) {
        this.placed = value;
    }

    /** True if the press that just ended actually moved the panel; clears the flag as it reads it. */
    public boolean consumeMoved() {
        boolean result = moved;
        moved = false;
        return result;
    }

    private float contentHeight() {
        float total = 0.0F;
        for (Module module : modules) {
            if (!shows(module)) {
                continue;
            }
            total += elementHeight(module) + GlassTheme.MODULE_SPACING;
        }
        return total;
    }

    /**
     * Current drawn height. The open height is eased back toward the header-only height by
     * {@code collapse}, so right-clicking a header folds the panel smoothly instead of snapping.
     */
    public float getHeight() {
        float content = contentHeight();
        if (content <= 0.0F) {
            return GlassTheme.PANEL_HEADER_HEIGHT;
        }
        float open = Math.min(GlassTheme.MAX_PANEL_HEIGHT,
                GlassTheme.PANEL_HEADER_HEIGHT + GlassTheme.PANEL_CONTENT_GAP + content);
        return GlassTheme.PANEL_HEADER_HEIGHT
                + (open - GlassTheme.PANEL_HEADER_HEIGHT) * (1.0F - collapse);
    }

    private float elementHeight(Module module) {
        return GlassTheme.MODULE_HEIGHT + (editMode ? 0.0F : expand(module) * settingsHeight(module));
    }

    private boolean shows(Module module) {
        return editMode || module.isVisibleInMenu();
    }

    private float settingsHeight(Module module) {
        if (!module.hasSettings()) {
            return 0.0F;
        }
        float total = GlassTheme.SETTING_SPACING;
        for (Setting setting : module.getSettings()) {
            if (!setting.isVisible()) {
                continue;
            }
            total += rowHeight(setting) + GlassTheme.SETTING_SPACING;
        }
        return total;
    }

    // --- Rendering -----------------------------------------------------------------------------

    public void render(float mouseX, float mouseY) {
        // One frame delta feeds every time-based animation, so they stay in step at any framerate.
        float dt = frameDeltaSeconds();
        collapse = approach(collapse, collapsed ? 1.0F : 0.0F, COLLAPSE_SPEED);
        updateSway(dt);
        updateRipple(dt);

        float height = getHeight();
        // While the panel is tilted, draw the whole thing through a rotation about its centre. The
        // scissor inside follows the transform (vgScissor premultiplies the state matrix), so the
        // content clip tilts with the panel rather than staying axis-aligned.
        boolean tilted = Math.abs(sway) > 0.01F;
        if (tilted) {
            float pivotX = x + width * 0.5F;
            float pivotY = y + height * 0.5F;
            VGRenderer.save();
            VGRenderer.translate(pivotX, pivotY);
            VGRenderer.rotate((float) Math.toRadians(sway));
            VGRenderer.translate(-pivotX, -pivotY);
        }
        try {
            renderBody(mouseX, mouseY, height, dt);
        } finally {
            if (tilted) {
                VGRenderer.restore();
            }
        }
    }

    /**
     * Runs the tilt spring. It aims with the drag direction, so dragging right tips the top of the
     * panel to the right, and it swings back upright once the drag stops or the panel is released.
     */
    private void updateSway(float dt) {
        // Sample how far the panel travelled this frame, not per input event. Mouse events arrive at
        // an uneven rate — several in one frame, none in the next — so per-event deltas are noisy and
        // flip sign, and a spring chasing that noise is what made the panel shudder back and forth.
        float frameTravel = x - swaySampleX;
        swaySampleX = x;
        float inputFollow = 1.0F - (float) Math.exp(-dt / SWAY_INPUT_TAU);
        dragSway += (frameTravel - dragSway) * inputFollow;

        float target = draggingPanel
                ? clamp(dragSway * SWAY_PER_UNIT, -SWAY_LIMIT, SWAY_LIMIT)
                : 0.0F;

        // Under-damped follow, so the tilt eases past the input and swings back. Sub-stepped so a
        // long frame cannot destabilise it.
        float omega = 2.0F / SWAY_FOLLOW_TAU;
        float damping = 2.0F * SWAY_DAMPING_RATIO * omega;
        float remaining = Math.min(dt, SWAY_MAX_FRAME);
        while (remaining > 0.0F) {
            float step = Math.min(remaining, SWAY_MAX_STEP);
            remaining -= step;
            float accel = omega * omega * (target - sway) - damping * swayVelocity;
            swayVelocity += accel * step;
            sway += swayVelocity * step;
        }
    }

    private void updateRipple(float dt) {
        if (rippleProgress >= 1.0F) {
            return;
        }
        rippleProgress = Math.min(1.0F, rippleProgress + dt / RIPPLE_DURATION);
        if (rippleProgress >= 1.0F) {
            rippleModule = null;
        }
    }

    /**
     * Starts a toggle ripple at the click point, in coordinates relative to the module row so it stays
     * put if the panel scrolls or is dragged while it plays.
     */
    private void startRipple(Module module, Row row, double mouseX, double mouseY, boolean enabling) {
        rippleModule = module;
        float localX = (float) mouseX - row.x();
        float localY = (float) mouseY - row.y();
        rippleX = localX;
        rippleY = localY;
        // Sized to the click: reach whichever edge is furthest away, so the crest always crosses the
        // whole row regardless of where along it the click landed. A click at the end of the row gets
        // a longer, faster wave than one in the middle, but the same duration.
        float dx = Math.max(localX, row.w() - localX);
        float dy = Math.max(localY, row.h() - localY);
        rippleRadius = RIPPLE_REACH * (float) Math.sqrt(dx * dx + dy * dy);
        rippleEnabling = enabling;
        rippleProgress = 0.0F;
    }

    /**
     * One frame of the toggle bloom. The leading ring is what the eye follows: a stroked circle that
     * spreads out of the click point, thins as it goes and fades, while the wash behind it lifts the
     * row as the wavefront passes. Everything ends at zero, leaving the steady enabled wash to take
     * over, so the bloom never has to snap off.
     */
    private void drawRippleBloom(float cx, float cy) {
        float radius = rippleRadius * rippleProgress;
        if (radius <= 0.5F) {
            return;
        }

        // Filled disc trailing the crest, so the ground the wave has already crossed fills in behind
        // it rather than only the thin ring being lit.
        VGRenderer.circle(cx, cy, radius,
                GlassTheme.accent(RIPPLE_ALPHA * RIPPLE_WASH * rippleFalloff(0.5F)));
        float stroke = RIPPLE_STROKE * (1.0F - rippleProgress * 0.55F);
        VGRenderer.circleOutline(cx, cy, Math.max(0.5F, radius - stroke * 0.5F), stroke,
                GlassTheme.accent(RIPPLE_ALPHA * rippleFalloff(0.7F)));
    }

    /**
     * Strength of the bloom at this point in the sweep: held at full while the wave is travelling,
     * then dropped over the last stretch. A straight ramp spends most of the animation already faint,
     * which reads as a twitch rather than a wave. Still zero at the end, so the row's steady enabled
     * wash takes over without a step. {@code hold} is the point in the sweep it starts fading from.
     */
    private float rippleFalloff(float hold) {
        // Turning a module off gets the same motion in reverse, just quieter: the crest collapses back
        // into the click point as the lift it came from drains away.
        float strength = rippleEnabling ? 1.0F : 0.4F;
        return clamp01((1.0F - rippleProgress) / (1.0F - hold)) * strength;
    }

    /** Seconds since the previous frame, clamped so a stalled frame cannot jump an animation. */
    private float frameDeltaSeconds() {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.0F : (now - lastFrameNanos) / 1.0E9F;
        lastFrameNanos = now;
        return Math.max(0.0F, Math.min(SWAY_MAX_FRAME, dt));
    }

    private float shakeOffset(Module module, float dt) {
        float remaining = shakeAnimations.getOrDefault(module, 0.0F);
        if (remaining <= 0.0F) {
            return 0.0F;
        }
        remaining = Math.max(0.0F, remaining - dt);
        if (remaining == 0.0F) {
            shakeAnimations.remove(module);
            return 0.0F;
        }
        shakeAnimations.put(module, remaining);
        float progress = 1.0F - remaining / SHAKE_DURATION;
        float decay = 1.0F - progress;
        return (float) Math.sin(progress * Math.PI * 8.0F) * SHAKE_AMPLITUDE * decay;
    }

    private void renderBody(float mouseX, float mouseY, float height, float dt) {
        // The edge is a filled rounded rect with the surface drawn inset on top of it, not a stroked
        // outline. A stroke is tessellated into short segments, and around the corner arcs that
        // polyline sits inside the true curve, so a stroked border always renders a touch thinner at
        // the corners than along the straight runs. Two concentric fills cannot do that.
        float border = GlassTheme.PANEL_BORDER;
        VGRenderer.roundedRect(x, y, width, height, GlassTheme.RADIUS_LG, GlassTheme.panelBorder());
        VGRenderer.verticalGradient(x + border, y + border,
                width - border * 2.0F, height - border * 2.0F,
                GlassTheme.RADIUS_LG - border,
                GlassTheme.surfacePanel(), GlassTheme.surfacePanelBottom());

        // Header: plain near-white with a per-panel edit ellipsis pinned right.
        float headerSize = GlassTheme.FONT_HEADER;
        VGRenderer.textLeft(category.getDisplayName(), x + GlassTheme.PADDING,
                y + GlassTheme.PANEL_HEADER_HEIGHT / 2.0F,
                headerSize, VGFont.semiboldFace(), HEADER_TRACKING, GlassTheme.headerText());
        String editGlyph = "...";
        float glyphWidth = VGRenderer.textWidth(editGlyph, headerSize,
                VGFont.semiboldFace(), HEADER_TRACKING);
        if (editMode) {
            VGRenderer.roundedRect(x + width - EDIT_BUTTON_WIDTH, y + 3.0F,
                    EDIT_BUTTON_WIDTH - 3.0F, GlassTheme.PANEL_HEADER_HEIGHT - 6.0F,
                    GlassTheme.RADIUS_SM, GlassTheme.accent(0.28F));
        }
        VGRenderer.textLeft(editGlyph,
                x + width - GlassTheme.PADDING - 2.0F - glyphWidth,
                y + GlassTheme.PANEL_HEADER_HEIGHT / 2.0F,
                headerSize, VGFont.semiboldFace(), HEADER_TRACKING,
                editMode ? GlassTheme.accentBright() : GlassTheme.textMuted());

        rows.clear();
        float contentAlpha = 1.0F - collapse;
        if (modules.isEmpty() || contentAlpha <= 0.01F) {
            return;
        }

        float contentTop = y + GlassTheme.PANEL_HEADER_HEIGHT + GlassTheme.PANEL_CONTENT_GAP;
        float visibleHeight = height - GlassTheme.PANEL_HEADER_HEIGHT - GlassTheme.PANEL_CONTENT_GAP;
        float maxScroll = Math.max(0.0F, contentHeight() * contentAlpha - visibleHeight);
        scroll = Math.max(0.0F, Math.min(maxScroll, scroll));

        if (visibleHeight <= 1.0F) {
            return;
        }

        VGRenderer.scissor(x, contentTop, width, visibleHeight);

        float cursor = contentTop - scroll + collapse * COLLAPSE_SLIDE;
        for (Module module : modules) {
            if (!shows(module)) {
                continue;
            }
            boolean expanded = !editMode && module.isExpanded() && module.hasSettings();
            float expand = animate(expandAnimations, module, expanded ? 1.0F : 0.0F, EXPAND_SPEED);
            float elementHeight = GlassTheme.MODULE_HEIGHT + expand * settingsHeight(module);

            float shake = shakeOffset(module, dt);
            if (Math.abs(shake) > 0.01F) {
                VGRenderer.save();
                VGRenderer.translate(shake, 0.0F);
            }

            boolean hovered = GlassTheme.inside(mouseX, mouseY, x, cursor, width, GlassTheme.MODULE_HEIGHT);
            float hover = animate(hoverAnimations, module, hovered ? 1.0F : 0.0F, HOVER_SPEED);
            float enabled = animate(enabledAnimations, module, module.isEnabled() ? 1.0F : 0.0F, ENABLE_SPEED);

            if (editMode) {
                VGRenderer.roundedRect(x + GlassTheme.PADDING * 0.5F, cursor,
                        width - GlassTheme.PADDING, GlassTheme.MODULE_HEIGHT,
                        GlassTheme.RADIUS_SM, module.isVisibleInMenu()
                                ? GlassTheme.accent(0.34F) : GlassTheme.interactiveMuted());
                VGRenderer.roundedOutline(x + GlassTheme.PADDING * 0.5F, cursor,
                        width - GlassTheme.PADDING, GlassTheme.MODULE_HEIGHT,
                        GlassTheme.RADIUS_SM, 1.0F, module.isVisibleInMenu()
                                ? GlassTheme.accent(0.72F) : GlassTheme.panelBorder());
            // Enabled state is a solid gradient bar behind the row, white label above it.
            } else if (enabled > 0.01F) {
                VGRenderer.verticalGradient(x + GlassTheme.PADDING * 0.5F, cursor,
                        width - GlassTheme.PADDING, GlassTheme.MODULE_HEIGHT,
                        GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.accentBright(), enabled * contentAlpha),
                        GlassTheme.scaleAlpha(GlassTheme.accentDeep(), enabled * contentAlpha));
            }

            if (hover > 0.01F) {
                VGRenderer.roundedRect(x + GlassTheme.PADDING * 0.5F, cursor,
                        width - GlassTheme.PADDING, GlassTheme.MODULE_HEIGHT,
                        GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.moduleHover(), hover * contentAlpha));
            }

            // Toggle bloom. Two parts: a row-wide lift, and a crest travelling out of the click point
            // clipped to the row so it reads as a wavefront sweeping along it. The lift is what carries
            // the effect — a travelling shape sets each pixel's brightness at the moment the wave
            // passes it, so the crest alone has faded to nothing by the time it reaches the far end of
            // the row — while the crest is what makes it read as coming *from* the click. Both are
            // drawn before the label so the label stays crisp above them.
            if (!editMode && rippleModule == module) {
                float rowInset = GlassTheme.PADDING * 0.5F;
                VGRenderer.roundedRect(x + rowInset, cursor, width - GlassTheme.PADDING,
                        GlassTheme.MODULE_HEIGHT, GlassTheme.RADIUS_SM,
                        GlassTheme.accent(RIPPLE_FLASH * rippleFalloff(0.55F) * contentAlpha));
                VGRenderer.save();
                VGRenderer.intersectScissor(x + rowInset, cursor,
                        width - GlassTheme.PADDING, GlassTheme.MODULE_HEIGHT);
                drawRippleBloom(x + rippleX, cursor + rippleY);
                VGRenderer.restore();
            }

            // The settings container is drawn from here, above the label, rather than down with the
            // rows it holds: that lets its edge stretch up over the module row and close around the
            // label and chevron. Its inset already matches the module row's own wash, so the stretched
            // outline sits flush with the row it grows out of.
            float groupX = x + GlassTheme.GROUP_INSET;
            float groupW = width - GlassTheme.GROUP_INSET * 2.0F;
            float groupTop = cursor + GlassTheme.MODULE_HEIGHT;
            float groupH = settingsHeight(module);
            if (module.hasSettings() && expand > 0.01F && groupH > border * 2.0F + 1.0F) {
                float groupAlpha = contentAlpha * expand;
                // Eased out rather than linear so the edge gets up to the row early in the opening and
                // settles. Linear would leave it crossing the middle of the row for the first frames,
                // where it cuts across the highlight the hover and ripple draw there.
                float outline = (1.0F - (1.0F - expand) * (1.0F - expand)) * MODULE_OUTLINE_REACH;
                // Top corners take the module row's radius so the edge sits exactly on the row's
                // highlight; the bottom keeps the well's wider radius.
                VGRenderer.roundedRectVarying(groupX, groupTop - outline, groupW, groupH + outline,
                        GlassTheme.RADIUS_SM, GlassTheme.RADIUS_SM,
                        GlassTheme.RADIUS_MD, GlassTheme.RADIUS_MD,
                        GlassTheme.scaleAlpha(GlassTheme.panelBorder(), groupAlpha * 0.65F));
                VGRenderer.roundedRectVarying(groupX + border, groupTop + border,
                        groupW - border * 2.0F, groupH - border * 2.0F,
                        GlassTheme.RADIUS_SM - border, GlassTheme.RADIUS_SM - border,
                        GlassTheme.RADIUS_MD - border, GlassTheme.RADIUS_MD - border,
                        GlassTheme.scaleAlpha(GlassTheme.groupFill(), groupAlpha));
            }

            Color nameColor = editMode
                    ? (module.isVisibleInMenu() ? GlassTheme.textPrimary() : GlassTheme.textMuted())
                    : GlassTheme.scaleAlpha(GlassTheme.lerp(GlassTheme.textSecondary(),
                            GlassTheme.textPrimary(), enabled), contentAlpha);
            VGRenderer.textLeft(module.getDisplayName(), x + GlassTheme.TEXT_INSET,
                    cursor + GlassTheme.MODULE_HEIGHT / 2.0F,
                    GlassTheme.FONT_MODULE, VGFont.face(), nameColor);

            if (!editMode && module.hasSettings()) {
                drawChevron(x + width - GlassTheme.PADDING - 2.0F,
                        cursor + GlassTheme.MODULE_HEIGHT / 2.0F, expand,
                        GlassTheme.scaleAlpha(GlassTheme.textMuted(), contentAlpha * (0.45F + 0.55F * expand)));
            }

            rows.add(new Row(TYPE_MODULE, module, null, x, cursor, width, GlassTheme.MODULE_HEIGHT));

            if (module.hasSettings() && expand > 0.01F) {
                // The rows sit at fixed offsets inside the container drawn above, so its height never
                // animates; the panel's own scissor reveals both in step with the opening module.
                float settingY = cursor + GlassTheme.MODULE_HEIGHT + GlassTheme.SETTING_SPACING;
                int index = 0;
                for (Setting setting : module.getSettings()) {
                    if (!setting.isVisible()) {
                        continue;
                    }
                    float rowHeight = rowHeight(setting);
                    // Rows ease in one after another, sliding up into the space the panel is opening.
                    float start = Math.min(0.6F, index * STAGGER);
                    float row = clamp01((expand - start) / (1.0F - start));
                    float offsetY = (1.0F - row) * SETTINGS_SLIDE;
                    float settingX = x + GlassTheme.SETTING_INDENT;
                    float settingW = width - GlassTheme.SETTING_INDENT * 2.0F;
                    boolean settingHovered = GlassTheme.inside(mouseX, mouseY,
                            settingX, settingY + offsetY, settingW, rowHeight);
                    float settingAlpha = contentAlpha * row;
                    if (settingAlpha > 0.02F) {
                        renderSetting(setting, settingX, settingY + offsetY, settingW, rowHeight,
                                settingHovered, settingAlpha, mouseX, mouseY);
                    }
                    rows.add(new Row(rowType(setting), module, setting, settingX,
                            settingY + offsetY, settingW, rowHeight));
                    settingY += rowHeight + GlassTheme.SETTING_SPACING;
                    index++;
                }
            }

            if (Math.abs(shake) > 0.01F) {
                VGRenderer.restore();
            }

            cursor += elementHeight + GlassTheme.MODULE_SPACING;
        }

        VGRenderer.resetScissor();
    }

    /**
     * A chevron that turns from a right arrow into a down arrow as settings open. Drawn as one
     * polyline through the vertex so the bend joins cleanly and both tips get round caps — two
     * separate strokes leave a notch at the corner and flat ends.
     */
    private void drawChevron(float cx, float cy, float expand, Color color) {
        float[] arrow = { -1.9F, -3.4F, 2.1F, 0.0F, -1.9F, 3.4F };
        VGRenderer.save();
        VGRenderer.translate(cx, cy);
        VGRenderer.rotate((float) Math.toRadians(90.0 * expand));
        VGRenderer.polyline(arrow, 3, 1.5F, false, color);
        VGRenderer.restore();
    }

    private void renderSetting(Setting setting, float sx, float sy, float sw, float sh,
                               boolean hovered, float rowAlpha, float mouseX, float mouseY) {
        float alpha = clamp01(rowAlpha);

        // Sliders put their label on the row's upper third to leave room for the track below it;
        // every other row type just centres its single line.
        boolean slider = setting instanceof NumberSetting || setting instanceof RangeSetting;
        float labelCenterY = sy + (slider
                ? GlassTheme.SLIDER_LABEL_CENTER
                : GlassTheme.SETTING_HEIGHT / 2.0F);
        float trackY = sy + GlassTheme.SLIDER_TRACK_TOP;

        if (setting instanceof BooleanSetting bool) {
            if (hovered) {
                VGRenderer.roundedRect(sx, sy, sw, sh, GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.moduleHover(), 0.45F * alpha));
            }
            VGRenderer.textLeft(setting.getName(), sx + GlassTheme.SETTING_PADDING, labelCenterY,
                    GlassTheme.FONT_SETTING, VGFont.face(),
                    GlassTheme.scaleAlpha(bool.get() ? GlassTheme.textPrimary() : GlassTheme.textSecondary(), alpha));

            // Switch: track plus a knob that slides between the two ends.
            float trackW = GlassTheme.TOGGLE_WIDTH;
            float trackH = GlassTheme.TOGGLE_HEIGHT;
            float tx = sx + sw - GlassTheme.SETTING_PADDING - trackW;
            float ty = sy + (GlassTheme.SETTING_HEIGHT - trackH) / 2.0F;
            float on = animate(toggleAnimations, bool, bool.get() ? 1.0F : 0.0F, TOGGLE_SPEED);

            VGRenderer.roundedRect(tx, ty, trackW, trackH, trackH / 2.0F,
                    GlassTheme.scaleAlpha(GlassTheme.lerp(GlassTheme.interactiveMuted(),
                            GlassTheme.accent(), on), alpha));

            float knob = trackH - GlassTheme.TOGGLE_INSET * 2.0F;
            float travel = trackW - knob - GlassTheme.TOGGLE_INSET * 2.0F;
            float knobX = tx + GlassTheme.TOGGLE_INSET + travel * on + knob / 2.0F;
            VGRenderer.circle(knobX, ty + trackH / 2.0F, knob / 2.0F,
                    GlassTheme.scaleAlpha(GlassTheme.knob(), alpha));
            return;
        }

        if (setting instanceof NumberSetting number) {
            if (hovered) {
                VGRenderer.roundedRect(sx, sy, sw, sh, GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.moduleHover(), 0.5F * alpha));
            }
            VGRenderer.textLeft(setting.getName(), sx + GlassTheme.SETTING_PADDING, labelCenterY,
                    GlassTheme.FONT_SETTING, VGFont.face(),
                    GlassTheme.scaleAlpha(GlassTheme.textSecondary(), alpha));

            String value = number.display();
            float valueWidth = VGRenderer.textWidth(value, GlassTheme.FONT_SETTING, VGFont.face(), 0.0F);
            VGRenderer.textLeft(value, (sx + sw - GlassTheme.SETTING_PADDING) - valueWidth, labelCenterY,
                    GlassTheme.FONT_SETTING, VGFont.face(),
                    GlassTheme.scaleAlpha(draggingSlider == number ? GlassTheme.textPrimary() : GlassTheme.textMuted(), alpha));

            float trackX = sx + GlassTheme.SETTING_PADDING;
            float trackW = sw - GlassTheme.SETTING_PADDING * 2.0F;
            float trackH = GlassTheme.TRACK_HEIGHT;
            VGRenderer.roundedRect(trackX, trackY, trackW, trackH, trackH / 2.0F,
                    GlassTheme.scaleAlpha(GlassTheme.sliderTrack(), alpha));

            float fraction = animate(sliderAnimations, number, number.normalized(), SLIDER_SPEED);
            float filled = trackW * fraction;
            if (filled > 0.5F) {
                VGRenderer.roundedRect(trackX, trackY, filled, trackH, trackH / 2.0F,
                        GlassTheme.scaleAlpha(GlassTheme.accent(), alpha));
            }
            float knob = GlassTheme.KNOB_SIZE;
            VGRenderer.circle(trackX + filled, trackY + trackH / 2.0F, knob * 0.5F,
                    GlassTheme.scaleAlpha(GlassTheme.knob(), alpha));
            return;
        }

        if (setting instanceof RangeSetting range) {
            if (hovered) {
                VGRenderer.roundedRect(sx, sy, sw, sh, GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.moduleHover(), 0.5F * alpha));
            }
            VGRenderer.textLeft(setting.getName(), sx + GlassTheme.SETTING_PADDING, labelCenterY,
                    GlassTheme.FONT_SETTING, VGFont.face(),
                    GlassTheme.scaleAlpha(GlassTheme.textSecondary(), alpha));

            String value = range.display();
            float valueWidth = VGRenderer.textWidth(value, GlassTheme.FONT_SETTING, VGFont.face(), 0.0F);
            VGRenderer.textLeft(value, (sx + sw - GlassTheme.SETTING_PADDING) - valueWidth, labelCenterY,
                    GlassTheme.FONT_SETTING, VGFont.face(),
                    GlassTheme.scaleAlpha(draggingRange == range ? GlassTheme.textPrimary() : GlassTheme.textMuted(), alpha));

            float trackX = sx + GlassTheme.SETTING_PADDING;
            float trackW = sw - GlassTheme.SETTING_PADDING * 2.0F;
            float trackH = GlassTheme.TRACK_HEIGHT;
            VGRenderer.roundedRect(trackX, trackY, trackW, trackH, trackH / 2.0F,
                    GlassTheme.scaleAlpha(GlassTheme.sliderTrack(), alpha));

            float lowX = trackW * animate(rangeLowAnimations, range, range.normalizedLow(), SLIDER_SPEED);
            float highX = trackW * animate(rangeHighAnimations, range, range.normalizedHigh(), SLIDER_SPEED);
            if (highX - lowX > 0.5F) {
                VGRenderer.roundedRect(trackX + lowX, trackY, highX - lowX, trackH, trackH / 2.0F,
                        GlassTheme.scaleAlpha(GlassTheme.accent(), alpha));
            }
            float knob = GlassTheme.KNOB_SIZE;
            VGRenderer.circle(trackX + lowX, trackY + trackH / 2.0F, knob * 0.5F,
                    GlassTheme.scaleAlpha(GlassTheme.knob(), alpha));
            VGRenderer.circle(trackX + highX, trackY + trackH / 2.0F, knob * 0.5F,
                    GlassTheme.scaleAlpha(GlassTheme.knob(), alpha));
            return;
        }

        if (setting instanceof DropdownSetting dropdown) {
            float headerH = GlassTheme.SETTING_HEIGHT;
            float open = animate(dropdownAnimations, dropdown,
                    dropdown.isOpen() ? 1.0F : 0.0F, EXPAND_SPEED);
            boolean headerHovered = hovered && mouseY < sy + headerH;
            if (headerHovered) {
                VGRenderer.roundedRect(sx, sy, sw, headerH, GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.moduleHover(), 0.45F * alpha));
            }
            VGRenderer.textLeft(setting.getName(), sx + GlassTheme.SETTING_PADDING, labelCenterY,
                    GlassTheme.FONT_SETTING, VGFont.face(),
                    GlassTheme.scaleAlpha(GlassTheme.textSecondary(), alpha));

            // Current choice right-aligned ahead of the chevron, which turns down as it opens.
            float chevronX = (sx + sw - GlassTheme.SETTING_PADDING) - 2.0F;
            String current = dropdown.get();
            float currentWidth = VGRenderer.textWidth(current,
                    GlassTheme.FONT_HINT, VGFont.face(), 0.0F);
            VGRenderer.textLeft(current, chevronX - 8.0F - currentWidth, labelCenterY,
                    GlassTheme.FONT_HINT, VGFont.face(),
                    GlassTheme.scaleAlpha(GlassTheme.textMuted(), alpha));
            drawChevron(chevronX, labelCenterY, open,
                    GlassTheme.scaleAlpha(GlassTheme.textMuted(), alpha));

            if (dropdown.isOpen()) {
                int optionCount = dropdown.optionCount();
                float optionTop = sy + headerH + GlassTheme.SETTING_SPACING;
                for (int i = 0; i < optionCount; i++) {
                    float optionY = optionTop + i * headerH;
                    boolean optionHovered = hovered
                            && mouseY >= optionY && mouseY < optionY + headerH;
                    boolean selected = i == dropdown.getSelected();
                    if (optionHovered) {
                        VGRenderer.roundedRect(sx, optionY, sw, headerH, GlassTheme.RADIUS_SM,
                                GlassTheme.scaleAlpha(GlassTheme.moduleHover(), 0.5F * alpha));
                    }
                    if (selected) {
                        VGRenderer.circle(sx + GlassTheme.SETTING_PADDING + 2.5F,
                                optionY + headerH / 2.0F, 2.0F,
                                GlassTheme.scaleAlpha(GlassTheme.accent(), alpha));
                    }
                    VGRenderer.textLeft(dropdown.option(i),
                            sx + GlassTheme.SETTING_PADDING + 8.0F, optionY + headerH / 2.0F,
                            GlassTheme.FONT_SETTING, VGFont.face(),
                            GlassTheme.scaleAlpha(selected || optionHovered
                                    ? GlassTheme.textPrimary() : GlassTheme.textSecondary(), alpha));
                }
            }
            return;
        }

        if (setting instanceof KeybindSetting keybind) {
            if (hovered) {
                VGRenderer.roundedRect(sx, sy, sw, sh, GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.moduleHover(), 0.45F * alpha));
            }
            VGRenderer.textLeft(setting.getName(), sx + GlassTheme.SETTING_PADDING, labelCenterY,
                    GlassTheme.FONT_SETTING, VGFont.face(),
                    GlassTheme.scaleAlpha(GlassTheme.textSecondary(), alpha));

            String label = keybind.displayName();
            float pillPad = 6.0F;
            float pillWidth = VGRenderer.textWidth(label, GlassTheme.FONT_HINT, VGFont.face(), 0.0F) + pillPad * 2.0F;
            float pillHeight = 13.0F;
            float pillX = (sx + sw - GlassTheme.SETTING_PADDING) - pillWidth;
            float pillY = sy + (GlassTheme.SETTING_HEIGHT - pillHeight) / 2.0F;
            VGRenderer.roundedRect(pillX, pillY, pillWidth, pillHeight, GlassTheme.RADIUS_SM,
                    GlassTheme.scaleAlpha(keybind.isListening() ? GlassTheme.accent()
                            : GlassTheme.interactiveMuted(), 0.9F * alpha));
            VGRenderer.textCentered(label, pillX + pillWidth / 2.0F, pillY + pillHeight / 2.0F,
                    GlassTheme.FONT_HINT, VGFont.face(),
                    GlassTheme.scaleAlpha(keybind.isListening() ? GlassTheme.textOnAccent()
                            : GlassTheme.textMuted(), alpha));
            return;
        }

        if (setting instanceof ToggleListSetting toggle) {
            float headerH = GlassTheme.SETTING_HEIGHT;
            boolean headerHovered = hovered && mouseY < sy + headerH;
            if (headerHovered) {
                VGRenderer.roundedRect(sx, sy, sw, headerH, GlassTheme.RADIUS_SM,
                        GlassTheme.scaleAlpha(GlassTheme.moduleHover(), 0.45F * alpha));
            }
            VGRenderer.textLeft(setting.getName(), sx + GlassTheme.SETTING_PADDING, labelCenterY,
                    GlassTheme.FONT_SETTING, VGFont.face(),
                    GlassTheme.scaleAlpha(GlassTheme.textSecondary(), alpha));

            // On-count ahead of the chevron, which turns down as the list opens.
            float chevronX = (sx + sw - GlassTheme.SETTING_PADDING) - 2.0F;
            String count = toggle.onCount() + "/" + toggle.getOptions().length;
            float countWidth = VGRenderer.textWidth(count,
                    GlassTheme.FONT_HINT, VGFont.face(), 0.0F);
            VGRenderer.textLeft(count, chevronX - 8.0F - countWidth, labelCenterY,
                    GlassTheme.FONT_HINT, VGFont.face(),
                    GlassTheme.scaleAlpha(GlassTheme.textMuted(), alpha));
            drawChevron(chevronX, labelCenterY, toggle.isOpen() ? 1.0F : 0.0F,
                    GlassTheme.scaleAlpha(GlassTheme.textMuted(), alpha));

            if (toggle.isOpen()) {
                int optionCount = toggle.optionCount();
                float optionTop = sy + headerH + GlassTheme.SETTING_SPACING;
                for (int i = 0; i < optionCount; i++) {
                    float optionY = optionTop + i * headerH;
                    boolean optionHovered = hovered
                            && mouseY >= optionY && mouseY < optionY + headerH;
                    boolean on = toggle.isOn(i);
                    if (optionHovered) {
                        VGRenderer.roundedRect(sx, optionY, sw, headerH, GlassTheme.RADIUS_SM,
                                GlassTheme.scaleAlpha(GlassTheme.moduleHover(), 0.5F * alpha));
                    }
                    float box = 9.0F;
                    float boxX = sx + GlassTheme.SETTING_PADDING;
                    float boxY = optionY + (headerH - box) / 2.0F;
                    if (on) {
                        VGRenderer.roundedRect(boxX, boxY, box, box, 2.0F,
                                GlassTheme.scaleAlpha(GlassTheme.accent(), 0.95F * alpha));
                    } else {
                        VGRenderer.roundedRect(boxX, boxY, box, box, 2.0F,
                                GlassTheme.scaleAlpha(GlassTheme.interactiveMuted(), 0.7F * alpha));
                    }
                    VGRenderer.textLeft(toggle.option(i), boxX + box + 5.0F, optionY + headerH / 2.0F,
                            GlassTheme.FONT_SETTING, VGFont.face(),
                            GlassTheme.scaleAlpha(on || optionHovered
                                    ? GlassTheme.textPrimary() : GlassTheme.textSecondary(), alpha));
                }
            }
            return;
        }

        if (setting instanceof ModeSetting mode) {
            // Segmented control: every option visible as its own pill, selected in accent.
            String[] modes = mode.getModes();
            float[] geometry = modePillGeometry(setting, modes, sx, sw);
            float pillX0 = geometry[0];
            float pillW = geometry[1];
            float pillGap = geometry[2];
            float pillH = 13.0F;
            float pillY = labelCenterY - pillH / 2.0F;
            VGRenderer.textLeft(setting.getName(), sx + GlassTheme.SETTING_PADDING, labelCenterY,
                    GlassTheme.FONT_SETTING, VGFont.face(),
                    GlassTheme.scaleAlpha(GlassTheme.textSecondary(), alpha));
            for (int i = 0; i < modes.length; i++) {
                float pillX = pillX0 + i * (pillW + pillGap);
                boolean selected = i == mode.getIndex();
                boolean pillHovered = hovered && mouseX >= pillX && mouseX <= pillX + pillW;
                if (selected) {
                    VGRenderer.roundedRect(pillX, pillY, pillW, pillH, GlassTheme.RADIUS_SM,
                            GlassTheme.scaleAlpha(GlassTheme.accent(), 0.95F * alpha));
                } else {
                    VGRenderer.roundedRect(pillX, pillY, pillW, pillH, GlassTheme.RADIUS_SM,
                            GlassTheme.scaleAlpha(GlassTheme.interactiveMuted(),
                                    (pillHovered ? 0.85F : 0.55F) * alpha));
                }
                float optionWidth = VGRenderer.textWidth(modes[i],
                        GlassTheme.FONT_HINT, VGFont.face(), 0.0F);
                VGRenderer.textLeft(modes[i], pillX + (pillW - optionWidth) / 2.0F,
                        pillY + pillH / 2.0F,
                        GlassTheme.FONT_HINT, VGFont.face(),
                        GlassTheme.scaleAlpha(selected ? GlassTheme.textOnAccent()
                                : (pillHovered ? GlassTheme.textPrimary() : GlassTheme.textMuted()),
                                alpha));
            }
            return;
        }
    }

    /**
     * Geometry of a segmented mode row as {@code [pillStartX, pillWidth, pillGap]}. Click
     * handling reuses the same math so taps land on the pill they highlight.
     */
    private static float[] modePillGeometry(Setting setting, String[] modes, float sx, float sw) {
        float nameWidth = VGRenderer.textWidth(setting.getName(),
                GlassTheme.FONT_SETTING, VGFont.face(), 0.0F);
        float pillGap = 3.0F;
        float available = sw - nameWidth - GlassTheme.SETTING_PADDING * 2.0F - 10.0F
                - pillGap * (modes.length - 1);
        float pillW = Math.max(24.0F, available / modes.length);
        float startX = sx + sw - GlassTheme.SETTING_PADDING
                - (pillW * modes.length + pillGap * (modes.length - 1));
        return new float[] { startX, pillW, pillGap };
    }

    private static float rowHeight(Setting setting) {
        if (setting instanceof NumberSetting || setting instanceof RangeSetting) {
            return GlassTheme.SLIDER_HEIGHT;
        }
        if (setting instanceof DropdownSetting dropdown && dropdown.isOpen()) {
            return GlassTheme.SETTING_HEIGHT + GlassTheme.SETTING_SPACING
                    + dropdown.optionCount() * GlassTheme.SETTING_HEIGHT;
        }
        if (setting instanceof ToggleListSetting toggle && toggle.isOpen()) {
            return GlassTheme.SETTING_HEIGHT + GlassTheme.SETTING_SPACING
                    + toggle.optionCount() * GlassTheme.SETTING_HEIGHT;
        }
        return GlassTheme.SETTING_HEIGHT;
    }

    private float expand(Module module) {
        if (!module.hasSettings()) {
            return 0.0F;
        }
        return expandAnimations.getOrDefault(module, module.isExpanded() ? 1.0F : 0.0F);
    }

    /**
     * Eases the value stored under {@code key} toward {@code target}. A fresh key starts at the
     * target so first paint is already settled, and the last half-percent snaps to avoid the value
     * creeping toward the target forever.
     */
    private static <K> float animate(Map<K, Float> map, K key, float target, float speed) {
        float current = map.getOrDefault(key, target);
        float next = current + (target - current) * speed;
        if (Math.abs(target - next) < 0.001F) {
            next = target;
        }
        map.put(key, next);
        return next;
    }

    /** Eases a plain scalar the same way {@link #animate} eases a mapped one. */
    private static float approach(float current, float target, float speed) {
        float next = current + (target - current) * speed;
        return Math.abs(target - next) < 0.001F ? target : next;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int rowType(Setting setting) {
        if (setting instanceof BooleanSetting) {
            return TYPE_BOOL;
        }
        if (setting instanceof NumberSetting) {
            return TYPE_NUMBER;
        }
        if (setting instanceof RangeSetting) {
            return TYPE_RANGE;
        }
        if (setting instanceof DropdownSetting) {
            return TYPE_DROPDOWN;
        }
        if (setting instanceof KeybindSetting) {
            return TYPE_KEYBIND;
        }
        if (setting instanceof ToggleListSetting) {
            return TYPE_TOGGLE_LIST;
        }
        return TYPE_MODE;
    }

    // --- Input ---------------------------------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (GlassTheme.inside(mouseX, mouseY, x, y, width, GlassTheme.PANEL_HEADER_HEIGHT)) {
            if (button == 0) {
                if (isEditButton(mouseX, mouseY)) {
                    editMode = !editMode;
                    collapsed = false;
                    scroll = 0.0F;
                    if (editMode) {
                        for (Module module : modules) {
                            module.setExpanded(false);
                        }
                    }
                } else {
                    draggingPanel = true;
                    dragOffsetX = (float) mouseX - x;
                    dragOffsetY = (float) mouseY - y;
                    swaySampleX = x;
                    dragSway = 0.0F;
                    swayVelocity = 0.0F;
                    moved = false;
                }
            } else if (button == 1) {
                collapsed = !collapsed;
            }
            return true;
        }

        for (Row row : rows) {
            if (!GlassTheme.inside(mouseX, mouseY, row.x, row.y, row.w, row.h)) {
                continue;
            }

            switch (row.type) {
                case TYPE_MODULE -> {
                    if (editMode && button == 0) {
                        row.module.setVisibleInMenu(!row.module.isVisibleInMenu());
                        shakeAnimations.put(row.module, SHAKE_DURATION);
                    // Reference behaviour: left click toggles, right click opens the settings.
                    } else if (!editMode && button == 0) {
                        boolean enabling = !row.module.isEnabled();
                        row.module.toggle();
                        // Anchored to the click, not the row centre, so the bloom starts under the
                        // cursor wherever along the row it landed.
                        startRipple(row.module, row, mouseX, mouseY, enabling);
                    } else if (!editMode && button == 1 && row.module.hasSettings()) {
                        row.module.setExpanded(!row.module.isExpanded());
                    }
                }
                case TYPE_BOOL -> {
                    if (button == 0 && row.setting instanceof BooleanSetting bool) {
                        bool.toggle();
                    }
                }
                case TYPE_NUMBER -> {
                    if (button == 0 && row.setting instanceof NumberSetting number) {
                        draggingSlider = number;
                        updateSlider(number, mouseX, row);
                    }
                }
                case TYPE_RANGE -> {
                    if (button == 0 && row.setting instanceof RangeSetting range) {
                        draggingRange = range;
                        draggingRangeHigh = nearestKnobIsHigh(range, mouseX, row);
                        updateRange(range, mouseX, row);
                    }
                }
                case TYPE_MODE -> {
                    if (row.setting instanceof ModeSetting mode && button == 0) {
                        String[] modes = mode.getModes();
                        float[] geometry = modePillGeometry(row.setting, modes, row.x, row.w);
                        int index = (int) ((mouseX - geometry[0]) / (geometry[1] + geometry[2]));
                        if (index >= 0 && index < modes.length) {
                            mode.select(index);
                        }
                    }
                }
                case TYPE_KEYBIND -> {
                    if (row.setting instanceof KeybindSetting keybind && button == 0) {
                        KeybindSetting.listen(keybind);
                    }
                }
                case TYPE_TOGGLE_LIST -> {
                    if (row.setting instanceof ToggleListSetting toggle && button == 0) {
                        float headerH = GlassTheme.SETTING_HEIGHT;
                        if (mouseY < row.y + headerH) {
                            boolean opening = !toggle.isOpen();
                            for (Setting other : row.module.getSettings()) {
                                if (other instanceof ToggleListSetting sibling && sibling != toggle) {
                                    sibling.setOpen(false);
                                }
                            }
                            toggle.setOpen(opening);
                        } else if (toggle.isOpen()) {
                            float optionsTop = row.y + headerH + GlassTheme.SETTING_SPACING;
                            int index = (int) ((mouseY - optionsTop) / headerH);
                            if (index >= 0 && index < toggle.optionCount()) {
                                toggle.toggle(index);
                            }
                        }
                    }
                }
                case TYPE_DROPDOWN -> {
                    if (row.setting instanceof DropdownSetting dropdown && button == 0) {
                        float headerH = GlassTheme.SETTING_HEIGHT;
                        if (mouseY < row.y + headerH) {
                            // One open dropdown per module: opening this one folds the others.
                            boolean opening = !dropdown.isOpen();
                            for (Setting other : row.module.getSettings()) {
                                if (other instanceof DropdownSetting sibling && sibling != dropdown) {
                                    sibling.setOpen(false);
                                }
                            }
                            dropdown.setOpen(opening);
                        } else if (dropdown.isOpen()) {
                            float optionsTop = row.y + headerH + GlassTheme.SETTING_SPACING;
                            int index = (int) ((mouseY - optionsTop) / headerH);
                            if (index >= 0 && index < dropdown.optionCount()) {
                                dropdown.select(index);
                                dropdown.setOpen(false);
                            }
                        }
                    }
                }
                default -> {
                }
            }
            return true;
        }

        return GlassTheme.inside(mouseX, mouseY, x, y, width, getHeight());
    }

    public void mouseDragged(double mouseX, double mouseY, int button) {
        if (draggingPanel) {
            // Position only. The sway reads the resulting movement once per frame in updateSway().
            x = (float) mouseX - dragOffsetX;
            y = (float) mouseY - dragOffsetY;
            moved = true;
            return;
        }
        if (draggingSlider != null) {
            for (Row row : rows) {
                if (row.setting == draggingSlider) {
                    updateSlider(draggingSlider, mouseX, row);
                    break;
                }
            }
        }
        if (draggingRange != null) {
            for (Row row : rows) {
                if (row.setting == draggingRange) {
                    updateRange(draggingRange, mouseX, row);
                    break;
                }
            }
        }
    }

    public void mouseReleased() {
        draggingPanel = false;
        draggingSlider = null;
        draggingRange = null;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (collapsed || !GlassTheme.inside(mouseX, mouseY, x, y, width, getHeight())) {
            return false;
        }
        float visible = getHeight() - GlassTheme.PANEL_HEADER_HEIGHT - GlassTheme.PANEL_CONTENT_GAP;
        float maxScroll = Math.max(0.0F, contentHeight() - visible);
        if (maxScroll <= 0.0F) {
            return false;
        }
        float next = Math.max(0.0F, Math.min(maxScroll, scroll - (float) amount * GlassTheme.SCROLL_MULTIPLIER));
        if (next == scroll) {
            return false;
        }
        scroll = next;
        return true;
    }

    private void updateSlider(NumberSetting number, double mouseX, Row row) {
        float trackX = row.x + GlassTheme.SETTING_PADDING;
        float trackW = row.w - GlassTheme.SETTING_PADDING * 2.0F;
        if (trackW <= 0.0F) {
            return;
        }
        number.setNormalized((float) ((mouseX - trackX) / trackW));
    }

    /** Picks the knob closer to the cursor so a drag grabs the end the user aimed at. */
    private static boolean nearestKnobIsHigh(RangeSetting range, double mouseX, Row row) {
        float trackX = row.x + GlassTheme.SETTING_PADDING;
        float trackW = row.w - GlassTheme.SETTING_PADDING * 2.0F;
        float lowX = trackX + trackW * range.normalizedLow();
        float highX = trackX + trackW * range.normalizedHigh();
        return Math.abs(mouseX - highX) < Math.abs(mouseX - lowX);
    }

    private void updateRange(RangeSetting range, double mouseX, Row row) {
        float trackX = row.x + GlassTheme.SETTING_PADDING;
        float trackW = row.w - GlassTheme.SETTING_PADDING * 2.0F;
        if (trackW <= 0.0F) {
            return;
        }
        float fraction = (float) ((mouseX - trackX) / trackW);
        if (draggingRangeHigh) {
            range.setNormalizedHigh(fraction);
        } else {
            range.setNormalizedLow(fraction);
        }
    }

    private record Row(int type, Module module, Setting setting, float x, float y, float w, float h) {
    }
}
