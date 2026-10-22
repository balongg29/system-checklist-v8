package com.farmlist;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;

/**
 * A number box with up/down arrows for adding quantity by hand.
 * Click an arrow (or press the Up/Down key while the box is focused) to change by 1; hold Shift for 10.
 */
final class Stepper {

    static final int MAX = 99999;

    final EditBox box;
    private final int min;

    Stepper(EditBox box, int min) {
        this.box = box;
        this.min = min;
    }

    /** Width the arrows take, including the gap to the box. */
    static final int ARROW_W = 14;

    private int ax() {
        return box.getX() + box.getWidth() + 2;
    }

    private int upY() {
        return box.getY();
    }

    private int downY() {
        return box.getY() + 10;
    }

    /** The number in the box, or -1 if it is blank or not a number. */
    int value() {
        try {
            return Integer.parseInt(box.getValue().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    boolean isBlank() {
        return box.getValue().isBlank();
    }

    void step(int delta) {
        int v = value();
        if (v < 0) v = isBlank() ? (min > 0 ? min - 1 : 0) : min;
        v = Math.max(min, Math.min(MAX, v + delta));
        box.setValue(Integer.toString(v));
    }

    /** Handles a click on one of the arrows. Returns true if it was used. */
    boolean click(int mx, int my, boolean shift) {
        int d = shift ? 10 : 1;
        if (Ui.in(mx, my, ax(), upY(), 12, 10)) {
            step(d);
            return true;
        }
        if (Ui.in(mx, my, ax(), downY(), 12, 10)) {
            step(-d);
            return true;
        }
        return false;
    }

    /** Up/Down keys while the box is focused. */
    boolean key(int keyCode, boolean shift) {
        if (!box.isFocused()) return false;
        int d = shift ? 10 : 1;
        if (keyCode == InputConstants.KEY_UP) {
            step(d);
            return true;
        }
        if (keyCode == InputConstants.KEY_DOWN) {
            step(-d);
            return true;
        }
        return false;
    }

    void draw(GuiGraphicsExtractor g, int mx, int my) {
        Ui.arrowButton(g, ax(), upY(), true, Ui.in(mx, my, ax(), upY(), 12, 10));
        Ui.arrowButton(g, ax(), downY(), false, Ui.in(mx, my, ax(), downY(), 12, 10));
    }
}
