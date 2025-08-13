package net.p4pingvin4ik.NickPaints.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.p4pingvin4ik.NickPaints.client.NickPaintsMod;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

/**
 * Manages the loading and saving of the unified NickPaintsConfig object.
 * Provides a single, static point of access to the mod's configuration.
 */
public class ConfigManager {

    private static final Logger LOGGER = NickPaintsMod.LOGGER;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("nickpaints.json").toFile();

    // The single, static instance of our entire configuration.
    public static NickPaintsConfig CONFIG;

    /**
     * Loads the configuration from nickpaints.json.
     * If the file doesn't exist or is invalid, a new default config is created.
     */
    public static void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                CONFIG = GSON.fromJson(reader, NickPaintsConfig.class);
                if (CONFIG == null) { // Handle case where file is empty or malformed
                    throw new IOException("Config file is empty or malformed.");
                }
                // Ensure map is not null
                if (CONFIG.disabledPlayers == null) {
                    CONFIG.disabledPlayers = new HashMap<>();
                }
                LOGGER.info("NickPaints configuration loaded successfully.");
            } catch (Exception e) {
                LOGGER.error("Could not read NickPaints config file, creating new default config.", e);
                CONFIG = new NickPaintsConfig();
            }
        } else {
            LOGGER.info("No NickPaints config file found, creating a new one.");
            CONFIG = new NickPaintsConfig();
            saveConfig(); // Save the default config on first run
        }
    }

    /**
     * Saves the current CONFIG object to nickpaints.json.
     * Should be called after any modification to the configuration.
     */
    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(CONFIG, writer);
            LOGGER.debug("NickPaints configuration saved.");
        } catch (IOException e) {
            LOGGER.error("Could not save NickPaints config file.", e);
        }
    }
}