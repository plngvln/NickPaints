package net.p4pingvin4ik.NickPaints.client;

import com.mojang.authlib.GameProfile;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.*;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.p4pingvin4ik.NickPaints.client.imgui.RenderInterface;
import net.p4pingvin4ik.NickPaints.config.ConfigManager;
import net.p4pingvin4ik.NickPaints.util.GradientUtil;

import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The main ImGui screen for NickPaints.
 * This class now encapsulates both the gradient editor and the local rendering settings,
 * providing a unified interface for all user configurations.
 */
public class ImGuiScreen extends Screen implements RenderInterface {

    private SimpleFramebuffer priviewFreamebuffer;
    // --- State Management for the Gradient Editor ---

    private final ImBoolean isRainbowMode = new ImBoolean(false);
    private final ImInt rainbowSpeed = new ImInt(3000);
    private final List<float[]> colors = new ArrayList<>();
    private final ImInt speed = new ImInt(4000);
    private final ImInt segment = new ImInt(16);
    private final ImBoolean isStatic = new ImBoolean(false);
    private final ImBoolean isBlockStyle = new ImBoolean(false);
    private final ImInt angle = new ImInt(45);

    // --- State Management for the Settings Window ---

    private final ImString playerToDisableInput = new ImString(32);
    private int tutorialStep = 0;
    private boolean isTutorialActive = false;
    private boolean isFirstFrame = true;
    private final float[] mainConfigPos = new float[2];
    private final float[] mainConfigSize = new float[2];
    private final float[] settingsPos = new float[2];
    private final float[] settingsSize = new float[2];
    private final float[] saveButtonPos = new float[2];
    /**
     * A simple layout to use if the user has no .ini file yet.
     */
    private static final String DEFAULT_LAYOUT = """
            [Window][Dockspace Host]
            Size=1920,1080
            Collapsed=0
            
            [Window][Settings##Settings]
            Size=350,1080
            Collapsed=0
            DockId=0x00000004,0
            
            [Window][NickPaints Configuration##NickPaintsConfig]
            Size=350,1080
            Collapsed=0
            DockId=0x00000001,0
            
            [Window][DockspaceHost]
            Pos=0,0
            Size=3440,1440
            Collapsed=0
            
            [Window][Debug##Default]
            Pos=60,60
            Size=350,1080
            Collapsed=0
            
            [Window][Настройки NickPaints##NickPaintsConfig]
            Pos=0,0
            Size=350,1080
            Collapsed=0
            DockId=0x00000007,0
            
            [Window][Настройки##Settings]
            Pos=3172,0
            Size=350,1080
            Collapsed=0
            DockId=0x00000006,0
            
            [Docking][Data]
            DockSpace         ID=0x38A10747 Pos=0,24 Size=1920,1056
            DockSpace         ID=0xDAF01B52 Window=0x11EB8EBF Pos=0,0 Size=3440,1440 Split=X
              DockNode        ID=0x00000007 Parent=0xDAF01B52 SizeRef=350,1440 Selected=0x690D7B6E
              DockNode        ID=0x00000008 Parent=0xDAF01B52 SizeRef=3022,1440 Split=X
                DockNode      ID=0x00000005 Parent=0x00000008 SizeRef=3170,1440 Split=X
                  DockNode    ID=0x00000003 Parent=0x00000005 SizeRef=1629,1080 Split=X
                    DockNode  ID=0x00000001 Parent=0x00000003 SizeRef=350,1080 Selected=0xB107EC98
                    DockNode  ID=0x00000002 Parent=0x00000003 SizeRef=1294,1080 CentralNode=1
                  DockNode    ID=0x00000004 Parent=0x00000005 SizeRef=350,1080 Selected=0x1C33C293
                DockNode      ID=0x00000006 Parent=0x00000008 SizeRef=350,1440 Selected=0x8FAD21AA
            """;

    /**
     * Constructs a new ImGuiScreen.
     */
    public ImGuiScreen() {
        super(Text.literal("NickPaints ImGui Screen"));
    }

