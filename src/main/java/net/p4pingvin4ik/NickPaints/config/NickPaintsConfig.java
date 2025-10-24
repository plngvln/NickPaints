package net.p4pingvin4ik.NickPaints.config;

import java.util.*;

/**
 * A single data-holding class for all mod configurations.
 * This class is designed to be easily serialized and deserialized by Gson.
 * An instance of this class represents the complete state of the user's configuration.
 */
public class NickPaintsConfig {

    // The gradient string used for the local player's nametag.
    public String currentGradient = "rainbow(3000)";

    // Stores a list of user-saved gradient presets.
    public List<String> presets = new ArrayList<>();

    // If false, no paints will be rendered at all, overriding per-player settings.
    public boolean globalRenderingEnabled = true;

    // If true, the local player's own nametag will be rendered in third-person view.
    public boolean showOwnNametag = true;

    // Stores players whose paints are locally disabled. Maps UUID to the last known username.
    public Map<UUID, String> disabledPlayers = new HashMap<>();

    public boolean hasShownWelcomeMessage = false;

    public boolean hasCompletedGuiTutorial = false;

    public boolean setGlobalRendering(boolean enabled) {
        this.globalRenderingEnabled = enabled;
        return this.globalRenderingEnabled;
    }

    public String baseUrl = "wss://2api.nickpaints.ru";

    public String apiKey = "20oymFVk6uOPmY0s6/XLiHgS--yF=LsQuJ0x/hgzOBCDZ8yvG-hvZyNBFttmodyj";

    public boolean setPlayerRendering(UUID playerUuid, String username, boolean enabled) {
        if (enabled) {
            this.disabledPlayers.remove(playerUuid);
            return true;
        } else {
            this.disabledPlayers.put(playerUuid, username);
            return false;
        }
    }

    public boolean isRenderingEnabledFor(UUID playerUuid) {
        if (!this.globalRenderingEnabled) {
            return false;
        }
        return !this.disabledPlayers.containsKey(playerUuid);
    }

    public Collection<String> getDisabledPlayerNames() {
        return this.disabledPlayers.values();
    }
}