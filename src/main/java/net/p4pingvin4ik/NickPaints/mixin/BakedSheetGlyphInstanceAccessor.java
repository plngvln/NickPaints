package net.p4pingvin4ik.NickPaints.mixin;

import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.font.glyphs.BakedSheetGlyph$GlyphInstance")
public interface BakedSheetGlyphInstanceAccessor {
    @Invoker("x")
    float nickpaints$x();

    @Invoker("y")
    float nickpaints$y();

    @Invoker("color")
    int nickpaints$color();

    @Invoker("style")
    Style nickpaints$style();

    @Invoker("shadowColor")
    int nickpaints$shadowColor();

    @Invoker("shadowOffset")
    float nickpaints$shadowOffset();

    @Invoker("boldOffset")
    float nickpaints$boldOffset();
}
