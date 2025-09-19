package net.p4pingvin4ik.NickPaints.client;

import com.mojang.authlib.GameProfile;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.*;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import net.minecraft.client.MinecraftClient;
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

    // --- State Management for the Gradient Editor ---
    private final ImBoolean isRainbowMode = new ImBoolean(false);
    private final ImInt rainbowSpeed = new ImInt(3000);
    private final List<float[]> colors = new ArrayList<>();
    private final ImInt speed = new ImInt(4000);
    private final ImInt segment = new ImInt(16);
    private final ImBoolean isStatic = new ImBoolean(false);
    private final ImBoolean isBlockStyle = new ImBoolean(false);
    private final ImBoolean isRightToLeft = new ImBoolean(false);

    // --- State Management for the Settings Window ---
    private final ImString playerToDisableInput = new ImString(32);

    private boolean isFirstFrame = true;

    // A simple layout to use if the user has no .ini file yet.
    private static final String DEFAULT_LAYOUT = """
            [Window][Dockspace Host]
            Size=1920,1080
            Collapsed=0
            
            [Window][Settings##Settings]
            Size=333,1080
            Collapsed=0
            DockId=0x00000004,0
            
            [Window][NickPaints Configuration##NickPaintsConfig]
            Size=300,1080
            Collapsed=0
            DockId=0x00000001,0
            
            [Window][DockspaceHost]
            Pos=0,0
            Size=3440,1440
            Collapsed=0
            
            [Window][Debug##Default]
            Pos=60,60
            Size=400,400
            Collapsed=0
            
            [Window][Настройки NickPaints##NickPaintsConfig]
            Pos=0,0
            Size=333,1080
            Collapsed=0
            DockId=0x00000007,0
            
            [Window][Настройки##Settings]
            Pos=3172,0
            Size=300,1080
            Collapsed=0
            DockId=0x00000006,0
            
            [Docking][Data]
            DockSpace         ID=0x38A10747 Pos=0,24 Size=1920,1056
            DockSpace         ID=0xDAF01B52 Window=0x11EB8EBF Pos=0,0 Size=3440,1440 Split=X
              DockNode        ID=0x00000007 Parent=0xDAF01B52 SizeRef=416,1440 Selected=0x690D7B6E
              DockNode        ID=0x00000008 Parent=0xDAF01B52 SizeRef=3022,1440 Split=X
                DockNode      ID=0x00000005 Parent=0x00000008 SizeRef=3170,1440 Split=X
                  DockNode    ID=0x00000003 Parent=0x00000005 SizeRef=1629,1080 Split=X
                    DockNode  ID=0x00000001 Parent=0x00000003 SizeRef=333,1080 Selected=0xB107EC98
                    DockNode  ID=0x00000002 Parent=0x00000003 SizeRef=1294,1080 CentralNode=1
                  DockNode    ID=0x00000004 Parent=0x00000005 SizeRef=289,1080 Selected=0x1C33C293
                DockNode      ID=0x00000006 Parent=0x00000008 SizeRef=268,1440 Selected=0x8FAD21AA
            """;

    public ImGuiScreen() {
        super(Text.literal("NickPaints ImGui Screen"));
    }

    @Override
    public void render(ImGuiIO io) {
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
            isFirstFrame = false;
        }

        setupDockspace();

        // Render our two main UI windows. They can be docked anywhere within the dockspace.
        renderMainConfigurationWindow();
        renderSettingsWindow();

        ImGui.end(); // End the Dockspace Host window
    }
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Do nothing.
    }
    /**
     * Renders the main window for creating and editing gradients.
     */
    private void renderMainConfigurationWindow() {
        ImGui.begin(Lang.get("gui.nickpaints.title") + "##NickPaintsConfig");

        drawPreview();
        ImGui.separator();
        ImGui.checkbox(Lang.get("gui.nickpaints.mode.rainbow"), isRainbowMode);
        ImGui.separator();

        if (isRainbowMode.get()) {
            ImGui.text(Lang.get("gui.nickpaints.option.speed"));
            ImGui.sliderInt("##rainbowspeed", rainbowSpeed.getData(), 1000, 20000);
        } else {
            renderColorEditor();
            renderOptions();
        }

        ImGui.separator();
        renderUtilities();

        if (ImGui.button(Lang.get("gui.nickpaints.button.save_sync"))) {
            ConfigManager.CONFIG.currentGradient = reconstructGradientString();
            ConfigManager.saveConfig();
            if (MinecraftClient.getInstance().player != null) {
                CloudSyncManager.syncMyPaint(MinecraftClient.getInstance().player.getUuid());
            }
        }
        ImGui.sameLine();
        if (ImGui.button(Lang.get("gui.nickpaints.button.close"))) {
            this.close();
        }

        ImGui.end();
    }

    /**
     * Renders the settings window, containing functionality from the old commands.
     */
    private void renderSettingsWindow() {
        ImGui.begin(Lang.get("gui.nickpaints.settings.title") + "##Settings");

        // Global Toggle
        ImGui.text(Lang.get("gui.nickpaints.settings.global_rendering"));
        ImGui.sameLine();
        boolean globalEnabled = ConfigManager.CONFIG.globalRenderingEnabled;
        if (ImGui.checkbox("##globaltoggle", globalEnabled)) {
            ConfigManager.CONFIG.setGlobalRendering(!globalEnabled);
            ConfigManager.saveConfig();
        }

        ImGui.separator();

        // Disable Player Section
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


        // List of Disabled Players
        ImGui.text(Lang.get("gui.nickpaints.settings.disabled_list"));
        ImGui.beginChild("##disabledlist", 0, -ImGui.getFrameHeightWithSpacing() * 2, true);
        if (ConfigManager.CONFIG.disabledPlayers.isEmpty()) {
            ImGui.textDisabled(Lang.get("gui.nickpaints.settings.none"));
        } else {
            // Create a copy to prevent ConcurrentModificationException while rendering.
            List<Map.Entry<UUID, String>> disabledList = new ArrayList<>(ConfigManager.CONFIG.disabledPlayers.entrySet());
            for (Map.Entry<UUID, String> entry : disabledList) {
                ImGui.text(entry.getValue());
                ImGui.sameLine();
                if (ImGui.button(Lang.get("gui.nickpaints.settings.reenable_button") + "##" + entry.getKey())) {
                    ConfigManager.CONFIG.setPlayerRendering(entry.getKey(), entry.getValue(), true);
                    ConfigManager.saveConfig();
                }
            }
        }
        ImGui.endChild();

        // Clear Cache Button
        if (ImGui.button(Lang.get("gui.nickpaints.settings.clear_cache_button"))) {
            CloudSyncManager.clearCache();
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

    private void drawPreview() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        String currentGradient = reconstructGradientString();
        String playerName = client.player.getName().getString();
        float textWidth = ImGui.calcTextSize(playerName).x;
        float windowWidth = ImGui.getWindowWidth();
        float startX = ImGui.getWindowPosX() + (windowWidth - textWidth) / 2.0f;
        float startY = ImGui.getCursorScreenPos().y;
        float currentX = startX;
        for (int i = 0; i < playerName.length(); i++) {
            String characterStr = String.valueOf(playerName.charAt(i));
            int argbColor = GradientUtil.getColor(currentGradient, i, playerName.length());
            int a = (argbColor >> 24) & 0xFF;
            int r = (argbColor >> 16) & 0xFF;
            int g = (argbColor >> 8) & 0xFF;
            int b = argbColor & 0xFF;
            int abgrColor = (a << 24) | (b << 16) | (g << 8) | r;
            ImGui.getWindowDrawList().addText(currentX, startY, abgrColor, characterStr);
            currentX += ImGui.calcTextSize(characterStr).x;
        }
        ImGui.dummy(0, ImGui.getTextLineHeightWithSpacing());
    }

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

    private void renderOptions() {
        ImGui.spacing(); ImGui.separator(); ImGui.text(Lang.get("gui.nickpaints.section.options"));
        ImGui.checkbox(Lang.get("gui.nickpaints.option.static"), isStatic);
        if (isStatic.get()) ImGui.beginDisabled();
        ImGui.text(Lang.get("gui.nickpaints.option.speed"));
        ImGui.sliderInt("##speed", speed.getData(), 1000, 20000);
        if (isStatic.get()) ImGui.endDisabled();
        ImGui.text(Lang.get("gui.nickpaints.option.segment"));
        ImGui.sliderInt("##segment", segment.getData(), 1, 100);
        ImGui.checkbox(Lang.get("gui.nickpaints.option.style_block"), isBlockStyle);
        ImGui.sameLine();
        ImGui.checkbox(Lang.get("gui.nickpaints.option.direction_rtl"), isRightToLeft);
    }

    private void parseGradientString(String gradientString) {
        if (gradientString == null || gradientString.isEmpty()) { colors.add(new float[]{1f,1f,1f}); return; }
        Matcher rainbowMatcher = Pattern.compile("rainbow\\((\\d+)\\)").matcher(gradientString);
        if (rainbowMatcher.matches()) { isRainbowMode.set(true); rainbowSpeed.set(Integer.parseInt(rainbowMatcher.group(1))); return; }
        isRainbowMode.set(false);
        String tempString = gradientString.toLowerCase();
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
        Matcher directionMatcher = Pattern.compile("direction\\((rtl|ltr)\\)").matcher(tempString);
        isRightToLeft.set(directionMatcher.find() && "rtl".equals(directionMatcher.group(1)));
        if (isRightToLeft.get()) tempString = directionMatcher.replaceAll("");
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

    private String reconstructGradientString() {
        if (isRainbowMode.get()) { return String.format("rainbow(%d)", rainbowSpeed.get()); }
        StringBuilder sb = new StringBuilder();
        String colorsString = colors.stream().map(color -> String.format("#%02x%02x%02x", (int)(color[0]*255), (int)(color[1]*255), (int)(color[2]*255))).collect(Collectors.joining(", "));
        sb.append(colorsString);
        if (isStatic.get()) { sb.append(" static(true)"); } else { sb.append(" speed(").append(speed.get()).append(")"); }
        sb.append(" segment(").append(segment.get()).append(")");
        if (isBlockStyle.get()) { sb.append(" style(block)"); }
        if (isRightToLeft.get()) { sb.append(" direction(rtl)"); }
        return sb.toString().trim();
    }

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

    @Override
    public boolean shouldPause() {
        return false;
    }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            ImGui.setWindowFocus(null);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override
    public void removed() {
        long windowHandle = MinecraftClient.getInstance().getWindow().getHandle();
        GLFW.glfwSetCursor(windowHandle, 0L);
        super.removed();
    }
}