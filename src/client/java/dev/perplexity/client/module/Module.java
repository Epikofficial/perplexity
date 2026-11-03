package dev.perplexity.client.module;

import dev.perplexity.client.event.PlayerEvents;
import dev.perplexity.client.event.ClientEvents;
import dev.perplexity.client.event.RenderEvents;
import dev.perplexity.client.gui.Toasts;
import dev.perplexity.client.module.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    private final Category category;
    private final List<Setting> settings = new ArrayList<>();
    /** Cached unmodifiable view: settings are all added during construction, so this never goes stale. */
    private List<Setting> settingsView;

    private boolean enabled;
    private int keybind;
    private boolean expanded;
    private boolean visibleInMenu = true;

    protected Module(Category category) {
        this.category = category;
    }

    protected <T extends Setting> T add(T setting) {
        settings.add(setting);
        return setting;
    }

    /**
     * Original display name, decoded fresh on every call and never stored: the plaintext
     * exists only transiently while drawn. Implementations return an Obf blob decode.
     */
    public abstract String getDisplayName();

    public Category getCategory() {
        return category;
    }

    public List<Setting> getSettings() {
        if (settingsView == null) {
            settingsView = Collections.unmodifiableList(settings);
        }
        return settingsView;
    }

    public boolean hasSettings() {
        return !settings.isEmpty();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        if (this.enabled == value) {
            return;
        }
        this.enabled = value;
        Toasts.push(this, value);
        if (value) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public int getKeybind() {
        return keybind;
    }

    public void setKeybind(int keybind) {
        this.keybind = keybind;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public boolean isVisibleInMenu() {
        return visibleInMenu;
    }

    public void setVisibleInMenu(boolean visibleInMenu) {
        this.visibleInMenu = visibleInMenu;
        if (!visibleInMenu) {
            expanded = false;
        }
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    protected void onTick() {
    }

    /** Global lifecycle; unlike player ticks, this also runs in menus and disconnect screens. */
    protected void onClientTick(ClientEvents.TickEvent event) {
        if (event.phase() == PlayerEvents.Phase.AFTER) {
            onTick();
        }
    }

    /** Per-frame rendering boundary published by GameRenderer's mixin. */
    protected void onFrame(RenderEvents.FrameEvent event) {
    }

    /** Context-rich world stages for modules that submit world geometry. */
    protected void onWorldRender(RenderEvents.WorldEvent event) {
    }

    /** Precise local-player lifecycle hooks published by the player mixins. */
    protected void onPlayerTick(PlayerEvents.TickEvent event) {
    }

    protected void onPlayerMove(PlayerEvents.MoveEvent event) {
    }

    protected void onPlayerAttack(PlayerEvents.AttackEvent event) {
    }

    protected void onPlayerSwing(PlayerEvents.SwingEvent event) {
    }

    protected void onPlayerJump(PlayerEvents.JumpEvent event) {
    }

    protected void onPlayerDamage(PlayerEvents.DamageEvent event) {
    }

    protected void onPlayerCorrection(PlayerEvents.CorrectionEvent event) {
    }

    /** Runs the module's per-tick hook. Called by {@link ModuleManager} for enabled modules only. */
    public void tick() {
        if (!enabled) {
            return;
        }
        onTick();
    }

    final void clientTick(ClientEvents.TickEvent event) {
        if (enabled) onClientTick(event);
    }

    final void frame(RenderEvents.FrameEvent event) {
        if (enabled) onFrame(event);
    }

    final void worldRender(RenderEvents.WorldEvent event) {
        if (enabled) onWorldRender(event);
    }

    final void playerTick(PlayerEvents.TickEvent event) {
        if (enabled) onPlayerTick(event);
    }

    final void playerMove(PlayerEvents.MoveEvent event) {
        if (enabled) onPlayerMove(event);
    }

    final void playerAttack(PlayerEvents.AttackEvent event) {
        if (enabled) onPlayerAttack(event);
    }

    final void playerSwing(PlayerEvents.SwingEvent event) {
        if (enabled) onPlayerSwing(event);
    }

    final void playerJump(PlayerEvents.JumpEvent event) {
        if (enabled) onPlayerJump(event);
    }

    final void playerDamage(PlayerEvents.DamageEvent event) {
        if (enabled) onPlayerDamage(event);
    }

    final void playerCorrection(PlayerEvents.CorrectionEvent event) {
        if (enabled) onPlayerCorrection(event);
    }
}