    /**
     * Renders the ImGui interface.
     * This method is the main entry point for rendering the UI. It sets up the dockspace,
     * renders the main configuration and settings windows, and handles the tutorial overlay.
     *
     * @param io The ImGuiIO object, which provides information about the input and output of ImGui.
     */
    @Override
    public void gradientNickname$render(ImGuiIO io) {
        if (isFirstFrame) {
            // Apply a default layout on first launch if no settings file exists.
            String iniPath = io.getIniFilename();
            if (iniPath != null && !iniPath.isEmpty()) {
                File iniFile = new File(iniPath);
                if (!iniFile.exists()) {
                    ImGui.loadIniSettingsFromMemory(DEFAULT_LAYOUT);
                }
            }
            parseGradientString(ConfigManager.CONFIG.currentGradient);
            isTutorialActive = !ConfigManager.CONFIG.hasCompletedGuiTutorial;
            isFirstFrame = false;
        }

        setupDockspace();

        if (isTutorialActive) {
            ImGui.beginDisabled();
        }

        renderMainConfigurationWindow();
        renderSettingsWindow();

        if (isTutorialActive) {
            ImGui.endDisabled();
            renderTutorial();
        }
        ImGui.end(); // End the Dockspace Host window
    }

    /**
     * Renders the background of the screen.
     * In this case, we don't want to render the default Minecraft screen background,
     * so this method is intentionally left empty.
     *
     * @param context The DrawContext for rendering.
     * @param mouseX The x-coordinate of the mouse.
     * @param mouseY The y-coordinate of the mouse.
     * @param delta The time delta since the last frame.
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Do nothing to keep the background clear for the ImGui interface.
    }


//    Not working rn
//    /**
//     * Override render to also render the preview using Minecraft's TextRenderer.
//     * This uses the actual Minecraft rendering path with the mixin, ensuring perfect accuracy.
//     * The preview is rendered at a fixed position that coordinates with ImGui window layout.
//     */
//    @Override
//    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
//        // Render the preview using Minecraft's TextRenderer before ImGui renders on top
//        // Position it where the ImGui preview window will be
//        String currentGradient = reconstructGradientString();
//        if (MinecraftClient.getInstance().player != null) {
//            // Render preview at top center of screen - adjust as needed to match ImGui window
//            int previewX = this.width / 2;
//            int previewY = 50; // Adjust to match your ImGui window position
//            drawPreviewWithMinecraftRenderer(context, currentGradient, previewX, previewY, true);
//        }
//
//        // Call super to handle other rendering
//        super.render(context, mouseX, mouseY, delta);
//    }
//
//    /**
//     * Renders the preview using Minecraft's TextRenderer for accurate gradient rendering.
//     * This can be called from the Screen's render() method to use Minecraft's actual rendering.
//     * Note: This requires rendering in Minecraft's context, not ImGui's context.
//     *
//     * @param context The DrawContext for rendering.
//     * @param gradientString The gradient string to use.
//     * @param x The x position to render at.
//     * @param y The y position to render at.
//     * @param centered Whether to center the text.
//     */
//    private void drawPreviewWithMinecraftRenderer(DrawContext context, String gradientString, int x, int y, boolean centered) {
//        MinecraftClient client = MinecraftClient.getInstance();
//        if (client.player == null) return;
//
//        String playerName = client.player.getName().getString();
//        int totalLength = playerName.length();
//        Text text = Text.literal(playerName);
//
//        // Set up the gradient data so the mixin can apply it
//        try {
//            if (totalLength > 0) {
//                GradientData.CURRENT_GRADIENT.set(new GradientData(gradientString, totalLength));
//            }
//
//            TextRenderer textRenderer = client.textRenderer;
//
//            // Calculate position
//            int renderX = x;
//            int renderY = y;
//            if (centered) {
//                int textWidth = textRenderer.getWidth(text);
//                int windowWidth = this.width;
//                renderX = (windowWidth - textWidth) / 2;
//            }
//
//            // Render using Minecraft's TextRenderer - this will trigger the mixin and apply the gradient
//            context.drawText(textRenderer, text, renderX, renderY, 0xFFFFFF, false);
//        } finally {
//            GradientData.CURRENT_GRADIENT.remove();
//        }
//    }

