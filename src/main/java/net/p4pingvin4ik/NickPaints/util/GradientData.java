package net.p4pingvin4ik.NickPaints.util;

/**
 * A data carrier class used to pass gradient information between mixins.
 * It is stored in a ThreadLocal to ensure thread safety during rendering.
 */
public class GradientData {

    // ThreadLocal ensures that each rendering thread has its own copy of the gradient data,
    // preventing race conditions and ensuring the correct gradient is used for each nametag.
    public static final ThreadLocal<GradientData> CURRENT_GRADIENT = new ThreadLocal<>();

    public final String paintString;
    public final int totalLength;

    public GradientData(String paintString, int totalLength) {
        this.paintString = paintString;
        this.totalLength = totalLength;
    }
}