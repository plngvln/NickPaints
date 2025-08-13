package net.p4pingvin4ik.NickPaints.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.p4pingvin4ik.NickPaints.client.CloudSyncManager;
import net.p4pingvin4ik.NickPaints.client.MojangAPIHelper;
import net.p4pingvin4ik.NickPaints.config.ConfigManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class NickPaintsCommands {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(NickPaintsCommands::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(literal("nickpaints")
                .executes(context -> {
                    context.getSource().sendFeedback(Text.literal("Usage: /nickpaints <subcommand>"));
                    return 1;
                })
                .then(literal("clear-cache")
                        .executes(context -> {
                            CloudSyncManager.clearCache();
                            context.getSource().sendFeedback(Text.literal("NickPaints cache cleared. Nicknames will be re-fetched."));
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
                                            // The save call is now inside this helper method.
                                            togglePlayerSetting(context.getSource(), username, null);
                                            return 1;
                                        })
                                        .then(argument("enabled", BoolArgumentType.bool())
                                                .executes(context -> {
                                                    String username = StringArgumentType.getString(context, "username");
                                                    boolean enabled = BoolArgumentType.getBool(context, "enabled");
                                                    // The save call is now inside this helper method.
                                                    togglePlayerSetting(context.getSource(), username, enabled);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(literal("list")
                                .executes(context -> {
                                    context.getSource().sendFeedback(Text.literal("--- NickPaints Settings ---").formatted(Formatting.YELLOW));
                                    Text globalStatus = Text.literal("Global Rendering: ")
                                            .append(ConfigManager.CONFIG.globalRenderingEnabled ? Text.literal("ENABLED").formatted(Formatting.GREEN) : Text.literal("DISABLED").formatted(Formatting.RED));
                                    context.getSource().sendFeedback(globalStatus);
                                    context.getSource().sendFeedback(Text.literal("Disabled players:").formatted(Formatting.YELLOW));
                                    if (ConfigManager.CONFIG.disabledPlayers.isEmpty()) {
                                        context.getSource().sendFeedback(Text.literal("  None").formatted(Formatting.GRAY));
                                    } else {
                                       ConfigManager.CONFIG.disabledPlayers.forEach((uuid, name) -> {
                                            context.getSource().sendFeedback(
                                                    Text.literal("  - " + name).formatted(Formatting.GRAY)
                                                            .append(Text.literal(" (" + uuid.toString() + ")").formatted(Formatting.DARK_GRAY))
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
        context.getSource().getPlayerNames().stream()
                .filter(name -> name.toLowerCase().startsWith(input))
                .forEach(builder::suggest);
       ConfigManager.CONFIG.getDisabledPlayerNames().stream()
                .filter(name -> name.toLowerCase().startsWith(input))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static void togglePlayerSetting(FabricClientCommandSource source, String username, Boolean enabledState) {
        source.sendFeedback(Text.literal("Resolving " + username + "...").formatted(Formatting.GRAY));
        MojangAPIHelper.getUuidForUsername(username).thenAccept(uuidOptional -> {
            source.getClient().execute(() -> {
                if (uuidOptional.isEmpty()) {
                    source.sendError(Text.literal("Player '" + username + "' not found via Mojang API."));
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
        Text status = enabled
                ? Text.literal("ENABLED").formatted(Formatting.GREEN)
                : Text.literal("DISABLED").formatted(Formatting.RED);
        source.sendFeedback(Text.literal(action + " set to ").append(status));
    }
}