    /**
     * Renders the interactive tutorial.
     * The tutorial guides the user through the main features of the UI.
     * It displays a series of pop-ups and highlights different parts of the screen.
     */
    private void renderTutorial() {
        ImGui.getBackgroundDrawList().addRectFilled(0, 0,
                MinecraftClient.getInstance().getWindow().getFramebufferWidth(),
                MinecraftClient.getInstance().getWindow().getFramebufferHeight(),
                ImGui.getColorU32(0, 0, 0, 0.6f));

        final float tutorialWidth = 300f;
        final float margin = 15f;
        final float viewportWidth = ImGui.getMainViewport().getSizeX();
        final float viewportHeight = ImGui.getMainViewport().getSizeY();

        switch (tutorialStep) {
            case 0:
                float centerX = ImGui.getMainViewport().getPosX() + viewportWidth * 0.5f;
                float centerY = ImGui.getMainViewport().getPosY() + viewportHeight * 0.5f;
                ImGui.setNextWindowPos(centerX, centerY, ImGuiCond.Appearing, 0.5f, 0.5f);

                if(ImGui.begin("WelcomePopup##Tutorial", ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoDocking)) {
                    ImGui.text(Lang.get("gui.nickpaints.tutorial.welcome.title"));
                    ImGui.separator();
                    ImGui.textWrapped(Lang.get("gui.nickpaints.tutorial.welcome.content"));
                    ImGui.spacing();
                    if (ImGui.button(Lang.get("gui.nickpaints.tutorial.button.start"))) {
                        tutorialStep++;
                    }
                    ImGui.end();
                }
                break;

            case 1:
                float step1X = mainConfigPos[0] + mainConfigSize[0] + margin;
                if (step1X + tutorialWidth > viewportWidth) {
                    step1X = mainConfigPos[0] - tutorialWidth - margin;
                }
                renderTutorialStep("gui.nickpaints.tutorial.step1", step1X, mainConfigPos[1]);
                break;

            case 2:
                float step2X = settingsPos[0] - tutorialWidth - margin;
                if (step2X < 0) {
                    step2X = settingsPos[0] + settingsSize[0] + margin;
                }
                renderTutorialStep("gui.nickpaints.tutorial.step2", step2X, settingsPos[1]);
                break;

            case 3:
                float step3X = saveButtonPos[0];
                float step3Y = saveButtonPos[1] + 30;
                if (saveButtonPos[1] > viewportHeight / 2) {
                    step3Y = saveButtonPos[1] - 120;
                }
                renderTutorialStep("gui.nickpaints.tutorial.step3", step3X, step3Y);
                break;
        }
    }

    /**
     * Renders a single step of the tutorial.
     *
     * @param langKeyPrefix The prefix for the language keys to use for the tutorial step's title and content.
     * @param x The x-coordinate of the tutorial window.
     * @param y The y-coordinate of the tutorial window.
     */
    private void renderTutorialStep(String langKeyPrefix, float x, float y) {
        ImGui.setNextWindowSize(300, 0, ImGuiCond.Appearing);

        final float viewportWidth = ImGui.getMainViewport().getSizeX();
        final float viewportHeight = ImGui.getMainViewport().getSizeY();

        if (x + 300 > viewportWidth) {
            x = viewportWidth - 300;
        }
        if (x < 0) {
            x = 0;
        }
        ImGui.setNextWindowPos(x, y, ImGuiCond.Appearing);

        int flags = ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoCollapse;

        if (ImGui.begin(Lang.get(langKeyPrefix + ".title"), flags)) {

            float windowHeight = ImGui.getWindowSizeY();
            if (y + windowHeight > viewportHeight) {
                y = viewportHeight - windowHeight;
                ImGui.setWindowPos(x, y);
            }
            if (y < 0) {
                y = 0;
                ImGui.setWindowPos(x, y);
            }

            ImGui.textWrapped(Lang.get(langKeyPrefix + ".content"));
            ImGui.separator();

            if (tutorialStep < 3) {
                if (ImGui.button(Lang.get("gui.nickpaints.tutorial.button.next"))) {
                    tutorialStep++;
                }
            } else {
                if (ImGui.button(Lang.get("gui.nickpaints.tutorial.button.finish"))) {
                    ConfigManager.CONFIG.hasCompletedGuiTutorial = true;
                    ConfigManager.saveConfig();
                    isTutorialActive = false;
                }
            }
            ImGui.end();
        }
    }

