package net.p4pingvin4ik.NickPaints.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.world.entity.player.Player;
import net.p4pingvin4ik.NickPaints.client.NickPaintsMod;

import java.util.Optional;

/**
 * Locates the substring of a nametag {@link Component} that is the player's login name,
 * in the same plain-text order as {@link net.minecraft.client.gui.Font} draws glyphs.
 * Prefixes (badges, team prefix) and suffixes stay outside this range so they keep vanilla colors.
 */
public final class NametagNicknameLocator {

    private NametagNicknameLocator() {}

    public record CharRange(int start, int end) {
        public int length() {
            return end - start;
        }
    }

    /**
     * @return {@code [start, end)} glyph indices for the nickname only, or empty if unknown (whole label is painted then).
     */
    public static Optional<CharRange> findNicknameRange(Component label, Player player) {
        String profile = player.getGameProfile().name();
        Optional<CharRange> byProfileLeaf = findExactPlainLeaf(label, profile);
        if (byProfileLeaf.isPresent()) {
            return byProfileLeaf;
        }
        String nameText = player.getName().getString();
        if (!nameText.equals(profile)) {
            Optional<CharRange> byName = findExactPlainLeaf(label, nameText);
            if (byName.isPresent()) {
                return byName;
            }
        }
        if (profile.length() >= 3) {
            String flat = flattenPlainText(label);
            int idx = flat.indexOf(profile);
            if (idx >= 0) {
                return Optional.of(new CharRange(idx, idx + profile.length()));
            }
        }
        return Optional.empty();
    }

    private static Optional<CharRange> findExactPlainLeaf(Component root, String target) {
        if (target == null || target.isEmpty()) {
            return Optional.empty();
        }
        Cursor c = new Cursor();
        return walkLeaf(root, target, c);
    }

    private static Optional<CharRange> walkLeaf(Component component, String target, Cursor cursor) {
        if (NickPaintsMod.PROTECTED_TAG_INSERTION_KEY.equals(component.getStyle().getInsertion())) {
            return Optional.empty();
        }
        if (component.getContents() instanceof PlainTextContents literal) {
            String s = literal.text();
            int nonWsCount = 0;
            for (int i = 0; i < s.length(); i++) {
                if (!Character.isWhitespace(s.charAt(i))) {
                    nonWsCount++;
                }
            }
            int start = cursor.index;
            if (s.equals(target)) {
                return Optional.of(new CharRange(start, start + nonWsCount));
            }
            cursor.index += nonWsCount;
        }
        for (Component sibling : component.getSiblings()) {
            Optional<CharRange> hit = walkLeaf(sibling, target, cursor);
            if (hit.isPresent()) {
                return hit;
            }
        }
        return Optional.empty();
    }

    private static String flattenPlainText(Component component) {
        StringBuilder sb = new StringBuilder();
        appendPlain(component, sb);
        return sb.toString();
    }

    private static void appendPlain(Component component, StringBuilder sb) {
        if (NickPaintsMod.PROTECTED_TAG_INSERTION_KEY.equals(component.getStyle().getInsertion())) {
            return;
        }
        if (component.getContents() instanceof PlainTextContents literal) {
            String s = literal.text();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (!Character.isWhitespace(c)) {
                    sb.append(c);
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            appendPlain(sibling, sb);
        }
    }

    private static final class Cursor {
        int index;
    }
}
