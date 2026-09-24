package org.kyowa.familyaddons.storage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** A single-line text field for the storage search box. */
public class TextInput {

    private static final Set<String> LETTERS = Set.of(
            "a", "b", "c", "d", "e", "f",
            "g", "h", "i", "j", "k",
            "l", "m", "n", "o", "p", "q",
            "r", "s", "t", "u", "v", "w",
            "x", "y", "z", "_", " ", "-", "+", ".", ",", "[", "]", "{", "}", "(", ")", "&", "'", "\"",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"
    );

    private final List<Consumer<String>> onGuiKeys = new ArrayList<>();
    private final List<Consumer<String>> onEnter = new ArrayList<>();

    /** number of characters to the right of the cursor */
    public int pointerIndex = 0;
    public String text = "";
    public boolean isActive = false;
    public boolean isCtrlDown = false;
    public int[] selection = {0, 0};

    private final boolean singleUse;
    private final String placeholderText;

    public TextInput(boolean singleUse, String placeholderText) {
        this.singleUse = singleUse;
        this.placeholderText = placeholderText;
    }

    private void fireGuiKeys() {
        for (Consumer<String> cb : new ArrayList<>(onGuiKeys)) cb.accept(text);
    }

    /**
     * The key-code half of the "guiKey" handler (LWJGL2 key codes mapped to GLFW).
     *
     * @return true when the key event must be cancelled (input is active)
     */
    public boolean onKeyPressed(int key) {
        if (!isActive) return false;
        switch (key) {
            case GLFW.GLFW_KEY_ENTER: // 28
                for (Consumer<String> cb : new ArrayList<>(onEnter)) cb.accept(text);
                break;
            case GLFW.GLFW_KEY_ESCAPE: // 1
                isActive = false;
                if (Minecraft.getInstance().gui.screen() != null) Minecraft.getInstance().gui.screen().onClose();
                break;
            case GLFW.GLFW_KEY_BACKSPACE: // 14
                text = text.substring(Math.min(selection[1], text.length()));
                selection = new int[]{0, 0};
                if (pointerIndex < text.length()) {
                    int cut = text.length() - pointerIndex;
                    text = text.substring(0, cut - 1) + text.substring(cut);
                }
                if (isCtrlDown) {
                    for (int i = text.length() - 1; i > -1 && text.charAt(i) != ' '; i--) {
                        text = text.substring(0, i);
                    }
                }
                fireGuiKeys();
                break;
            case GLFW.GLFW_KEY_DELETE: // 211
                if (pointerIndex > 0) {
                    int cut = text.length() - pointerIndex;
                    text = text.substring(0, cut) + text.substring(cut + 1);
                    pointerIndex--;
                    fireGuiKeys();
                }
                break;
            case GLFW.GLFW_KEY_LEFT: // 203
                selection = new int[]{0, 0};
                if (pointerIndex < text.length()) {
                    pointerIndex++;
                }
                break;
            case GLFW.GLFW_KEY_RIGHT: // 205
                selection = new int[]{0, 0};
                if (pointerIndex > 0) {
                    pointerIndex--;
                }
                break;
            case GLFW.GLFW_KEY_A: // 30
                if (!isCtrlDown) break;
                selection = new int[]{0, text.length()};
                break;
            default:
                break;
        }
        return true;
    }

    /**
     * The character half of the "guiKey" handler.
     *
     * @return true when the event must be cancelled (input is active)
     */
    public boolean onCharTyped(String ch) {
        if (!isActive) return false;
        if (ch == null || ch.isEmpty()) return true;
        if (!LETTERS.contains(Utils.removeFormatting(ch).toLowerCase())) return true;
        text = text.substring(Math.min(selection[1], text.length()));
        selection = new int[]{0, 0};
        int cut = text.length() - pointerIndex;
        text = text.substring(0, cut) + ch + text.substring(cut);
        fireGuiKeys();
        return true;
    }

    /** "guiClosed" handler */
    public void onGuiClosed() {
        isActive = false;
        isCtrlDown = false;
    }

    public void draw(GuiGraphicsExtractor ctx, double x, double y, double w, double h, double mx, double my) {
        String shown = text.isEmpty() ? placeholderText : text;
        String prefix = isActive || (mx > x - 2 && mx < x + w + 2 && my > y - 2 && my < y + h + 2) ? "&e" : "&7";
        Utils.drawString(ctx, prefix + shown, x + 2, y + 2, true);
        if (!isActive) return;
        int width = Utils.getStringWidth(text.substring(0, Math.max(0, text.length() - pointerIndex)));
        int selStart = Math.min(selection[0], text.length());
        int selEnd = Math.min(selection[1], text.length());
        int beginning = Utils.getStringWidth(text.substring(0, selStart));
        int mid = Utils.getStringWidth(text.substring(selStart, Math.max(selStart, selEnd)));
        Utils.drawRect(ctx, Utils.color(50, 150, 50, 100), x + 2 + beginning, y + 2, mid, 10);
        Utils.drawLine(ctx, Utils.color(220, 220, 220), x + width + 2, y + h, x + width + 8, y + h, 1.5);
        isCtrlDown = Utils.isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL);
    }

    public void onGuiKey(Consumer<String> cb) {
        onGuiKeys.add(cb);
    }

    public void clearGuiKeyListeners() {
        onGuiKeys.clear();
    }

    public void onEnter(Consumer<String> cb) {
        onEnter.add(cb);
    }
}
