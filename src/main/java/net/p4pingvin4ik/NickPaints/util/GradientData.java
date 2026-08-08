package net.p4pingvin4ik.NickPaints.util;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.network.chat.Component;

/**
 * A data carrier class used to pass gradient information between mixins.
 * It is stored in a ThreadLocal to ensure thread safety during rendering.
 */
public class GradientData {

    // ThreadLocal ensures that each rendering thread has its own copy of the gradient data,
    // preventing race conditions and ensuring the correct gradient is used for each nametag.
    public static final ThreadLocal<GradientData> CURRENT_GRADIENT = new ThreadLocal<>();

    /**
     * Nametag rendering is deferred via {@code SubmitNodeCollector}; associate paint with the
     * submitted {@link Component} until {@code NameTagFeatureRenderer} draws glyphs.
     * Kept until both NORMAL and SEE_THROUGH submits are prepared (cleared in finishExecute).
     */
    public static final Map<Component, GradientData> PENDING_NAMETAG_GRADIENTS =
            Collections.synchronizedMap(new IdentityHashMap<>());

    /** Counts glyphs drawn for the current nametag when only the nickname substring is painted. */
    public static final ThreadLocal<AtomicInteger> NAMETAG_GLYPH_INDEX = ThreadLocal.withInitial(AtomicInteger::new);

    /** Left edge (pixels) of the first painted nickname glyph; used to align the gradient to the name only. */
    public static final ThreadLocal<AtomicReference<Float>> NAMETAG_GRADIENT_ANCHOR_X = ThreadLocal.withInitial(AtomicReference::new);

    public final String paintString;
    public final int totalLength;
    /** Inclusive glyph index; negative means paint the whole label (legacy behaviour). */
    public final int paintGlyphStart;
    /** Exclusive glyph index; ignored when {@code paintGlyphStart < 0}. */
    public final int paintGlyphEnd;

    public GradientData(String paintString, int totalLength) {
        this(paintString, totalLength, -1, -1);
    }

    public GradientData(String paintString, int totalLength, int paintGlyphStart, int paintGlyphEnd) {
        this.paintString = paintString;
        this.totalLength = totalLength;
        this.paintGlyphStart = paintGlyphStart;
        this.paintGlyphEnd = paintGlyphEnd;
    }

    public boolean isNicknameRestricted() {
        return paintGlyphStart >= 0;
    }
}
