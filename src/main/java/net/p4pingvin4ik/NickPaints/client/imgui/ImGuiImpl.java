package net.p4pingvin4ik.NickPaints.client.imgui;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import imgui.*;
import imgui.extension.implot.ImPlot;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.texture.GlTexture;
import net.p4pingvin4ik.NickPaints.client.NickPaintsMod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;


import java.io.File;

/**
 * This class handles the low-level initialization and rendering loop for ImGui.
 * It is now a proper implementation based on the user-provided, working example.
 * It correctly manages Minecraft's framebuffer state to prevent crashes.
 */
public class ImGuiImpl {
    private final static ImGuiImplGlfw imGuiImplGlfw = new ImGuiImplGlfw();
    private final static ImGuiImplGl3 imGuiImplGl3 = new ImGuiImplGl3();
    private static final float FONT_SIZE_PIXELS = 20.0f;
    public static void create(final long handle) {
        ImGui.createContext();
        ImPlot.createContext();

        final ImGuiIO data = ImGui.getIO();
        data.setIniFilename("nickpaints.ini");

        try {
            final ImFontAtlas fontAtlas = data.getFonts();
            final ImFontConfig fontConfig = new ImFontConfig();
            final short[] glyphRanges = fontAtlas.getGlyphRangesCyrillic();

            File fontFile = new File("C:/Windows/Fonts/tahoma.ttf");
            if (fontFile.exists()) {
                fontAtlas.clear();
                fontAtlas.addFontFromFileTTF(fontFile.getAbsolutePath(), FONT_SIZE_PIXELS, fontConfig, glyphRanges);
            } else {
                NickPaintsMod.LOGGER.warn("System font tahoma.ttf not found. Using default ImGui font. Cyrillic characters may not render correctly.");
                fontAtlas.addFontDefault();
            }
            fontConfig.destroy();

        } catch (Exception e) {
            NickPaintsMod.LOGGER.error("Failed to load custom font for ImGui.", e);
        }
        data.setConfigFlags(ImGuiConfigFlags.DockingEnable);
        try {
            final ImFontAtlas fontAtlas = data.getFonts();
            final ImFontConfig fontConfig = new ImFontConfig();

            // This is crucial: it tells ImGui to not clear the atlas, so we can add to the default font.
            fontConfig.setMergeMode(true);
            fontConfig.setPixelSnapH(true);

            // Get the Cyrillic character range from ImGui's built-in ranges.
            final short[] glyphRanges = fontAtlas.getGlyphRangesCyrillic();

            // --- Path to a system font that supports Cyrillic ---
            // Tahoma is a clean, widely available font on Windows.
            // For cross-platform support, it's better to bundle a font with the mod.
            File fontFile = new File("C:/Windows/Fonts/tahoma.ttf");
            if (fontFile.exists()) {
                // Add the default font first.
                fontAtlas.addFontDefault();
                // Then, merge our new font with Cyrillic characters into the default one.
                fontAtlas.addFontFromFileTTF(fontFile.getAbsolutePath(), 16.0f, fontConfig, glyphRanges);
                NickPaintsMod.LOGGER.info("Successfully loaded and merged system font for Cyrillic support.");
            } else {
                NickPaintsMod.LOGGER.warn("System font tahoma.ttf not found. Cyrillic characters may not render correctly.");
            }

            // Clean up the config object.
            fontConfig.destroy();

        } catch (Exception e) {
            NickPaintsMod.LOGGER.error("Failed to load custom font for ImGui.", e);
        }
        data.getFonts().build();
        data.setConfigFlags(ImGuiConfigFlags.DockingEnable);

        // This is crucial: Build the font atlas.
        data.getFonts().build();

        imGuiImplGlfw.init(handle, true);
        imGuiImplGl3.init();
    }

    public static void draw(final RenderInterface renderInterface) {
        // Minecraft will not bind the framebuffer unless it is needed, so do it manually and hope Vulcan never gets real:tm:
        final Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        final int previousFramebuffer = ((GlTexture) framebuffer.getColorAttachment()).getOrCreateFramebuffer(((GlBackend) RenderSystem.getDevice()).getBufferManager(), null);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        GL11.glViewport(0, 0, framebuffer.viewportWidth, framebuffer.viewportHeight);

        // start frame
        imGuiImplGlfw.newFrame(); // Handle keyboard and mouse interactions
        ImGui.newFrame();

        // do rendering logic
        renderInterface.render(ImGui.getIO());

        // end frame
        ImGui.render();
        imGuiImplGl3.renderDrawData(ImGui.getDrawData());

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);

// Add this code if you have enabled Viewports in the create method
//        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
//            final long pointer = GLFW.glfwGetCurrentContext();
//            ImGui.updatePlatformWindows();
//            ImGui.renderPlatformWindowsDefault();
//
//            GLFW.glfwMakeContextCurrent(pointer);
//        }
    }

    public static void dispose() {
        imGuiImplGl3.dispose();
        imGuiImplGlfw.dispose();
        ImGui.destroyContext();
    }
}