    /**
     * Renders the main window for creating and editing gradients.
     */
    private void renderMainConfigurationWindow() {
        ImGui.begin(Lang.get("gui.nickpaints.title") + "##NickPaintsConfig");

        mainConfigPos[0] = ImGui.getWindowPosX();
        mainConfigPos[1] = ImGui.getWindowPosY();
        mainConfigSize[0] = ImGui.getWindowSizeX();
        mainConfigSize[1] = ImGui.getWindowSizeY();

        drawPreview(reconstructGradientString(), true);
        ImGui.separator();
        ImGui.checkbox(Lang.get("gui.nickpaints.mode.rainbow"), isRainbowMode);
        ImGui.separator();

        ImGui.pushItemWidth(-1);
        if (isRainbowMode.get()) {
            ImGui.text(Lang.get("gui.nickpaints.option.speed"));
            ImGui.sliderInt("##rainbowspeed", rainbowSpeed.getData(), 1000, 20000);
        } else {
            renderColorEditor();
            ImGui.text(Lang.get("gui.nickpaints.option.speed"));
            if (isStatic.get()) ImGui.beginDisabled();
            ImGui.sliderInt("##speed", speed.getData(), 1000, 20000);
            if (isStatic.get()) ImGui.endDisabled();

            ImGui.text(Lang.get("gui.nickpaints.option.segment"));
            ImGui.sliderInt("##segment", segment.getData(), 1, 200);

            ImGui.text(Lang.get("gui.nickpaints.option.angle"));
            ImGui.sliderInt("##angle", angle.getData(), 0, 360);
        }
        ImGui.popItemWidth();
        ImGui.separator();

        if (!isRainbowMode.get()) {
            ImGui.checkbox(Lang.get("gui.nickpaints.option.static"), isStatic);
            ImGui.checkbox(Lang.get("gui.nickpaints.option.style_block"), isBlockStyle);
            ImGui.separator();
        }

        renderUtilities();

        if (ImGui.button(Lang.get("gui.nickpaints.button.save_sync"), -1, 0)) {
            ConfigManager.CONFIG.currentGradient = reconstructGradientString();
            ConfigManager.saveConfig();
            if (MinecraftClient.getInstance().player != null) {
                WebSocketManager.syncMyPaint();
            }
        }
        saveButtonPos[0] = ImGui.getItemRectMinX();
        saveButtonPos[1] = ImGui.getItemRectMinY();

        ImGui.separator();
        renderPresets();
        ImGui.end();
    }


