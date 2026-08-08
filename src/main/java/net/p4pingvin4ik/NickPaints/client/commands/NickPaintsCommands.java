package net.p4pingvin4ik.NickPaints.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.p4pingvin4ik.NickPaints.client.NickPaintsScreen;
import net.p4pingvin4ik.NickPaints.client.MojangAPIHelper;
import net.p4pingvin4ik.NickPaints.client.WebSocketManager;
import net.p4pingvin4ik.NickPaints.config.ConfigManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class NickPaintsCommands {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(NickPaintsCommands::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(literal("nickpaints")
                .executes(context -> {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().gui.setScreen(new NickPaintsScreen());
                    });
                    return 1;
                })
                .then(literal("clear-cache")
                        .executes(context -> {
                            WebSocketManager.clearCache();
                            context.getSource().sendFeedback(Component.literal("NickPaints cache cleared. Nicknames will be re-fetched."));
                            return 1;
                        })
                )
                .then(literal("toggle")
                        .then(literal("global")
                                .executes(context -> {
                                    boolean newState =ConfigManager.CONFIG.setGlobalRendering(!ConfigManager.CONFIG.globalRenderingEnabled);
                                    ConfigManager.saveConfig();
                                    sendToggleFeedback(context.getSource(), "Global paint rendering", newState);
                                    return 1;
                                })
                                .then(argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> {
                                            boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                            ConfigManager.CONFIG.setGlobalRendering(enabled);
                                            ConfigManager.saveConfig();
                                            sendToggleFeedback(context.getSource(), "Global paint rendering", enabled);
                                            return 1;
                                        })
                                )
                        )
                        .then(literal("player")
                                .then(argument("username", StringArgumentType.string())
                                        .suggests(NickPaintsCommands::getPlayerSuggestions)
                                        .executes(context -> {
                                            String username = StringArgumentType.getString(context, "username");
                                            togglePlayerSetting(context.getSource(), username, null);
                                            return 1;
                                        })
                                        .then(argument("enabled", BoolArgumentType.bool())
                                                .executes(context -> {
                                                    String username = StringArgumentType.getString(context, "username");
                                                    boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                                    togglePlayerSetting(context.getSource(), username, enabled);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(literal("list")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Component.literal("--- NickPaints Settings ---").withStyle(ChatFormatting.YELLOW));
                                    Component globalStatus = Component.literal("Global Rendering: ")
                                            .append(ConfigManager.CONFIG.globalRenderingEnabled ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN) : Component.literal("DISABLED").withStyle(ChatFormatting.RED));
                                    context.getSource().sendFeedback(globalStatus);
                                    context.getSource().sendFeedback(Component.literal("Disabled players:").withStyle(ChatFormatting.YELLOW));
                                    if (ConfigManager.CONFIG.disabledPlayers.isEmpty()) {
                                        context.getSource().sendFeedback(Component.literal("  None").withStyle(ChatFormatting.GRAY));
                                    } else {
                                        ConfigManager.CONFIG.disabledPlayers.forEach((uuid, name) -> {
                                            context.getSource().sendFeedback(
                                                    Component.literal("  - " + name).withStyle(ChatFormatting.GRAY)
                                                            .append(Component.literal(" (" + uuid.toString() + ")").withStyle(ChatFormatting.DARK_GRAY))
                                            );
                                        });
                                    }
                                    return 1;
                                })
                        )
                )
        );
    }

    private static CompletableFuture<Suggestions> getPlayerSuggestions(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase();
        context.getSource().getOnlinePlayerNames().stream()
                .filter(name -> name.toLowerCase().startsWith(input))
                .forEach(builder::suggest);
        ConfigManager.CONFIG.getDisabledPlayerNames().stream()
                .filter(name -> name.toLowerCase().startsWith(input))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static void togglePlayerSetting(FabricClientCommandSource source, String username, Boolean enabledState) {
        source.sendFeedback(Component.literal("Resolving " + username + "...").withStyle(ChatFormatting.GRAY));
        MojangAPIHelper.getUuidForUsername(username).thenAccept(uuidOptional -> {
            source.getClient().execute(() -> {
                if (uuidOptional.isEmpty()) {
                    source.sendError(Component.literal("Player '" + username + "' not found via Mojang API."));
                    return;
                }
                UUID playerUuid = (UUID) uuidOptional.get();
                boolean isCurrentlyDisabled = ConfigManager.CONFIG.disabledPlayers.containsKey(playerUuid);
                boolean newState = (enabledState != null) ? enabledState : isCurrentlyDisabled;

                ConfigManager.CONFIG.setPlayerRendering(playerUuid, username, newState);

                ConfigManager.saveConfig();

                sendToggleFeedback(source, "Paint rendering for " + username, newState);
            });
        });
    }

    private static void sendToggleFeedback(FabricClientCommandSource source, String action, boolean enabled) {
        Component status = enabled
                ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN)
                : Component.literal("DISABLED").withStyle(ChatFormatting.RED);
        source.sendFeedback(Component.literal(action + " set to ").append(status));
    }
}