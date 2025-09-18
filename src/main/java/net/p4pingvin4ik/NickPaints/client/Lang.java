package net.p4pingvin4ik.NickPaints.client;

import net.minecraft.client.resource.language.I18n;

/**
 * A simple helper class to bridge Minecraft's translation system with our ImGui interface.
 * This allows us to use standard .lang files for localization.
 */
public class Lang {

    /**
     * Gets the translated string for a given key.
     * This is the primary method used by the GUI to fetch localized text.
     * @param key The translation key (e.g., "gui.nickpaints.title").
     * @return The translated string in the currently selected language.
     * @deprecated The direct use of I18n is deprecated, but it remains the most straightforward
     *             way to get a raw string for non-Minecraft UI contexts like ImGui.
     *             A more modern approach would involve Text.translatable().getString(), but
     *             I18n is sufficient and performant here.
     */
    @SuppressWarnings("deprecation")
    public static String get(String key) {
        return I18n.translate(key);
    }
}