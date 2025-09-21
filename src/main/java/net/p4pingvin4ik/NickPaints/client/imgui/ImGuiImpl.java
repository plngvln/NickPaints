package net.p4pingvin4ik.NickPaints.client.imgui;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import imgui.*;
import imgui.extension.implot.ImPlot;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.type.ImInt;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.texture.GlTexture;
import net.p4pingvin4ik.NickPaints.client.NickPaintsMod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.lwjgl.opengl.ARBInternalformatQuery2.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.*;

/**
 * This class handles the low-level initialization and rendering loop for ImGui.
 * It is now a proper implementation based on the user-provided, working example.
 * It correctly manages Minecraft's framebuffer state to prevent crashes.
 */
public class ImGuiImpl {
    private final static ImGuiImplGlfw imGuiImplGlfw = new ImGuiImplGlfw();
    private final static ImGuiImplGl3 imGuiImplGl3 = new ImGuiImplGl3();
    private static final float FONT_SIZE_PIXELS = 18.0f;
    private static int gFontTexture = -1;
    public static void create(final long handle) {
        ImGui.createContext();
        ImPlot.createContext();

        final ImGuiIO data = ImGui.getIO();
        Path configPath = FabricLoader.getInstance().getConfigDir();
        data.setIniFilename(configPath.resolve("nickpaints.ini").toString());

        data.setConfigFlags(ImGuiConfigFlags.DockingEnable);

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
        data.getFonts().build();
        data.setConfigFlags(ImGuiConfigFlags.DockingEnable);

        data.getFonts().build();

        imGuiImplGlfw.init(handle, true);
        imGuiImplGl3.init();
    }
//    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
//        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
//        int nRead;
//        byte[] data = new byte[1024];
//        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
//            buffer.write(data, 0, nRead);
//        }
//        buffer.flush();
//        return buffer.toByteArray();
//    }
    public static void updateFontsTexture() {
        if (gFontTexture != -1) {
            glDeleteTextures(gFontTexture);
        }

        final ImFontAtlas fontAtlas = ImGui.getIO().getFonts();
        final ImInt width = new ImInt();
        final ImInt height = new ImInt();
        final ByteBuffer buffer = fontAtlas.getTexDataAsRGBA32(width, height);

        gFontTexture = glGenTextures();

        glBindTexture(GL_TEXTURE_2D, gFontTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(), height.get(), 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        fontAtlas.setTexID(gFontTexture);
    }
    public static void draw(final RenderInterface renderInterface) {
        if (gFontTexture == -1) {
            updateFontsTexture();
        }
        final Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        final int previousFramebuffer = ((GlTexture) framebuffer.getColorAttachment()).getOrCreateFramebuffer(((GlBackend) RenderSystem.getDevice()).getBufferManager(), null);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        GL11.glViewport(0, 0, framebuffer.viewportWidth, framebuffer.viewportHeight);

        // start frame
        imGuiImplGlfw.newFrame(); // Handle keyboard and mouse interactions
        ImGui.newFrame();

        // do rendering logic
        renderInterface.gradientNickname$render(ImGui.getIO());

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
        if (gFontTexture != -1) {
            glDeleteTextures(gFontTexture);
            gFontTexture = -1;
        }
        imGuiImplGl3.dispose();
        imGuiImplGlfw.dispose();
        ImGui.destroyContext();
    }
}