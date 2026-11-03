package dev.perplexity.client.gui.clickgui;

import dev.perplexity.client.module.Category;
import dev.perplexity.client.auth.AuthGate;
import dev.perplexity.client.auth.CurrentAccount;
import dev.perplexity.client.config.ConfigManager;
import dev.perplexity.client.gui.auth.AuthScreen;
import dev.perplexity.client.impl.modules.ui.MenuKeyModule;
import dev.perplexity.client.impl.modules.ui.TabControlModule;
import dev.perplexity.client.module.ModuleManager;
import dev.perplexity.client.module.setting.KeybindSetting;
import dev.perplexity.client.render.vg.VGFont;
import dev.perplexity.client.render.vg.VGRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.awt.Color;

/**
 * The glass ClickGUI. Everything is drawn with the ported NanoVG renderer inside a single frame that
 * a mixin runs after Minecraft submits its own GUI (see {@link ClickGuiOverlay}). Panels are laid out
 * in a centred grid with a shared scroll.
 */
public final class ClickGuiScreen extends Screen {
    private static final float GRID_MARGIN = 24.0F;
    private static final float GLOBAL_SCROLL_SPEED = 60.0F;
    private static final float ACCOUNT_BUTTON_WIDTH = 108.0F;
    private static final float ACCOUNT_BUTTON_HEIGHT = 18.0F;
    private static final float ACCOUNT_MENU_WIDTH = 174.0F;
    private static final float ACCOUNT_MENU_HEIGHT = 78.0F;
    /**
     * GUI scale the interface is authored against. Rendering and every hit-test normalize
     * into this space, so the menu keeps identical size and layout whether the game's GUI
     * scale is 1, 2, 3 or Auto.
     */
    private static final float UI_REFERENCE_SCALE = 2.0F;

    private final Backdrop backdrop = new Backdrop();
    private final List<Panel> panels = new ArrayList<>();
    /** Reused scratch list so layout never allocates per frame. */
    private final List<Panel> visiblePanels = new ArrayList<>();
    private boolean built;
    private float globalScroll;
    private float maxGlobalScroll;
    private int draggingPanel = -1;
    /** Index of the front-most panel. The list order never changes; this only drives draw order. */
    private int focusedPanel = -1;
    private boolean accountOpen;
    private long lastLayoutNanos;

    /** Time for managed panels to settle into a resized/reflowed grid slot. */
    private static final float LAYOUT_FOLLOW_SECONDS = 0.12F;

    private float lastMouseX;
    private float lastMouseY;

    public ClickGuiScreen() {
        super(Component.literal("ClickGui"));
    }

