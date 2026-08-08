package net.p4pingvin4ik.NickPaints.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.p4pingvin4ik.NickPaints.client.commands.NickPaintsCommands;
import net.p4pingvin4ik.NickPaints.config.ConfigManager;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NickPaintsMod implements ClientModInitializer {

    public static final String MOD_ID = "nickpaints";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String PROTECTED_TAG_INSERTION_KEY = "NICKPAINTS_PROTECTED_V1";

    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main")
    );

    private static KeyMapping keyBinding;

    static {Runtime.getRuntime().addShutdownHook(new Thread(WebSocketManager::disconnect));}

    @Override
    public void onInitializeClient() {
        ConfigManager.loadConfig();
        NickPaintsCommands.register();
        VersionChecker.checkForUpdates();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            LOGGER.info("initializing NickPaints...");
        });

        keyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.nickpaints.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_MINUS,
                KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (keyBinding.consumeClick()) {
                client.gui.setScreen(new NickPaintsScreen());
            }
            WebSocketManager.updateVisiblePlayers();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            VersionChecker.onPlayerJoin();
            WebSocketManager.connect();

            if (!ConfigManager.CONFIG.hasShownWelcomeMessage) {
                new Thread(() -> {
                    try {
                        Thread.sleep(1500);
                        client.execute(() -> {
                            if (client.player != null) {
                                client.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
                                client.player.sendSystemMessage(createWelcomeMessage());
                                ConfigManager.CONFIG.hasShownWelcomeMessage = true;
                                ConfigManager.saveConfig();
                            }
                        });
                    } catch (InterruptedException e) {
                        LOGGER.error("Failed to send NickPaints welcome message", e);
                    }
                }).start();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            WebSocketManager.onWorldDisconnect();
        });
        LOGGER.info("NickPaints Mod initialized.");
    }

    private Component createWelcomeMessage() {
        return Component.literal("[NickPaints] ").withStyle(ChatFormatting.AQUA)
                .append(Component.translatable("chat.nickpaints.welcome.main").withStyle(ChatFormatting.WHITE))
                .append(" ")
                .append(Component.translatable("chat.nickpaints.welcome.click")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                        .withStyle(style -> style
                                .withClickEvent(new ClickEvent.RunCommand("/nickpaints"))
                                .withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.nickpaints.welcome.hover")))
                        )
                )
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY));
    }
}