    /**
     * Renders the settings window, containing functionality from the old commands.
     */
    private void renderSettingsWindow() {
        ImGui.begin(Lang.get("gui.nickpaints.settings.title") + "##Settings");

        // Store the position of the window for the tutorial
        settingsPos[0] = ImGui.getWindowPosX();
        settingsPos[1] = ImGui.getWindowPosY();

        // Global rendering toggle
        boolean globalEnabled = ConfigManager.CONFIG.globalRenderingEnabled;
        if (ImGui.checkbox("##globaltoggle", globalEnabled)) {
            ConfigManager.CONFIG.setGlobalRendering(!globalEnabled);
            ConfigManager.saveConfig();
        }
        ImGui.sameLine();
        ImGui.text(Lang.get("gui.nickpaints.settings.global_rendering"));

        // Toggle for showing own nametag
        boolean showOwnNametag = ConfigManager.CONFIG.showOwnNametag;
        if (ImGui.checkbox("##showownnametag", showOwnNametag)) {
            ConfigManager.CONFIG.showOwnNametag = !showOwnNametag;
            ConfigManager.saveConfig();
        }
        ImGui.sameLine();
        ImGui.text(Lang.get("gui.nickpaints.settings.show_own_nametag"));

        ImGui.separator();

        // Section for disabling rendering for specific players
        ImGui.text(Lang.get("gui.nickpaints.settings.disable_player_label"));
        ImGui.inputText("##playerinput", playerToDisableInput);
        ImGui.sameLine();
        if (ImGui.button(Lang.get("gui.nickpaints.settings.disable_button"))) {
            String username = playerToDisableInput.get();
            if (!username.isEmpty()) {
                MojangAPIHelper.getUuidForUsername(username).thenAccept(uuidOptional -> {
                    uuidOptional.ifPresent(uuid -> {
                        ConfigManager.CONFIG.setPlayerRendering((UUID) uuid, username, false);
                        ConfigManager.saveConfig();
                    });
                });
                playerToDisableInput.set(""); // Clear input after submission
            }
        }

        renderPlayerSuggestions();


        // List of disabled players
        ImGui.text(Lang.get("gui.nickpaints.settings.disabled_list"));
        ImGui.beginChild("##disabledlist", 0, -ImGui.getFrameHeightWithSpacing() * 2, true);
        if (ConfigManager.CONFIG.disabledPlayers.isEmpty()) {
            ImGui.textDisabled(Lang.get("gui.nickpaints.settings.none"));
        } else {
            // Create a copy to prevent ConcurrentModificationException while rendering.
            List<Map.Entry<UUID, String>> disabledList = new ArrayList<>(ConfigManager.CONFIG.disabledPlayers.entrySet());
            for (Map.Entry<UUID, String> entry : disabledList) {
                if (ImGui.button(Lang.get("gui.nickpaints.settings.reenable_button") + "##" + entry.getKey())) {
                    ConfigManager.CONFIG.setPlayerRendering(entry.getKey(), entry.getValue(), true);
                    ConfigManager.saveConfig();
                }
                ImGui.sameLine();
                ImGui.text(entry.getValue());
            }
        }
        ImGui.endChild();

        // Button to clear the cache
        if (ImGui.button(Lang.get("gui.nickpaints.settings.clear_cache_button"))) {
            WebSocketManager.clearCache();
        }

        ImGui.end();
    }

    /**
     * Renders a list of online players as clickable suggestions for the disable input field.
     */
    private void renderPlayerSuggestions() {
        final String currentInput = playerToDisableInput.get().toLowerCase();
        if (currentInput.isEmpty()) {
            return; // Don't show suggestions if input is empty
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null || client.player == null) {
            return;
        }

        // Filter online players based on the current input
        List<String> suggestions = client.getNetworkHandler().getPlayerList().stream()
                .map(PlayerListEntry::getProfile)
                .map(GameProfile::getName)
                .filter(name -> !name.equalsIgnoreCase(client.player.getName().getString())) // Exclude self
                .filter(name -> name.toLowerCase().startsWith(currentInput)) // Match input start
                .filter(name -> !ConfigManager.CONFIG.disabledPlayers.containsValue(name)) // Exclude already disabled players
                .collect(Collectors.toList());

        // Don't show suggestions if the only suggestion is the same as the input
        if (suggestions.size() == 1 && suggestions.get(0).equalsIgnoreCase(currentInput)) {
            return;
        }

        if (!suggestions.isEmpty()) {
            ImGui.beginChild("##suggestions", ImGui.getContentRegionAvailX(), Math.min(120, suggestions.size() * ImGui.getTextLineHeightWithSpacing() + 5), true);
            for (String suggestion : suggestions) {
                if (ImGui.selectable(suggestion + "##sugg")) {
                    playerToDisableInput.set(suggestion);
                }
            }
            ImGui.endChild();
        }
    }

    /**
     * Draws a preview of the gradient on the player's nickname.
     * This version is corrected to render a simpler, per-character vertical gradient
     * to more accurately reflect Minecraft's rendering capabilities and to fix graphical artifacts.
     *
     * @param gradientString The gradient string to use for the preview.
     * @param centered       Whether to center the preview text.
     */
    private void drawPreview(String gradientString, boolean centered) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        String playerName = client.player.getName().getString();
        int totalLength = playerName.length();

        float startX;
        float startY = ImGui.getCursorScreenPosY();

        if (centered) {
            float textWidth = ImGui.calcTextSize(playerName).x;
            float windowWidth = ImGui.getWindowWidth();
            startX = ImGui.getWindowPosX() + (windowWidth - textWidth) / 2.0f;
        } else {
            startX = ImGui.getCursorScreenPosX();
        }

