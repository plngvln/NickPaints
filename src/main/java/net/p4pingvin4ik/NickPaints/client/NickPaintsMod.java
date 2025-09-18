package net.p4pingvin4ik.NickPaints.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.p4pingvin4ik.NickPaints.client.commands.NickPaintsCommands;
import net.p4pingvin4ik.NickPaints.client.imgui.ImGuiImpl;
import net.p4pingvin4ik.NickPaints.config.ConfigManager;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NickPaintsMod implements ClientModInitializer {

    public static final String MOD_ID = "nickpaints";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding keyBinding;


    @Override
    public void onInitializeClient() {
        ConfigManager.loadConfig();
        NickPaintsCommands.register();
        VersionChecker.checkForUpdates();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            LOGGER.info("Minecraft client started, initializing ImGui context...");
            ImGuiImpl.create(client.getWindow().getHandle());
        });

        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.gradient_nickname.open_gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.gradient_nickname.main"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (keyBinding.wasPressed()) {
                client.setScreen(new ImGuiScreen());
            }
            CloudSyncManager.processQueue();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            VersionChecker.onPlayerJoin();
        });
        LOGGER.info("NickPaints Mod initialized.");
    }
}