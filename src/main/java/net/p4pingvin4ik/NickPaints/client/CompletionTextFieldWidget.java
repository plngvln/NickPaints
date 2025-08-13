package net.p4pingvin4ik.NickPaints.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class CompletionTextFieldWidget extends TextFieldWidget {

    private static final List<String> SUGGESTIONS = List.of("rainbow()", "speed()", "segment()","static(true)","style(block)","direction()");

    public CompletionTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text message) {
        super(textRenderer, x, y, width, height, message);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.isFocused() && keyCode == GLFW.GLFW_KEY_TAB) {
            String text = this.getText();
            int cursorPosition = this.getCursor();

            int lastSpaceIndex = text.lastIndexOf(' ', cursorPosition - 1);
            String currentWord = text.substring(lastSpaceIndex + 1, cursorPosition);

            if (!currentWord.isEmpty()) {
                for (String suggestion : SUGGESTIONS) {
                    if (suggestion.toLowerCase().startsWith(currentWord.toLowerCase())) {

                        String textBefore = text.substring(0, lastSpaceIndex + 1);
                        String textAfter = text.substring(cursorPosition);
                        String newText = textBefore + suggestion + textAfter;

                        this.setText(newText);

                        int newCursorPos = (textBefore + suggestion).length() - 1;

                        this.setCursor(newCursorPos, false);

                        return true;
                    }
                }
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}