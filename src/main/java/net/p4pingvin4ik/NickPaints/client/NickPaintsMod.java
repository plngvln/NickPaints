package net.p4pingvin4ik.NickPaints.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.p4pingvin4ik.NickPaints.client.commands.NickPaintsCommands;
import net.p4pingvin4ik.NickPaints.config.ConfigManager;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NickPaintsMod implements ClientModInitializer {

    public static final String MOD_ID = "nickpaints";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String PROTECTED_TAG_INSERTION_KEY = "NICKPAINTS_PROTECTED_V1";

    private static KeyBinding keyBinding;

    static {Runtime.getRuntime().addShutdownHook(new Thread(WebSocketManager::disconnect));}

    @Override
    public void onInitializeClient() {
        ConfigManager.loadConfig();
        NickPaintsCommands.register();
        VersionChecker.checkForUpdates();

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            LOGGER.info("initializing NickPaints...");
        });

        keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.nickpaints.open_gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_MINUS, "category.nickpaints.main"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (keyBinding.wasPressed()) {
                client.setScreen(new NickPaintsScreen());
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
                                client.player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME);
                                client.player.sendMessage(createWelcomeMessage(), false);
                            }
                        });
                    } catch (InterruptedException e) {
                        LOGGER.error("Failed to send NickPaints welcome message", e);
                    }
                }).start();
            }
        });
        LOGGER.info("NickPaints Mod initialized.");
    }

    private Text createWelcomeMessage() {
        return Text.literal("[NickPaints] ").formatted(Formatting.AQUA)
                .append(Text.translatable("chat.nickpaints.welcome.main").formatted(Formatting.WHITE))
                .append(" ")
                .append(Text.translatable("chat.nickpaints.welcome.click")
                        .formatted(Formatting.YELLOW, Formatting.BOLD)
                        .styled(style -> style
                                .withClickEvent(new ClickEvent.RunCommand("/nickpaints"))
                                .withHoverEvent(new HoverEvent.ShowText(Text.translatable("chat.nickpaints.welcome.hover")))
                        )
                )
                .append(Text.literal(".").formatted(Formatting.GRAY));
    }
}