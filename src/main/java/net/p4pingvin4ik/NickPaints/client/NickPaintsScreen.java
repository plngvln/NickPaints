package net.p4pingvin4ik.NickPaints.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import org.lwjgl.glfw.GLFW;
import net.minecraft.text.Text;
import net.p4pingvin4ik.NickPaints.client.gui.ColorPickerPopup;
import net.p4pingvin4ik.NickPaints.client.gui.FlatButton;
import net.p4pingvin4ik.NickPaints.client.gui.GradientEditorState;
import net.p4pingvin4ik.NickPaints.client.gui.GradientPreviewRenderer;
import net.p4pingvin4ik.NickPaints.client.gui.IntSliderWidget;
import net.p4pingvin4ik.NickPaints.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class NickPaintsScreen extends Screen {

    // ── Palette (monochrome) ─────────────────────────────────────────────────
    private static final int C_BG           = 0xF2101010;
    private static final int C_SURFACE      = 0xFF191919;
    private static final int C_ELEVATED     = 0xFF222222;
    private static final int C_BORDER       = 0xFF323232;
    private static final int C_BORDER_LIT   = 0xFF505050;
    private static final int C_OVERLAY      = 0xAA000000;
    private static final int C_ACCENT_BAR   = 0xFF646464;
    private static final int C_TEXT_PRI     = 0xFFE6E6E6;
    private static final int C_TEXT_SEC     = 0xFF848484;
    private static final int C_TEXT_HINT    = 0xFF4A4A4A;

    // ── Layout ──────────────────────────────────────────────────────────────
    private static final int PAD        = 12;
    private static final int GAP        = 5;
    private static final int TAB_H      = 24;
    private static final int BTN_H      = 20;
    private static final int ROW_PRE    = 16;
    private static final int ROW_DIS    = 22;
    private static final int ROW_SUG    = 18;

    private static final int COLOR_ROW_STRIDE = 22 + GAP;
    private static final int COLOR_ADD_STRIP = BTN_H + GAP;

    // ── State ────────────────────────────────────────────────────────────────
    private final GradientEditorState editor = new GradientEditorState();

    private int panelX, panelY, panelW, panelH;
    private int contentLeft, contentTop, contentW, contentBottom;

    private int activeTab = 0;

    private final List<ClickableWidget> tabWidgets = new ArrayList<>();

    private TextFieldWidget playerDisableField;

    private int colorHexFocusIndex = -1;
    private String colorHexEditBuffer = "";
    private int colorHexCursor = 0;

    private boolean tutorialActive;
    private boolean firstInit = true;

    // Scroll offsets
    private int editorScroll    = 0;
    private int presetsScroll   = 0;
    private int disabledScroll  = 0;
    private int suggestScroll   = 0;

    // Editor left-column total content height (for scroll clamping)
    private int editorContentH  = 0;

    // Presets list bounds (within panel)
    private int presetsListTop, presetsListBottom;

    // Settings list bounds
    private int disabledListTop, disabledListBottom;
    private int suggestListTop,  suggestListBottom;

    // Two-column layout for editor
    private int col1X, col1W, col2X, col2W;

    // Color picker popup
    private final ColorPickerPopup colorPicker = new ColorPickerPopup();

    // Which color index is being edited by the picker
    private int pickerColorIndex = -1;

    // ────────────────────────────────────────────────────────────────────────
    // INIT
    // ────────────────────────────────────────────────────────────────────────

    public NickPaintsScreen() {
        super(Text.translatable("gui.nickpaints.title"));
    }

    @Override
    protected void init() {
        super.init();

        for (ClickableWidget w : tabWidgets) remove(w);
        tabWidgets.clear();
        blurColorHexEdit(false);
        colorPicker.close();

        panelW = Math.min(560, this.width  - 24);
        panelH = Math.min((int)(this.height * 0.93), this.height - 12);
        panelX = (this.width  - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        int headerH   = PAD + TAB_H + PAD;
        contentLeft   = panelX + PAD;
        contentTop    = panelY + headerH;
        contentW      = panelW - PAD * 2;
        contentBottom = panelY + panelH - PAD;

        col1W = (contentW - GAP * 3) / 2;
        col1X = contentLeft;
        col2X = contentLeft + col1W + GAP * 3;
        col2W = contentW - col1W - GAP * 3;

        if (firstInit) {
            editor.parseGradientString(ConfigManager.CONFIG.currentGradient);
            tutorialActive = !ConfigManager.CONFIG.hasCompletedGuiTutorial;
            firstInit = false;
        }

        buildTabButtons();

        if (activeTab == 0) buildEditorWidgets();
        else                buildSettingsWidgets();
    }

    // ── Tab buttons ──────────────────────────────────────────────────────────

    private FlatButton tabEditorBtn, tabSettingsBtn;

    private void buildTabButtons() {
        int tabW = (panelW - PAD * 2 - GAP) / 2;
        int tabY = panelY + PAD;

        if (tabEditorBtn  != null) remove(tabEditorBtn);
        if (tabSettingsBtn != null) remove(tabSettingsBtn);

        tabEditorBtn = FlatButton.create(Text.translatable("gui.nickpaints.tab.editor"), b -> switchTab(0))
                .dimensions(panelX + PAD, tabY, tabW, TAB_H)
                .variant(FlatButton.Variant.NORMAL)
                .toggled(activeTab == 0)
                .build();

        tabSettingsBtn = FlatButton.create(Text.translatable("gui.nickpaints.tab.settings"), b -> switchTab(1))
                .dimensions(panelX + PAD + tabW + GAP, tabY, tabW, TAB_H)
                .variant(FlatButton.Variant.NORMAL)
                .toggled(activeTab == 1)
                .build();

        addDrawableChild(tabEditorBtn);
        addDrawableChild(tabSettingsBtn);
    }

    private void switchTab(int tab) {
        if (activeTab == 0 && tab == 1) {
            blurColorHexEdit(true);
        }
        activeTab = tab;
        editorScroll = 0;
        presetsScroll = 0;
        disabledScroll = 0;
        suggestScroll = 0;
        init();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EDITOR TAB
    // ─────────────────────────────────────────────────────────────────────────

    private void buildEditorWidgets() {
        // ── LEFT COLUMN (scrollable) ─────────────────────────────────────────

        rebuildEditorLeftWidgets();

        // ── RIGHT COLUMN ─────────────────────────────────────────────────────
        int y2 = contentTop + 38;

        if (!editor.rainbowMode) {
            IntSliderWidget spdSlider = new IntSliderWidget(col2X, y2, col2W, Text.translatable("gui.nickpaints.option.speed"), 1000, 20000, editor.speed, s -> editor.speed = s.getIntValue());
            spdSlider.active = !editor.staticGradient;
            addTab(spdSlider);
            y2 += BTN_H + GAP;

            IntSliderWidget segSlider = new IntSliderWidget(col2X, y2, col2W, Text.translatable("gui.nickpaints.option.segment"), 1, 200, editor.segment, s -> editor.segment = s.getIntValue());
            addTab(segSlider);
            y2 += BTN_H + GAP;

            IntSliderWidget angSlider = new IntSliderWidget(col2X, y2, col2W, Text.translatable("gui.nickpaints.option.angle"), 0, 360, editor.angle, s -> editor.angle = s.getIntValue());
            addTab(angSlider);
            y2 += BTN_H + GAP * 2;
        }

        int hw = (col2W - GAP) / 2;
        addTab(FlatButton.create(Text.translatable("gui.nickpaints.button.copy"),  b -> client.keyboard.setClipboard(editor.reconstructGradientString()))
                .dimensions(col2X, y2, hw, BTN_H).build());
        addTab(FlatButton.create(Text.translatable("gui.nickpaints.button.paste"), b -> {
            String clip = client.keyboard.getClipboard();
            if (clip != null && !clip.isEmpty()) { editor.parseGradientString(clip.trim()); init(); }
        }).dimensions(col2X + hw + GAP, y2, hw, BTN_H).build());
        y2 += BTN_H + GAP;

        addTab(FlatButton.create(Text.translatable("gui.nickpaints.button.save_sync"), b -> {
            ConfigManager.CONFIG.currentGradient = editor.reconstructGradientString();
            ConfigManager.saveConfig();
            if (client.player != null) WebSocketManager.syncMyPaint();
        }).dimensions(col2X, y2, col2W, BTN_H).variant(FlatButton.Variant.PRIMARY).build());
        y2 += BTN_H + GAP;

        int bottomActionsY = contentBottom - BTN_H;
        presetsListTop    = y2 + 14;
        presetsListBottom = bottomActionsY - GAP;

        addTab(FlatButton.create(Text.translatable("gui.nickpaints.button.save_preset"), b -> {
            ConfigManager.CONFIG.presets.add(editor.reconstructGradientString());
            ConfigManager.saveConfig();
        }).dimensions(col2X, bottomActionsY, col2W, BTN_H).build());
    }

    private void rebuildEditorLeftWidgets() {
        int y = contentTop + 38;

        // Rainbow toggle
        addTab(FlatButton.create(
                        Text.translatable("gui.nickpaints.mode.rainbow"),
                        b -> { editor.rainbowMode = !editor.rainbowMode; init(); })
                .dimensions(col1X, y, col1W, BTN_H)
                .toggled(editor.rainbowMode)
                .build());
        y += BTN_H + GAP;

        if (editor.rainbowMode) {
            IntSliderWidget rSpd = new IntSliderWidget(col1X, y, col1W, Text.translatable("gui.nickpaints.option.speed"), 1000, 20000, editor.rainbowSpeed, s -> editor.rainbowSpeed = s.getIntValue());
            addTab(rSpd);
            y += BTN_H + GAP;
            editorContentH = y - (contentTop + 38);
        } else {
            int hw = (col1W - GAP) / 2;
            addTab(FlatButton.create(Text.translatable("gui.nickpaints.option.static"), b -> { editor.staticGradient = !editor.staticGradient; init(); })
                    .dimensions(col1X, y, hw, BTN_H).toggled(editor.staticGradient).build());
            addTab(FlatButton.create(Text.translatable("gui.nickpaints.option.style_block"), b -> { editor.blockStyle = !editor.blockStyle; init(); })
                    .dimensions(col1X + hw + GAP, y, hw, BTN_H).toggled(editor.blockStyle).build());
            y += BTN_H + GAP * 2;

            y += textRenderer.fontHeight + GAP;

            FlatButton addBtn = FlatButton.create(Text.translatable("gui.nickpaints.button.add_color"), b -> {
                editor.colors.add(new float[]{1f, 1f, 1f});

                int colorsTotal = editor.colors.size() * COLOR_ROW_STRIDE;
                int listTop = editorColorsListTop();
                int colorsVisible = editorColorsRowsBottom() - listTop;
                editorScroll = Math.max(0, colorsTotal - colorsVisible);

                init();
            }).dimensions(col1X, contentBottom - BTN_H, col1W, BTN_H).build();
            addTab(addBtn);

            editorContentH = editor.colors.size() * COLOR_ROW_STRIDE;

            clampEditorScroll();
            if (colorHexFocusIndex >= editor.colors.size()) {
                blurColorHexEdit(false);
            }
        }
    }

    private int editorColorsLabelY() {
        return contentTop + 38 + BTN_H + GAP + BTN_H + GAP * 2;
    }

    /** First color row Y (scrollable list top). */
    private int editorColorsListTop() {
        return editorColorsLabelY() + textRenderer.fontHeight + GAP;
    }

    /** Bottom edge of the scrollable color rows (above pinned «Добавить цвет»). */
    private int editorColorsRowsBottom() {
        int raw = contentBottom - COLOR_ADD_STRIP;
        return Math.max(editorColorsListTop() + 8, raw);
    }

    private void clampEditorScroll() {
        if (editor.rainbowMode) return;
        int visible = editorColorsRowsBottom() - editorColorsListTop();
        int max = Math.max(0, editorContentH - visible);
        editorScroll = Math.max(0, Math.min(max, editorScroll));
    }

    private void blurColorHexEdit(boolean apply) {
        if (colorHexFocusIndex >= 0 && apply && colorHexFocusIndex < editor.colors.size()) {
            applyHexColor(colorHexFocusIndex, colorHexEditBuffer);
        }
        colorHexFocusIndex = -1;
        colorHexEditBuffer = "";
        colorHexCursor = 0;
    }

    private void beginColorHexEdit(int index) {
        blurColorHexEdit(true);
        if (index < 0 || index >= editor.colors.size()) return;
        colorHexFocusIndex = index;
        float[] rgb = editor.colors.get(index);
        colorHexEditBuffer = String.format("#%02x%02x%02x", (int)(rgb[0]*255), (int)(rgb[1]*255), (int)(rgb[2]*255));
        colorHexCursor = colorHexEditBuffer.length();
    }

    private static boolean isHexInputChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == '#';
    }

    /** Swatch + hex + delete for one row; same geometry as hit-testing. */
    private void renderEditorColorRows(DrawContext ctx, int mx, int my) {
        int listTop = editorColorsListTop();
        int listBottom = editorColorsRowsBottom();
        int n = editor.colors.size();
        if (n == 0) return;

        ctx.enableScissor(col1X, listTop, col1X + col1W, listBottom);
        int y = listTop - editorScroll;
        for (int i = 0; i < n; i++) {
            if (y + COLOR_ROW_STRIDE <= listTop) {
                y += COLOR_ROW_STRIDE;
                continue;
            }
            if (y >= listBottom) break;

            float[] rgb = editor.colors.get(i);
            int sw = 0xFF000000 | ((int)(rgb[0]*255) << 16) | ((int)(rgb[1]*255) << 8) | (int)(rgb[2]*255);
            boolean hovSw = inside(col1X, y, 18, 18, mx, my);
            ctx.fill(col1X, y, col1X + 18, y + 18, sw);
            drawBorder(ctx, col1X, y, 18, 18, hovSw ? C_BORDER_LIT : C_BORDER);
            if (hovSw) ctx.fill(col1X, y, col1X + 18, y + 18, 0x18000000);

            int fieldX = col1X + 22;
            int fieldW = col1W - 22 - (n > 1 ? 34 : 4);
            String hexShow = (i == colorHexFocusIndex) ? colorHexEditBuffer
                    : String.format("#%02x%02x%02x", (int)(rgb[0]*255), (int)(rgb[1]*255), (int)(rgb[2]*255));
            boolean hexFocus = i == colorHexFocusIndex;
            boolean hovHex = inside(fieldX, y, fieldW, 18, mx, my);
            ctx.fill(fieldX, y, fieldX + fieldW, y + 18, 0xFF0D0D0D);
            drawBorder(ctx, fieldX, y, fieldW, 18, (hexFocus || hovHex) ? C_BORDER_LIT : C_BORDER);
            ctx.drawTextWithShadow(textRenderer, Text.literal(hexShow), fieldX + 4, y + 5, C_TEXT_PRI);
            if (hexFocus && (System.currentTimeMillis() / 500) % 2 == 0) {
                int safeLen = Math.min(colorHexCursor, hexShow.length());
                int cx = fieldX + 4 + textRenderer.getWidth(hexShow.substring(0, safeLen));
                ctx.fill(cx, y + 3, cx + 1, y + 15, 0xFFAAAAAA);
            }

            if (n > 1) {
                int dx = col1X + col1W - 30;
                int dy = y - 1;
                boolean hovDel = inside(dx, dy, 30, BTN_H, mx, my);
                ctx.fill(dx, dy, dx + 30, dy + BTN_H, hovDel ? 0xFF321A1A : 0xFF221414);
                drawBorder(ctx, dx, dy, 30, BTN_H, hovDel ? 0xFF5A2020 : C_BORDER);
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("✕"), dx + 15, dy + 6, hovDel ? 0xFFCC6666 : 0xFF774444);
            }

            y += COLOR_ROW_STRIDE;
        }
        ctx.disableScissor();
    }

    /** @return true if click was handled (including blur on empty area inside list). */
    private boolean handleEditorColorListClick(double mx, double my, int button) {
        if (button != 0) return false;
        int listTop = editorColorsListTop();
        int listBottom = editorColorsRowsBottom();
        int imx = (int) mx, imy = (int) my;
        if (!inside(col1X, listTop, col1W, listBottom - listTop, imx, imy)) return false;

        int n = editor.colors.size();
        int rowY = listTop - editorScroll;
        for (int i = 0; i < n; i++) {
            if (rowY + COLOR_ROW_STRIDE <= listTop) {
                rowY += COLOR_ROW_STRIDE;
                continue;
            }
            if (rowY >= listBottom) break;

            if (inside(col1X, rowY, 18, 18, imx, imy)) {
                blurColorHexEdit(true);
                openPickerFor(i);
                return true;
            }
            int fieldX = col1X + 22;
            int fieldW = col1W - 22 - (n > 1 ? 34 : 4);
            if (inside(fieldX, rowY, fieldW, 18, imx, imy)) {
                beginColorHexEdit(i);
                return true;
            }
            if (n > 1) {
                int dx = col1X + col1W - 30;
                int dy = rowY - 1;
                if (inside(dx, dy, 30, BTN_H, imx, imy)) {
                    blurColorHexEdit(true);
                    editor.colors.remove(i);
                    init();
                    return true;
                }
            }
            rowY += COLOR_ROW_STRIDE;
        }
        blurColorHexEdit(true);
        return true;
    }
    // ─────────────────────────────────────────────────────────────────────────
    // SETTINGS TAB
    // ─────────────────────────────────────────────────────────────────────────

    private void buildSettingsWidgets() {
        int x = contentLeft, y = contentTop, w = contentW;

        addTab(FlatButton.create(
                        Text.translatable("gui.nickpaints.settings.global_rendering"),
                        b -> { ConfigManager.CONFIG.setGlobalRendering(!ConfigManager.CONFIG.globalRenderingEnabled); ConfigManager.saveConfig(); init(); })
                .dimensions(x, y, w, BTN_H)
                .toggled(ConfigManager.CONFIG.globalRenderingEnabled)
                .build());
        y += BTN_H + GAP;

        addTab(FlatButton.create(
                        Text.translatable("gui.nickpaints.settings.show_own_nametag"),
                        b -> { ConfigManager.CONFIG.showOwnNametag = !ConfigManager.CONFIG.showOwnNametag; ConfigManager.saveConfig(); init(); })
                .dimensions(x, y, w, BTN_H)
                .toggled(ConfigManager.CONFIG.showOwnNametag)
                .build());
        y += BTN_H + GAP * 3;

        // Label for disable field — drawn in renderSettingsOverlay, reserve space
        y += textRenderer.fontHeight + GAP * 2;

        // Disable-player row
        int fieldW = w - 90;
        playerDisableField = new TextFieldWidget(textRenderer, x, y, fieldW, 20, Text.empty());
        playerDisableField.setMaxLength(32);
        addTab(playerDisableField);

        addTab(FlatButton.create(Text.translatable("gui.nickpaints.settings.disable_button"), b -> {
            String username = playerDisableField.getText().trim();
            if (!username.isEmpty()) {
                MojangAPIHelper.getUuidForUsername(username).thenAccept(uuidOpt -> uuidOpt.ifPresent(raw -> {
                    UUID uuid = (UUID) raw;
                    client.execute(() -> {
                        ConfigManager.CONFIG.setPlayerRendering(uuid, username, false);
                        ConfigManager.saveConfig();
                        playerDisableField.setText("");
                    });
                }));
            }
        }).dimensions(x + fieldW + GAP, y, 84, 20).build());
        y += 28;

        suggestListTop    = y;
        suggestListBottom = y + 40;
        y = suggestListBottom + 10;

        disabledListTop    = y + textRenderer.fontHeight + GAP;
        disabledListBottom = contentBottom - 30;

        addTab(FlatButton.create(Text.translatable("gui.nickpaints.settings.clear_cache_button"), b -> WebSocketManager.clearCache())
                .dimensions(x, contentBottom - BTN_H, w, BTN_H)
                .build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RENDER
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Dim
        ctx.fill(0, 0, width, height, C_OVERLAY);

        // Panel shadow + fill
        ctx.fill(panelX + 2, panelY + 2, panelX + panelW + 2, panelY + panelH + 2, 0x55000000);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, C_BG);
        drawBorder(ctx, panelX, panelY, panelW, panelH, C_BORDER);

        // Separator under tab bar
        int sepY = panelY + PAD + TAB_H + PAD - 2;
        ctx.fill(panelX + 1, sepY, panelX + panelW - 1, sepY + 1, C_BORDER);

        // MC-rendered widgets (tabs + tab content)
        super.render(ctx, mx, my, delta);

        // Tab-specific overlays
        if (activeTab == 0) renderEditorOverlay(ctx, mx, my);
        else                renderSettingsOverlay(ctx, mx, my);

        // Color picker on top
        colorPicker.render(ctx, textRenderer, mx, my);

        // Tutorial on very top
        if (tutorialActive) renderTutorial(ctx, mx, my);
    }

    private void renderEditorOverlay(DrawContext ctx, int mx, int my) {
        String name = client.player != null ? client.player.getName().getString() : "Preview";
        String grad = editor.reconstructGradientString();

        GradientPreviewRenderer.drawPreview(ctx, textRenderer, contentLeft, contentTop + 6, contentW, grad, name, true);

        int len = grad.length();
        String lenStr = len + " / 256";
        int lenColor  = len > 230 ? 0xFF886666 : C_TEXT_HINT;
        ctx.drawTextWithShadow(textRenderer, Text.literal(lenStr), contentLeft + contentW - textRenderer.getWidth(lenStr), contentTop + 20, lenColor);

        if (!editor.rainbowMode) {
            int labelY = editorColorsLabelY();
            int colorsAreaTop    = editorColorsListTop();
            int colorsAreaBottom = editorColorsRowsBottom();
            int addStripTop      = colorsAreaBottom;

            drawSectionLabel(ctx, Text.translatable("gui.nickpaints.section.colors").getString(), col1X, labelY);

            ctx.fill(col1X, colorsAreaTop, col1X + col1W, colorsAreaBottom, 0x28000000);
            drawBorder(ctx, col1X, colorsAreaTop, col1W, colorsAreaBottom - colorsAreaTop, C_BORDER);

            ctx.fill(col1X, addStripTop, col1X + col1W, contentBottom, 0x18000000);

            renderEditorColorRows(ctx, mx, my);

            int colorsVisible = colorsAreaBottom - colorsAreaTop;
            if (editorContentH > colorsVisible) {
                int barX = col1X + col1W + 4;
                int barW = 4;
                ctx.fill(barX - 1, colorsAreaTop, barX + barW + 1, colorsAreaBottom, 0xFF141414);
                drawScrollBar(ctx, barX, colorsAreaTop, barW, colorsVisible, editorScroll, editorContentH);
            }
        }

        drawSectionLabel(ctx, Text.translatable("gui.nickpaints.button.save_preset").getString(), col2X, presetsListTop - textRenderer.fontHeight - GAP);
        renderPresetsList(ctx, mx, my);
    }

    private void renderSettingsOverlay(DrawContext ctx, int mx, int my) {
        // Label above disable field — positioned to match widget Y in buildSettingsWidgets
        // two toggles + GAP*3 + fontHeight + GAP*2 = label baseline
        int labelY = contentTop + (BTN_H + GAP) + (BTN_H + GAP * 3) + GAP;
        ctx.drawTextWithShadow(textRenderer, Text.translatable("gui.nickpaints.settings.disable_player_label"), contentLeft, labelY, C_TEXT_SEC);

        renderSuggestionsList(ctx, mx, my);

        // Disabled list header
        drawSectionLabel(ctx, Text.translatable("gui.nickpaints.settings.disabled_list").getString(), contentLeft, disabledListTop - textRenderer.fontHeight - GAP);
        renderDisabledList(ctx, mx, my);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIST RENDERERS
    // ─────────────────────────────────────────────────────────────────────────

    private void renderPresetsList(DrawContext ctx, int mx, int my) {
        List<String> presets = ConfigManager.CONFIG.presets;
        if (presets.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("—"), col2X, presetsListTop + 4, C_TEXT_HINT);
            return;
        }
        ctx.enableScissor(col2X, presetsListTop, col2X + col2W, presetsListBottom);
        int y = presetsListTop - presetsScroll;
        String pname = client.player != null ? client.player.getName().getString() : "Preview";

        for (int i = 0; i < presets.size(); i++) {
            if (y + ROW_PRE < presetsListTop) { y += ROW_PRE; continue; }
            if (y > presetsListBottom) break;

            boolean hovRow = inside(col2X, y, col2W - 22, ROW_PRE - 1, mx, my);
            boolean hovDel = inside(col2X + col2W - 20, y, 20, ROW_PRE - 1, mx, my);

            ctx.fill(col2X, y, col2X + col2W - 22, y + ROW_PRE - 1, hovRow ? C_ELEVATED : C_SURFACE);
            drawBorder(ctx, col2X, y, col2W - 22, ROW_PRE - 1, hovRow ? C_BORDER_LIT : C_BORDER);
            GradientPreviewRenderer.drawPreview(ctx, textRenderer, col2X + 4, y + 4, col2W - 30, presets.get(i), pname, false);

            // Del button
            ctx.fill(col2X + col2W - 20, y, col2X + col2W, y + ROW_PRE - 1, hovDel ? 0xFF2E1010 : 0xFF1A0A0A);
            drawBorder(ctx, col2X + col2W - 20, y, 20, ROW_PRE - 1, hovDel ? 0xFF5A2020 : C_BORDER);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("✕"), col2X + col2W - 10, y + 3, hovDel ? 0xFFCC6666 : 0xFF774444);

            y += ROW_PRE;
        }
        ctx.disableScissor();

        // Scroll indicator
        int total = presets.size() * ROW_PRE;
        int visible = presetsListBottom - presetsListTop;
        if (total > visible) drawScrollBar(ctx, col2X + col2W + 2, presetsListTop, 3, visible, presetsScroll, total);
    }

    private void renderSuggestionsList(DrawContext ctx, int mx, int my) {
        if (playerDisableField == null || client.getNetworkHandler() == null || client.player == null) return;
        String input = playerDisableField.getText().toLowerCase();
        if (input.isEmpty()) return;

        List<String> sugg = client.getNetworkHandler().getPlayerList().stream()
                .map(e -> e.getProfile().getName())
                .filter(n -> !n.equalsIgnoreCase(client.player.getName().getString()))
                .filter(n -> n.toLowerCase().startsWith(input))
                .filter(n -> !ConfigManager.CONFIG.disabledPlayers.containsValue(n))
                .collect(Collectors.toList());

        if (sugg.isEmpty() || (sugg.size() == 1 && sugg.get(0).equalsIgnoreCase(input))) return;

        ctx.enableScissor(contentLeft, suggestListTop, contentLeft + contentW, suggestListBottom);
        int y = suggestListTop - suggestScroll;
        for (String s : sugg) {
            if (y + ROW_SUG < suggestListTop) { y += ROW_SUG; continue; }
            if (y > suggestListBottom) break;
            boolean hov = inside(contentLeft, y, contentW, ROW_SUG, mx, my);
            ctx.fill(contentLeft, y, contentLeft + contentW, y + ROW_SUG, hov ? C_ELEVATED : C_SURFACE);
            drawBorder(ctx, contentLeft, y, contentW, ROW_SUG, C_BORDER);
            ctx.drawTextWithShadow(textRenderer, Text.literal(s), contentLeft + 6, y + 5, C_TEXT_PRI);
            y += ROW_SUG;
        }
        ctx.disableScissor();
    }

    private void renderDisabledList(DrawContext ctx, int mx, int my) {
        if (ConfigManager.CONFIG.disabledPlayers.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.translatable("gui.nickpaints.settings.none"), contentLeft, disabledListTop + 4, C_TEXT_HINT);
            return;
        }
        ctx.enableScissor(contentLeft, disabledListTop, contentLeft + contentW, disabledListBottom);
        int y = disabledListTop - disabledScroll;
        for (Map.Entry<UUID, String> e : ConfigManager.CONFIG.disabledPlayers.entrySet()) {
            if (y + ROW_DIS < disabledListTop) { y += ROW_DIS; continue; }
            if (y > disabledListBottom) break;
            boolean hov = inside(contentLeft, y, 72, ROW_DIS - 2, mx, my);
            ctx.fill(contentLeft, y, contentLeft + 72, y + ROW_DIS - 2, hov ? C_ELEVATED : C_SURFACE);
            drawBorder(ctx, contentLeft, y, 72, ROW_DIS - 2, hov ? C_BORDER_LIT : C_BORDER);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.nickpaints.settings.reenable_button"), contentLeft + 36, y + 5, hov ? C_TEXT_PRI : C_TEXT_SEC);
            ctx.drawTextWithShadow(textRenderer, Text.literal(e.getValue()), contentLeft + 80, y + 5, C_TEXT_SEC);
            y += ROW_DIS;
        }
        ctx.disableScissor();

        int total   = ConfigManager.CONFIG.disabledPlayers.size() * ROW_DIS;
        int visible = disabledListBottom - disabledListTop;
        if (total > visible) drawScrollBar(ctx, contentLeft + contentW + 2, disabledListTop, 3, visible, disabledScroll, total);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TUTORIAL
    // ─────────────────────────────────────────────────────────────────────────

    private int tutorialStep = 0;
    private static final String[] TUTORIAL_TITLE_KEYS = {
            "gui.nickpaints.tutorial.welcome.title",
            "gui.nickpaints.tutorial.step1.title",
            "gui.nickpaints.tutorial.step2.title",
            "gui.nickpaints.tutorial.step3.title"
    };
    private static final String[] TUTORIAL_BODY_KEYS = {
            "gui.nickpaints.tutorial.welcome.content",
            "gui.nickpaints.tutorial.step1.content",
            "gui.nickpaints.tutorial.step2.content",
            "gui.nickpaints.tutorial.step3.content"
    };
    private static final int TUTORIAL_STEPS = 4;

    private void renderTutorial(DrawContext ctx, int mx, int my) {
        ctx.fill(0, 0, width, height, 0xBB000000);

        int tw = 340, th = 180;
        int tx = (width  - tw) / 2;
        int ty = (height - th) / 2;

        // Card
        ctx.fill(tx + 3, ty + 3, tx + tw + 3, ty + th + 3, 0x66000000);
        ctx.fill(tx, ty, tx + tw, ty + th, C_BG);
        drawBorder(ctx, tx, ty, tw, th, C_BORDER_LIT);
        ctx.fill(tx, ty, tx + tw, ty + 3, C_ACCENT_BAR); // top bar

        // Step indicator
        String stepStr = (tutorialStep + 1) + " / " + TUTORIAL_STEPS;
        ctx.drawTextWithShadow(textRenderer, Text.literal(stepStr), tx + tw - textRenderer.getWidth(stepStr) - 8, ty + 8, C_TEXT_HINT);

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable(TUTORIAL_TITLE_KEYS[tutorialStep]), tx + tw / 2, ty + 10, C_TEXT_PRI);
        ctx.fill(tx + 16, ty + 22, tx + tw - 16, ty + 23, C_BORDER);

        // Body
        Text body = Text.translatable(TUTORIAL_BODY_KEYS[tutorialStep]);
        int ly = ty + 30;
        for (OrderedText line : textRenderer.wrapLines(body, tw - 32)) {
            ctx.drawTextWithShadow(textRenderer, line, tx + 16, ly, C_TEXT_SEC);
            ly += textRenderer.fontHeight + 2;
        }

        // Buttons
        int bw = 90, by = ty + th - 30;
        drawInlineBtn(ctx, Text.translatable("gui.nickpaints.tutorial.button.skip").getString(),
                tx + 12, by, bw, 22, mx, my, false);
        String nextKey = tutorialStep == TUTORIAL_STEPS - 1
                ? "gui.nickpaints.tutorial.button.finish"
                : "gui.nickpaints.tutorial.button.next";
        drawInlineBtn(ctx, Text.translatable(nextKey).getString(),
                tx + tw - bw - 12, by, bw, 22, mx, my, true);
    }

    private void drawInlineBtn(DrawContext ctx, String label, int x, int y, int w, int h, int mx, int my, boolean primary) {
        boolean hov = inside(x, y, w, h, mx, my);
        int bg     = primary ? (hov ? 0xFF303030 : 0xFF242424) : (hov ? 0xFF222222 : 0xFF181818);
        int border = primary ? (hov ? C_BORDER_LIT : C_BORDER) : C_BORDER;
        ctx.fill(x, y, x + w, y + h, bg);
        drawBorder(ctx, x, y, w, h, border);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + w / 2, y + (h - 8) / 2, primary ? C_TEXT_PRI : C_TEXT_SEC);
    }

    private void finishTutorial() {
        tutorialActive = false;
        ConfigManager.CONFIG.hasCompletedGuiTutorial = true;
        ConfigManager.saveConfig();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INPUT
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Color picker eats input first
        if (colorPicker.isVisible()) {
            if (colorPicker.mouseClicked(mx, my, button)) return true;
        }

        if (tutorialActive && button == 0) {
            int tw = 340, th = 180;
            int tx = (width - tw) / 2, ty = (height - th) / 2;
            int bw = 90, by = ty + th - 30;
            if (inside(tx + 12, by, bw, 22, (int)mx, (int)my)) { finishTutorial(); return true; }
            if (inside(tx + tw - bw - 12, by, bw, 22, (int)mx, (int)my)) {
                if (tutorialStep < TUTORIAL_STEPS - 1) tutorialStep++;
                else finishTutorial();
                return true;
            }
            return true;
        }

        if (activeTab == 0 && !editor.rainbowMode) {
            int lt = editorColorsListTop();
            int lb = editorColorsRowsBottom();
            boolean inList = inside(col1X, lt, col1W, lb - lt, (int) mx, (int) my);
            if (!inList) {
                blurColorHexEdit(true);
            } else if (handleEditorColorListClick(mx, my, button)) {
                return true;
            }
        }

        if (activeTab == 0) {
            // Preset row clicks
            int y = presetsListTop - presetsScroll;
            for (int i = 0; i < ConfigManager.CONFIG.presets.size(); i++) {
                if (y + ROW_PRE >= presetsListTop && y <= presetsListBottom) {
                    if (inside(col2X + col2W - 20, y, 20, ROW_PRE - 1, (int)mx, (int)my)) {
                        ConfigManager.CONFIG.presets.remove(i);
                        ConfigManager.saveConfig();
                        return true;
                    }
                    if (inside(col2X, y, col2W - 22, ROW_PRE - 1, (int)mx, (int)my)) {
                        editor.parseGradientString(ConfigManager.CONFIG.presets.get(i));
                        init();
                        return true;
                    }
                }
                y += ROW_PRE;
            }
        } else {
            // Suggestion clicks
            if (playerDisableField != null && client.getNetworkHandler() != null && client.player != null) {
                String input = playerDisableField.getText().toLowerCase();
                if (!input.isEmpty()) {
                    List<String> sugg = client.getNetworkHandler().getPlayerList().stream()
                            .map(e -> e.getProfile().getName())
                            .filter(n -> n.toLowerCase().startsWith(input))
                            .collect(Collectors.toList());
                    int y = suggestListTop - suggestScroll;
                    for (String s : sugg) {
                        if (inside(contentLeft, y, contentW, ROW_SUG, (int)mx, (int)my)) {
                            playerDisableField.setText(s);
                            return true;
                        }
                        y += ROW_SUG;
                    }
                }
            }

            // Disabled-player "enable" clicks
            int y = disabledListTop - disabledScroll;
            List<Map.Entry<UUID, String>> list = new ArrayList<>(ConfigManager.CONFIG.disabledPlayers.entrySet());
            for (Map.Entry<UUID, String> e : list) {
                if (inside(contentLeft, y, 72, ROW_DIS - 2, (int)mx, (int)my)) {
                    ConfigManager.CONFIG.setPlayerRendering(e.getKey(), e.getValue(), true);
                    ConfigManager.saveConfig();
                    return true;
                }
                y += ROW_DIS;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (colorPicker.isVisible() && colorPicker.mouseDragged(mx, my, button, dx, dy)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        colorPicker.mouseReleased(mx, my, button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (colorPicker.isVisible()) return false;
        int delta = (int)(vAmt * 14);

        if (activeTab == 0) {
            if (!editor.rainbowMode) {
                int listTop = editorColorsListTop();
                int listBottom = editorColorsRowsBottom();
                int listH = listBottom - listTop;
                int barX = col1X + col1W + 4;
                int barW = 4;
                boolean overList = inside(col1X, listTop, col1W, listH, (int)mx, (int)my);
                boolean overBar = editorContentH > listH && inside(barX - 1, listTop, barW + 2, listH, (int)mx, (int)my);
                if (overList || overBar) {
                    int colorsVisible = listH;
                    int maxScroll = Math.max(0, editorContentH - colorsVisible);
                    editorScroll = Math.max(0, Math.min(maxScroll, editorScroll - delta));
                    return true;
                }
            }
            // Presets scroll
            if (inside(col2X, presetsListTop, col2W, presetsListBottom - presetsListTop, (int)mx, (int)my)) {
                int total = ConfigManager.CONFIG.presets.size() * ROW_PRE;
                int vis   = presetsListBottom - presetsListTop;
                presetsScroll = Math.max(0, Math.min(Math.max(0, total - vis), presetsScroll - delta));
                return true;
            }
        } else {
            if (inside(contentLeft, suggestListTop, contentW, suggestListBottom - suggestListTop, (int)mx, (int)my)) {
                suggestScroll = Math.max(0, suggestScroll - delta);
                return true;
            }
            if (inside(contentLeft, disabledListTop, contentW, disabledListBottom - disabledListTop, (int)mx, (int)my)) {
                int total = ConfigManager.CONFIG.disabledPlayers.size() * ROW_DIS;
                int vis   = disabledListBottom - disabledListTop;
                disabledScroll = Math.max(0, Math.min(Math.max(0, total - vis), disabledScroll - delta));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (colorPicker.isVisible() && colorPicker.charTyped(chr, modifiers)) return true;
        if (activeTab == 0 && !editor.rainbowMode && colorHexFocusIndex >= 0) {
            if (colorHexEditBuffer.length() < 7 && isHexInputChar(chr)) {
                colorHexEditBuffer = colorHexEditBuffer.substring(0, colorHexCursor) + chr + colorHexEditBuffer.substring(colorHexCursor);
                colorHexCursor++;
                if (colorHexEditBuffer.length() == 7) {
                    applyHexColor(colorHexFocusIndex, colorHexEditBuffer);
                }
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (colorPicker.isVisible() && colorPicker.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (activeTab == 0 && !editor.rainbowMode && colorHexFocusIndex >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                blurColorHexEdit(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                blurColorHexEdit(true);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && colorHexCursor > 1) {
                colorHexEditBuffer = colorHexEditBuffer.substring(0, colorHexCursor - 1) + colorHexEditBuffer.substring(colorHexCursor);
                colorHexCursor--;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT && colorHexCursor < colorHexEditBuffer.length()) {
                colorHexCursor++;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT && colorHexCursor > 0) {
                colorHexCursor--;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BACKGROUND (suppress Minecraft default)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        // intentionally empty — our own overlay is drawn in render()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COLOR PICKER
    // ─────────────────────────────────────────────────────────────────────────

    private void openPickerFor(int colorIndex) {
        if (colorIndex < 0 || colorIndex >= editor.colors.size()) return;
        blurColorHexEdit(true);
        pickerColorIndex = colorIndex;
        float[] rgb = editor.colors.get(colorIndex);

        int popX = col1X + 24;
        int popY = editorColorsListTop() + colorIndex * COLOR_ROW_STRIDE - editorScroll;

        if (popX + 164 > width)  popX = col1X - 168;
        if (popY + 172 > height) popY = height - 176;

        colorPicker.open(popX, popY, rgb[0], rgb[1], rgb[2], updatedRgb -> {
            if (pickerColorIndex >= 0 && pickerColorIndex < editor.colors.size()) {
                editor.colors.set(pickerColorIndex, updatedRgb);
                if (colorHexFocusIndex == pickerColorIndex) {
                    colorHexEditBuffer = String.format("#%02x%02x%02x",
                            (int)(updatedRgb[0]*255), (int)(updatedRgb[1]*255), (int)(updatedRgb[2]*255));
                    colorHexCursor = colorHexEditBuffer.length();
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UTILITIES
    // ─────────────────────────────────────────────────────────────────────────

    private void addTab(ClickableWidget w) {
        addDrawableChild(w);
        tabWidgets.add(w);
    }

    private void applyHexColor(int idx, String s) {
        if (idx < 0 || idx >= editor.colors.size()) return;
        String t = s.trim();
        if (!t.startsWith("#")) t = "#" + t;
        try {
            java.awt.Color c = java.awt.Color.decode(t);
            editor.colors.set(idx, new float[]{c.getRed()/255f, c.getGreen()/255f, c.getBlue()/255f});
        } catch (Exception ignored) {}
    }

    private void drawSectionLabel(DrawContext ctx, String text, int x, int y) {
        ctx.drawTextWithShadow(textRenderer, Text.literal(text), x, y, C_TEXT_HINT);
        ctx.fill(x, y + textRenderer.fontHeight + 1, x + textRenderer.getWidth(text), y + textRenderer.fontHeight + 2, C_BORDER);
    }

    private void drawScrollBar(DrawContext ctx, int x, int listTop, int barW, int visibleH, int scroll, int totalH) {
        if (totalH <= visibleH) return;
        float ratio    = (float) visibleH / totalH;
        int   thumbH   = Math.max(14, (int)(visibleH * ratio));
        int   range    = totalH - visibleH;
        int   thumbY   = listTop + (range <= 0 ? 0 : (int)((visibleH - thumbH) * ((float) scroll / range)));
        ctx.fill(x, listTop, x + barW, listTop + visibleH, 0xFF1A1A1A);
        ctx.fill(x + 1, thumbY + 1, x + barW - 1, thumbY + thumbH - 1, 0xFF6A6A6A);
        ctx.fill(x + 1, thumbY + 1, x + barW - 1, thumbY + 2, 0xFF909090);
    }

    private static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x,         y,       x + w,   y + 1,   color);
        ctx.fill(x,         y + h-1, x + w,   y + h,   color);
        ctx.fill(x,         y,       x + 1,   y + h,   color);
        ctx.fill(x + w - 1, y,       x + w,   y + h,   color);
    }

    private static boolean inside(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}