    @Override
    protected void init() {
        if (built) {
            return;
        }
        built = true;
        for (Category category : Category.values()) {
            // Every category keeps its panel even while it has no modules, so the grid stays stable
            // and a category is always in the same place once a module is registered for it.
            panels.add(new Panel(category, ModuleManager.get().byCategory(category), 0.0F, GlassTheme.INITIAL_Y));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int passes = MenuKeyModule.blurPasses();
        for (int i = 0; i < passes; i++) {
            renderBlurredBackground(guiGraphics);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        init();

        // 1.21.9+ only *collects* GUI draw state here; the real GPU submission happens later in
        // GuiRenderer.render, so the VG frame is drawn from ClickGuiOverlay instead.
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        GlassTheme.setDark(ClickGuiConfig.dark);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /** Draws the whole ClickGUI with NanoVG; must run with the main render target bound. */
    public void drawVg() {
        float unit = uiUnitScale();
        float uiWidth = width * unit;
        float uiHeight = height * unit;
        float mouseX = lastMouseX * unit;
        float mouseY = lastMouseY * unit;
        if (!VGRenderer.beginFrame()) {
            return;
        }
        VGFont.setFamily(MenuKeyModule.fontId());
        try {
            VGRenderer.save();
            VGRenderer.scale(UI_REFERENCE_SCALE, UI_REFERENCE_SCALE);

            VGRenderer.rect(0.0F, 0.0F, uiWidth, uiHeight, GlassTheme.scrim());
            backdrop.render(uiWidth, uiHeight);
            drawWatermark();

            layoutPanels(uiWidth, uiHeight);
            // Draw back-to-front with the focused panel last, so raising one never reflows the grid.
            // Off-screen panels are culled outright: same pixels, no tessellation for them.
            for (int i = 0; i < panels.size(); i++) {
                if (i != focusedPanel
                        && TabControlModule.isShown(panels.get(i).getCategory())
                        && onScreen(panels.get(i), uiWidth, uiHeight)) {
                    panels.get(i).render(mouseX, mouseY);
                }
            }
            if (focusedPanel >= 0 && focusedPanel < panels.size()
                    && TabControlModule.isShown(panels.get(focusedPanel).getCategory())
                    && onScreen(panels.get(focusedPanel), uiWidth, uiHeight)) {
                panels.get(focusedPanel).render(mouseX, mouseY);
            }
            drawAccountSettings(uiWidth, mouseX, mouseY);
            drawEditingIndicator(uiWidth, uiHeight);

            VGRenderer.restore();
        } finally {
            VGRenderer.endFrame();
        }
    }

    /**
     * Converts a vanilla GUI-unit coordinate into UI space. Vanilla units shrink as the GUI
     * scale grows; multiplying back up keeps the menu fixed to the reference scale.
     */
    private static float uiUnitScale() {
        return (float) (Minecraft.getInstance().getWindow().getGuiScale() / UI_REFERENCE_SCALE);
    }

    /** True when any part of the panel intersects the viewport. */
    private static boolean onScreen(Panel panel, float uiWidth, float uiHeight) {
        return panel.getX() + panel.getWidth() > 0.0F && panel.getX() < uiWidth
                && panel.getY() + panel.getHeight() > 0.0F && panel.getY() < uiHeight;
    }

    private void layoutPanels(float uiWidth, float uiHeight) {
        long now = System.nanoTime();
        float dt = lastLayoutNanos == 0L
                ? 1.0F / 60.0F
                : Math.min(0.05F, (now - lastLayoutNanos) / 1_000_000_000.0F);
        lastLayoutNanos = now;
        // Exponential follow is framerate-independent. Capping dt above is important after the
        // window was minimized: the first restored frame eases into place instead of teleporting.
        float layoutFollow = 1.0F - (float) Math.exp(-dt / LAYOUT_FOLLOW_SECONDS);

        visiblePanels.clear();
        for (Panel panel : panels) {
            if (TabControlModule.isShown(panel.getCategory())) {
                visiblePanels.add(panel);
            }
        }
        Panel dragging = draggingPanel >= 0 && draggingPanel < panels.size()
                ? panels.get(draggingPanel)
                : null;

        int columns = Math.max(1, (int) ((uiWidth - GRID_MARGIN) / (GlassTheme.PANEL_WIDTH + GlassTheme.PANEL_SPACING)));
        columns = Math.min(columns, Math.max(1, visiblePanels.size()));

        int rows = (visiblePanels.size() + columns - 1) / columns;
        float[] rowHeights = new float[rows];
        for (int i = 0; i < visiblePanels.size(); i++) {
            int row = i / columns;
            rowHeights[row] = Math.max(rowHeights[row], visiblePanels.get(i).getHeight());
        }

        float totalHeight = 0.0F;
        for (float rowHeight : rowHeights) {
            totalHeight += rowHeight + GlassTheme.PANEL_SPACING;
        }
        maxGlobalScroll = Math.max(0.0F, (GlassTheme.INITIAL_Y + totalHeight) - uiHeight + 20.0F);
        globalScroll = Math.max(0.0F, Math.min(globalScroll, maxGlobalScroll));

        float y = GlassTheme.INITIAL_Y - globalScroll;
        for (int row = 0; row < rows; row++) {
            int first = row * columns;
            int count = Math.min(columns, visiblePanels.size() - first);
            float rowWidth = count * GlassTheme.PANEL_WIDTH + (count - 1) * GlassTheme.PANEL_SPACING;
            float startX = (uiWidth - rowWidth) / 2.0F;

            for (int column = 0; column < count; column++) {
                Panel panel = visiblePanels.get(first + column);
                float slotX = startX + column * (GlassTheme.PANEL_WIDTH + GlassTheme.PANEL_SPACING);
                // Slots are still computed for every panel, so the grid stays put as panels are
                // dragged out of it — but a panel the user has placed is left where they dropped it.
                if (panel != dragging && !panel.isPlaced()) {
                    panel.approachGridPosition(slotX, y, layoutFollow);
                }
            }
            y += rowHeights[row] + GlassTheme.PANEL_SPACING;
        }
    }

    private void drawWatermark() {
        String user = CurrentAccount.email();
        String label = "perplexity";
        if (user != null && !user.isEmpty()) {
            String shown = user.length() > 24 ? user.substring(0, 21) + "..." : user;
            label = "perplexity - " + shown;
        }
        VGRenderer.textLeft(label, 12.0F, 16.0F, 10.0F, VGFont.semiboldFace(), 0.6F,
                GlassTheme.headerText());
    }

    /** Account control is intentionally absent for anonymous users who chose Skip. */
    private void drawAccountSettings(float uiWidth, float mouseX, float mouseY) {
        if (!CurrentAccount.isLoggedIn()) {
            accountOpen = false;
            return;
        }

        float buttonX = uiWidth - ACCOUNT_BUTTON_WIDTH - 12.0F;
        float buttonY = 7.0F;
        boolean buttonHover = GlassTheme.inside(mouseX, mouseY, buttonX, buttonY,
                ACCOUNT_BUTTON_WIDTH, ACCOUNT_BUTTON_HEIGHT);
        VGRenderer.roundedRect(buttonX, buttonY, ACCOUNT_BUTTON_WIDTH, ACCOUNT_BUTTON_HEIGHT,
                GlassTheme.RADIUS_MD, buttonHover || accountOpen
                        ? GlassTheme.moduleHover() : GlassTheme.groupFill());
        VGRenderer.roundedOutline(buttonX, buttonY, ACCOUNT_BUTTON_WIDTH, ACCOUNT_BUTTON_HEIGHT,
                GlassTheme.RADIUS_MD, 1.0F,
                accountOpen ? GlassTheme.accent(0.8F) : GlassTheme.panelBorder());
        VGRenderer.textLeft("Account", buttonX + 10.0F,
                buttonY + ACCOUNT_BUTTON_HEIGHT * 0.5F, 8.0F,
                VGFont.mediumFace(), GlassTheme.textPrimary());

        if (!accountOpen) return;

        float menuX = uiWidth - ACCOUNT_MENU_WIDTH - 12.0F;
        float menuY = buttonY + ACCOUNT_BUTTON_HEIGHT + 5.0F;
        VGRenderer.shadow(menuX, menuY, ACCOUNT_MENU_WIDTH, ACCOUNT_MENU_HEIGHT,
                GlassTheme.RADIUS_LG, 12.0F, new Color(0, 0, 0, 130));
        VGRenderer.verticalGradient(menuX, menuY, ACCOUNT_MENU_WIDTH, ACCOUNT_MENU_HEIGHT,
                GlassTheme.RADIUS_LG, GlassTheme.surfacePanel(), GlassTheme.surfacePanelBottom());
        VGRenderer.roundedOutline(menuX, menuY, ACCOUNT_MENU_WIDTH, ACCOUNT_MENU_HEIGHT,
                GlassTheme.RADIUS_LG, GlassTheme.PANEL_BORDER, GlassTheme.panelBorder());

        VGRenderer.textLeft("Signed in as", menuX + 10.0F, menuY + 13.0F,
                7.0F, VGFont.face(), GlassTheme.textMuted());
        VGRenderer.textLeft(shortEmail(CurrentAccount.email()), menuX + 10.0F, menuY + 28.0F,
                8.0F, VGFont.mediumFace(), GlassTheme.textPrimary());

        float signOutX = menuX + 8.0F;
        float signOutY = menuY + 46.0F;
        float signOutWidth = ACCOUNT_MENU_WIDTH - 16.0F;
        float signOutHeight = 23.0F;
        boolean signOutHover = GlassTheme.inside(mouseX, mouseY, signOutX, signOutY,
                signOutWidth, signOutHeight);
        VGRenderer.roundedRect(signOutX, signOutY, signOutWidth, signOutHeight,
                GlassTheme.RADIUS_MD, signOutHover
                        ? GlassTheme.accent(0.9F) : GlassTheme.interactiveMuted());
        VGRenderer.textCentered("Sign Out", menuX + ACCOUNT_MENU_WIDTH * 0.5F,
                signOutY + signOutHeight * 0.5F, 8.0F, VGFont.semiboldFace(),
                signOutHover ? GlassTheme.textOnAccent() : GlassTheme.textPrimary());
    }

    private static String shortEmail(String email) {
        if (email == null) return "";
        return email.length() > 27 ? email.substring(0, 24) + "..." : email;
    }

    private void drawEditingIndicator(float uiWidth, float uiHeight) {
        boolean editing = false;
        for (Panel panel : panels) {
            if (panel.isEditing()) {
                editing = true;
                break;
            }
        }
        if (!editing) return;

        String label = "Editing mode";
        float textWidth = VGRenderer.textWidth(label, 8.0F, VGFont.mediumFace(), 0.0F);
        float pillWidth = textWidth + 24.0F;
        float pillHeight = 21.0F;
        float pillX = (uiWidth - pillWidth) * 0.5F;
        float pillY = uiHeight - pillHeight - 10.0F;
        VGRenderer.shadow(pillX, pillY, pillWidth, pillHeight,
                pillHeight * 0.5F, 9.0F, new Color(0, 0, 0, 120));
        VGRenderer.roundedRect(pillX, pillY, pillWidth, pillHeight,
                pillHeight * 0.5F, GlassTheme.accent(0.88F));
        VGRenderer.textCentered(label, uiWidth * 0.5F, pillY + pillHeight * 0.5F,
                8.0F, VGFont.mediumFace(), GlassTheme.textOnAccent());
    }

    // --- Input ---------------------------------------------------------------------------------

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (KeybindSetting.capture(event.key())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        float unit = uiUnitScale();
        double mouseX = event.x() * unit;
        double mouseY = event.y() * unit;
        int button = event.button();

        if (button == 0 && CurrentAccount.isLoggedIn()) {
            float uiWidth = width * unit;
            float accountX = uiWidth - ACCOUNT_BUTTON_WIDTH - 12.0F;
            float accountY = 7.0F;
            if (GlassTheme.inside(mouseX, mouseY, accountX, accountY,
                    ACCOUNT_BUTTON_WIDTH, ACCOUNT_BUTTON_HEIGHT)) {
                accountOpen = !accountOpen;
                return true;
            }
            if (accountOpen) {
                float menuX = uiWidth - ACCOUNT_MENU_WIDTH - 12.0F;
                float menuY = accountY + ACCOUNT_BUTTON_HEIGHT + 5.0F;
                if (GlassTheme.inside(mouseX, mouseY, menuX + 8.0F, menuY + 46.0F,
                        ACCOUNT_MENU_WIDTH - 16.0F, 23.0F)) {
                    signOut();
                    return true;
                }
                if (!GlassTheme.inside(mouseX, mouseY, menuX, menuY,
                        ACCOUNT_MENU_WIDTH, ACCOUNT_MENU_HEIGHT)) {
                    accountOpen = false;
                }
            }
        }

        // Front-to-back hit test: the focused panel gets first refusal, then the rest top-down.
        if (focusedPanel >= 0 && focusedPanel < panels.size()
                && TabControlModule.isShown(panels.get(focusedPanel).getCategory())
                && panels.get(focusedPanel).mouseClicked(mouseX, mouseY, button)) {
            focus(focusedPanel, mouseX, mouseY, button);
            return true;
        }
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (i == focusedPanel) {
                continue;
            }
            if (!TabControlModule.isShown(panels.get(i).getCategory())) {
                continue;
            }
            if (panels.get(i).mouseClicked(mouseX, mouseY, button)) {
                focus(i, mouseX, mouseY, button);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void signOut() {
        ConfigManager.onAccountChanged(null);
        CurrentAccount.clear();
        AuthGate.reset();
        accountOpen = false;
        if (minecraft != null) {
            minecraft.setScreen(new AuthScreen());
        }
    }

    private void focus(int index, double mouseX, double mouseY, int button) {
        focusedPanel = index;
        // Tested against where the panel actually is, not its grid slot: a placed panel no longer
        // lives at its slot, and testing the slot would make it impossible to pick up again.
        Panel panel = panels.get(index);
        if (button == 0 && GlassTheme.inside(mouseX, mouseY, panel.getX(), panel.getY(),
                panel.getWidth(), GlassTheme.PANEL_HEADER_HEIGHT)
                && !panel.isEditButton(mouseX, mouseY)) {
            draggingPanel = index;
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        for (Panel panel : panels) {
            panel.mouseReleased();
        }
        if (draggingPanel >= 0 && draggingPanel < panels.size()) {
            Panel panel = panels.get(draggingPanel);
            // Only a press that actually moved the panel pins it to its new spot. A plain click on a
            // header must leave the panel grid-managed, or merely clicking would stop it reflowing.
            if (panel.consumeMoved()) {
                clampToScreen(panel);
                panel.setPlaced(true);
            }
        }
        draggingPanel = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        float unit = uiUnitScale();
        for (Panel panel : panels) {
            if (TabControlModule.isShown(panel.getCategory())) {
                panel.mouseDragged(event.x() * unit, event.y() * unit, event.button());
            }
        }
        if (draggingPanel >= 0 && draggingPanel < panels.size()) {
            clampToScreen(panels.get(draggingPanel));
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    /**
     * Keeps a dragged panel on screen. Positions now persist, so a panel dragged past an edge would
     * otherwise be lost with no way to grab it again.
     */
    private void clampToScreen(Panel panel) {
        float unit = uiUnitScale();
        float maxX = Math.max(0.0F, width * unit - panel.getWidth());
        float maxY = Math.max(0.0F, height * unit - GlassTheme.PANEL_HEADER_HEIGHT);
        panel.setX(Math.max(0.0F, Math.min(maxX, panel.getX())));
        panel.setY(Math.max(0.0F, Math.min(maxY, panel.getY())));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float unit = uiUnitScale();
        double uiMouseX = mouseX * unit;
        double uiMouseY = mouseY * unit;
        if (focusedPanel >= 0 && focusedPanel < panels.size()
                && TabControlModule.isShown(panels.get(focusedPanel).getCategory())
                && panels.get(focusedPanel).mouseScrolled(uiMouseX, uiMouseY, scrollY)) {
            return true;
        }
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (i == focusedPanel) {
                continue;
            }
            if (!TabControlModule.isShown(panels.get(i).getCategory())) {
                continue;
            }
            if (panels.get(i).mouseScrolled(uiMouseX, uiMouseY, scrollY)) {
                return true;
            }
        }
        if (scrollY != 0.0 && maxGlobalScroll > 0.0F) {
            globalScroll = Math.max(0.0F, Math.min(maxGlobalScroll,
                    globalScroll - (float) scrollY * GLOBAL_SCROLL_SPEED));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