        // Use Minecraft's font metrics for coordinate calculation
        final float MC_CHAR_WIDTH = 8.0f;
        final float MC_FONT_HEIGHT = 9.0f;
        final int VERTICAL_SEGMENTS = 8;

        float currentX = startX;
        float fontHeight = ImGui.getTextLineHeight();

        // Iterate over each character
        for (int i = 0; i < playerName.length(); i++) {
            String characterStr = String.valueOf(playerName.charAt(i));
            float charWidth = ImGui.calcTextSize(characterStr).x;

            // Determine the start (top) and end (bottom) colors for this character's vertical gradient
            float characterCenterX = (i + 0.5f) * MC_CHAR_WIDTH;
            int topArgbColor = GradientUtil.get2DColor(gradientString, totalLength, characterCenterX, 0);
            int bottomArgbColor = GradientUtil.get2DColor(gradientString, totalLength, characterCenterX, MC_FONT_HEIGHT);

            float segmentHeight = fontHeight / VERTICAL_SEGMENTS;

            // Render the character in vertical slices, each with an interpolated color
            for (int vSeg = 0; vSeg < VERTICAL_SEGMENTS; vSeg++) {
                // Interpolate the color for the current vertical segment
                // The 't' value represents the progress down the character (0.0 at top, 1.0 at bottom)
                float t = (vSeg + 0.5f) / VERTICAL_SEGMENTS;
                int interpolatedArgb = interpolateColor(topArgbColor, bottomArgbColor, t);

                // Convert ARGB to ABGR for ImGui
                int a = (interpolatedArgb >> 24) & 0xFF;
                int r = (interpolatedArgb >> 16) & 0xFF;
                int g = (interpolatedArgb >> 8) & 0xFF;
                int b = interpolatedArgb & 0xFF;
                int abgrColor = (a << 24) | (b << 16) | (g << 8) | r;

                // Define the clipping rectangle for this vertical slice
                float clipStartX = currentX;
                float clipStartY = startY + vSeg * segmentHeight;
                float clipEndX = clipStartX + charWidth + 1.0f;
                float clipEndY = clipStartY + segmentHeight + 1.0f;

                // Push clip rectangle, draw the character with the interpolated color, and pop.
                ImGui.getWindowDrawList().pushClipRect(clipStartX, clipStartY, clipEndX, clipEndY, true);
                ImGui.getWindowDrawList().addText(currentX, startY, abgrColor, characterStr);
                ImGui.getWindowDrawList().popClipRect();
            }
            currentX += charWidth;
        }

