package net.p4pingvin4ik.NickPaints.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Text;

import java.util.Optional;

/**
 * Locates the substring of a nametag {@link Text} that is the player's login name,
 * in the same plain-text order as {@link net.minecraft.client.font.TextRenderer} draws glyphs.
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
    public static Optional<CharRange> findNicknameRange(Text label, PlayerEntity player) {
        String profile = player.getGameProfile().getName();
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

    private static Optional<CharRange> findExactPlainLeaf(Text root, String target) {
        if (target == null || target.isEmpty()) {
            return Optional.empty();
        }
        Cursor c = new Cursor();
        return walkLeaf(root, target, c);
    }

    private static Optional<CharRange> walkLeaf(Text component, String target, Cursor cursor) {
        if (component.getContent() instanceof PlainTextContent literal) {
            String s = literal.string();
            int start = cursor.index;
            if (s.equals(target)) {
                return Optional.of(new CharRange(start, start + s.length()));
            }
            cursor.index += s.length();
        }
        for (Text sibling : component.getSiblings()) {
            Optional<CharRange> hit = walkLeaf(sibling, target, cursor);
            if (hit.isPresent()) {
                return hit;
            }
        }
        return Optional.empty();
    }

    private static String flattenPlainText(Text component) {
        StringBuilder sb = new StringBuilder();
        appendPlain(component, sb);
        return sb.toString();
    }

    private static void appendPlain(Text component, StringBuilder sb) {
        if (component.getContent() instanceof PlainTextContent literal) {
            sb.append(literal.string());
        }
        for (Text sibling : component.getSiblings()) {
            appendPlain(sibling, sb);
        }
    }

    private static final class Cursor {
        int index;
    }
}
