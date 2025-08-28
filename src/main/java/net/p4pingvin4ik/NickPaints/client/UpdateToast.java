package net.p4pingvin4ik.NickPaints.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * A custom toast implementation for displaying update notifications.
 * This version is compatible with modern Minecraft's Toast interface and rendering methods.
 */
public class UpdateToast implements Toast {

    private static final Identifier TEXTURE = Identifier.of(NickPaintsMod.MOD_ID, "textures/gui/toast/update_toast.png");

    private final Text title;
    private final Text description;
    private long startTime;
    private boolean justUpdated;
    private Visibility visibility;

    public UpdateToast(Text title, Text description) {
        this.title = title;
        this.description = description;
        this.justUpdated = true;
        this.visibility = Visibility.SHOW;
    }

    /**
     * This method is now responsible for updating the toast's visibility state.
     * The rendering is handled separately in the draw method.
     */
    @Override
    public void update(ToastManager manager, long time) {
        if (this.justUpdated) {
            this.startTime = time;
            this.justUpdated = false;
        }

        // The toast will be visible for 5000 milliseconds (5 seconds).
        if ((time - this.startTime) < 5000L) {
            this.visibility = Visibility.SHOW;
        } else {
            this.visibility = Visibility.HIDE;
        }
    }

    /**
     * This method handles the actual rendering of the toast.
     * It is called by the ToastManager when the visibility is SHOW.
     */
    @Override
    public void draw(DrawContext context, TextRenderer textRenderer, long startTime) {
        // We draw the full 160x32 texture from the top-left corner (0,0) of the image file.
        context.drawTexture(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0, 0, this.getWidth(), this.getHeight(), 160, 32);

        // We'll use the Brush item as a thematic icon.
        ItemStack icon = new ItemStack(Items.BRUSH);
        // Draw the icon on the left side of the toast.
        context.drawItem(icon, 8, 8);

        // Draw the title and description text next to the icon.
        context.drawText(textRenderer, this.title, 30, 7, 0xFF55FF55, true); // Bright green title
        context.drawText(textRenderer, this.description, 30, 18, 0xFFFFFFFF, true); // White description
    }

    /**
     * This required method returns the current visibility state of the toast.
     * The ToastManager uses this to decide whether to draw the toast and play sounds.
     */
    @Override
    public Visibility getVisibility() {
        return this.visibility;
    }
}