        if (centered) {
            ImGui.dummy(0, ImGui.getTextLineHeightWithSpacing());
        } else {
            ImGui.dummy(currentX - startX, ImGui.getTextLineHeight());
        }
    }

    /**
     * Linearly interpolates between two ARGB colors.
     *
     * @param color1 The starting color.
     * @param color2 The ending color.
     * @param t      The interpolation factor, from 0.0 to 1.0.
     * @return The interpolated ARGB color.
     */
    private int interpolateColor(int color1, int color2, float t) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Renders the color editor for the custom gradient.
     * This allows the user to add, remove, and edit the colors of the gradient.
     */
    private void renderColorEditor() {
        ImGui.text(Lang.get("gui.nickpaints.section.colors"));
        int colorToRemove = -1;
        for (int i = 0; i < colors.size(); i++) {
            ImGui.pushID(i);
            ImGui.colorEdit3("##color", colors.get(i), ImGuiColorEditFlags.NoInputs);
            if (colors.size() > 1) {
                ImGui.sameLine();
                if (ImGui.button(Lang.get("gui.nickpaints.button.remove"))) { colorToRemove = i; }
            }
            ImGui.popID();
        }
        if (colorToRemove != -1) colors.remove(colorToRemove);
        if (ImGui.button(Lang.get("gui.nickpaints.button.add_color"))) {
            colors.add(new float[]{1.0f, 1.0f, 1.0f});
        }
    }

    /**
     * Renders utility buttons, such as copy, paste, and the character count.
     */
    private void renderUtilities() {
        if (ImGui.button(Lang.get("gui.nickpaints.button.copy"))) {
            ImGui.setClipboardText(reconstructGradientString());
        }
        ImGui.sameLine();
        if (ImGui.button(Lang.get("gui.nickpaints.button.paste"))) {
            String clipboardText = ImGui.getClipboardText();
            if (clipboardText != null && !clipboardText.isEmpty()) {
                parseGradientString(clipboardText);
            }
        }
        ImGui.sameLine();
        String currentGradient = reconstructGradientString();
        int currentLength = currentGradient.length();
        int limit = 256;
        ImGui.text(Lang.get("gui.nickpaints.util.label_length"));
        ImGui.sameLine();
        String valueText = String.format("%d/%d", currentLength, limit);
        if (currentLength > limit) {
            valueText += Lang.get("gui.nickpaints.util.length_warning");
        }
        float r = 0.5f, g = 1.0f, b = 0.5f; // Green
        if (currentLength > limit) { r = 1.0f; g = 0.2f; b = 0.2f; } // Red
        else if (currentLength > limit - 30) { r = 1.0f; g = 1.0f; b = 0.2f; } // Yellow
        ImGui.textColored(r, g, b, 1.0f, valueText);
    }

    /**
     * Renders the presets section.
     * This allows the user to save, apply, and delete gradient presets.
     */
    private void renderPresets() {
        int presetToDelete = -1;
        MinecraftClient client = MinecraftClient.getInstance();
        String playerName = (client != null && client.player != null) ? client.player.getName().getString() : "Preview";
        float previewWidth = ImGui.calcTextSize(playerName).x;

        float buttonSize = ImGui.getTextLineHeightWithSpacing();

        for (int i = 0; i < ConfigManager.CONFIG.presets.size(); i++) {
            ImGui.pushID(i);

            if (ImGui.button("+", buttonSize, buttonSize)) {
                parseGradientString(ConfigManager.CONFIG.presets.get(i));
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(Lang.get("gui.nickpaints.tooltip.apply_preset"));
            }

            ImGui.sameLine();

            float startX = ImGui.getCursorPosX();
            float endX = ImGui.getWindowContentRegionMaxX() - buttonSize;
            float availableSpace = endX - startX;
            float previewStartX = startX + (availableSpace - previewWidth) / 2;

            if (previewStartX > startX) {
                ImGui.setCursorPosX(previewStartX);
                drawPreview(ConfigManager.CONFIG.presets.get(i), false);
            }

            ImGui.sameLine();

            ImGui.setCursorPosX(endX);

            ImGui.pushStyleColor(ImGuiCol.Button, 0.7f, 0.2f, 0.2f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.3f, 0.3f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.6f, 0.15f, 0.15f, 1.0f);
            if (ImGui.button("-", buttonSize, buttonSize)) {
                presetToDelete = i;
            }
            ImGui.popStyleColor(3);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(Lang.get("gui.nickpaints.tooltip.delete_preset"));
            }

            ImGui.popID();
        }

        ImGui.separator();

        if (ImGui.button(Lang.get("gui.nickpaints.button.save_preset"), -1, 0)) {
            ConfigManager.CONFIG.presets.add(reconstructGradientString());
            ConfigManager.saveConfig();
        }

        if (presetToDelete != -1) {
            ConfigManager.CONFIG.presets.remove(presetToDelete);
            ConfigManager.saveConfig();
        }
    }

    /**
     * Parses a gradient string and updates the state of the UI accordingly.
     *
     * @param gradientString The gradient string to parse.
     */
    private void parseGradientString(String gradientString) {
        if (gradientString == null || gradientString.isEmpty()) { colors.add(new float[]{1f,1f,1f}); return; }
        Matcher rainbowMatcher = Pattern.compile("rainbow\\((\\d+)\\)").matcher(gradientString);
        if (rainbowMatcher.matches()) { isRainbowMode.set(true); rainbowSpeed.set(Integer.parseInt(rainbowMatcher.group(1))); return; }
        isRainbowMode.set(false);
        String tempString = gradientString.toLowerCase();
        Matcher angleMatcher = Pattern.compile("angle\\((\\d+)\\)").matcher(tempString);
        if (angleMatcher.find()) {
            angle.set(Integer.parseInt(angleMatcher.group(1)));
            tempString = angleMatcher.replaceAll("");
        }
        Matcher staticMatcher = Pattern.compile("static\\(true\\)").matcher(tempString);
        isStatic.set(staticMatcher.find());
        if (isStatic.get()) tempString = staticMatcher.replaceAll("");
        Matcher speedMatcher = Pattern.compile("speed\\((\\d+)\\)").matcher(tempString);
        if (speedMatcher.find()) { speed.set(Integer.parseInt(speedMatcher.group(1))); tempString = speedMatcher.replaceAll(""); }
        Matcher segmentMatcher = Pattern.compile("segment\\((\\d+)\\)").matcher(tempString);
        if (segmentMatcher.find()) { segment.set(Integer.parseInt(segmentMatcher.group(1))); tempString = segmentMatcher.replaceAll(""); }
        Matcher styleMatcher = Pattern.compile("style\\((block)\\)").matcher(tempString);
        isBlockStyle.set(styleMatcher.find());
        if (isBlockStyle.get()) tempString = styleMatcher.replaceAll("");
        colors.clear();
        String[] hexCodes = tempString.trim().split(",");
        for (String hex : hexCodes) {
            if (hex.trim().isEmpty()) continue;
            try {
                Color c = Color.decode(hex.trim());
                colors.add(new float[]{c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f});
            } catch (NumberFormatException e) {}
        }
        if (colors.isEmpty()) { colors.add(new float[]{1f,1f,1f}); }
    }

    /**
     * Reconstructs the gradient string from the current state of the UI.
     *
     * @return The reconstructed gradient string.
     */
    private String reconstructGradientString() {
        if (isRainbowMode.get()) { return String.format("rainbow(%d)", rainbowSpeed.get()); }
        StringBuilder sb = new StringBuilder();
        String colorsString = colors.stream().map(color -> String.format("#%02x%02x%02x", (int)(color[0]*255), (int)(color[1]*255), (int)(color[2]*255))).collect(Collectors.joining(", "));
        sb.append(colorsString);
        if (isStatic.get()) { sb.append(" static(true)"); } else { sb.append(" speed(").append(speed.get()).append(")"); }
        sb.append(" segment(").append(segment.get()).append(")");
        sb.append(" angle(").append(angle.get()).append(")");
        if (isBlockStyle.get()) { sb.append(" style(block)"); }
        return sb.toString().trim();
    }

    /**
     * Sets up the dockspace for the ImGui windows.
     * This creates a main dockspace that covers the entire screen,
     * allowing the user to dock the configuration and settings windows.
     */
    private void setupDockspace() {
        int windowFlags = ImGuiWindowFlags.NoDocking | ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoCollapse |
                ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoBringToFrontOnFocus |
                ImGuiWindowFlags.NoNavFocus | ImGuiWindowFlags.NoBackground;

        ImGui.setNextWindowPos(0, 0, ImGuiCond.Always);
        ImGui.setNextWindowSize(MinecraftClient.getInstance().getWindow().getFramebufferWidth(), MinecraftClient.getInstance().getWindow().getFramebufferHeight());
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);
        ImGui.begin("DockspaceHost", new ImBoolean(true), windowFlags);
        ImGui.popStyleVar(3);
        ImGui.pushStyleColor(ImGuiCol.DockingEmptyBg, 0, 0, 0, 0);
        int dockspaceId = ImGui.getID("MainDockspace");
        ImGui.dockSpace(dockspaceId);
        ImGui.popStyleColor();
    }

    /**
     * Determines whether the game should be paused when this screen is open.
     *
     * @return false, as we don't want to pause the game.
     */
    @Override
    public boolean shouldPause() {
        return false;
    }

    /**
     * Handles key press events.
     *
     * @param keyCode The key code of the pressed key.
     * @param scanCode The scan code of the pressed key.
     * @param modifiers The modifier keys that were pressed.
     * @return true if the event was handled, false otherwise.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            ImGui.setWindowFocus(null);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Called when the screen is removed.
     * This is used to restore the cursor to its default state.
     */
    @Override
    public void removed() {
        long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
        GLFW.glfwSetCursor(windowHandle, 0L);
        super.removed();
    }
}