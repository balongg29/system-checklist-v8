package com.farmlist;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Text buttons for a screen. Three places:
 *  TOP_RIGHT: a small "X" in the panel's top-right corner (closes / goes back).
 *  BOTTOM:    an evenly spaced row of text buttons along the bottom of the panel.
 *  RIGHT:     a column of text buttons just outside the panel's right edge. If the screen is too narrow
 *             for that column, these move into a second bottom row instead.
 */
final class ToolBar {

    enum Place { TOP_RIGHT, BOTTOM, RIGHT }

    static final int BTN_H = 18;
    static final int SIDE_W = 46;
    static final int GAP = 3;
    static final int MARGIN = 8;          // inside the panel, left and right
    static final int STATUS_H = 12;       // status line under the buttons
    static final int X_SIZE = 14;

    private record Tool(String label, String tip, Runnable action, Place place, int tint) {
    }

    private final List<Tool> tools = new ArrayList<>();
    private int[] xs = new int[0];
    private int[] ys = new int[0];
    private int[] ws = new int[0];
    private int footerH = 0;

    /** tint: 0 normal, otherwise an ARGB fill (the red confirm state for Delete). */
    ToolBar add(String label, String tip, Place place, int tint, Runnable action) {
        tools.add(new Tool(label, tip, action, place, tint));
        return this;
    }

    ToolBar add(String label, String tip, Place place, Runnable action) {
        return add(label, tip, place, 0, action);
    }

    /** Space the list must leave under itself: buttons plus the status line. Valid after layout(). */
    int footerHeight() {
        return footerH;
    }

    /** Works out where every button goes. Call after the panel's position and size are known. */
    void layout(int px, int py, int pw, int ph, int screenW) {
        int n = tools.size();
        xs = new int[n];
        ys = new int[n];
        ws = new int[n];

        int bottomY = py + ph - STATUS_H - 4 - BTN_H;
        int bottomCount = count(Place.BOTTOM);
        int rightCount = count(Place.RIGHT);
        boolean sideFits = rightCount == 0 || px + pw + GAP + SIDE_W + 4 <= screenW;
        int rightRowY = bottomY - GAP - BTN_H;   // used only when the side column does not fit

        int bw = bottomCount == 0 ? 0 : (pw - 2 * MARGIN - (bottomCount - 1) * GAP) / bottomCount;
        int rw = rightCount == 0 ? 0 : (pw - 2 * MARGIN - (rightCount - 1) * GAP) / rightCount;

        int bi = 0;
        int ri = 0;
        for (int i = 0; i < n; i++) {
            Tool t = tools.get(i);
            switch (t.place()) {
                case TOP_RIGHT -> {
                    xs[i] = px + pw - 4 - X_SIZE;
                    ys[i] = py + 4;
                    ws[i] = X_SIZE;
                }
                case BOTTOM -> {
                    xs[i] = px + MARGIN + bi * (bw + GAP);
                    ys[i] = bottomY;
                    ws[i] = bw;
                    bi++;
                }
                case RIGHT -> {
                    if (sideFits) {
                        xs[i] = px + pw + GAP;
                        ys[i] = py + 24 + ri * (BTN_H + GAP);
                        ws[i] = SIDE_W;
                    } else {
                        xs[i] = px + MARGIN + ri * (rw + GAP);
                        ys[i] = rightRowY;
                        ws[i] = rw;
                    }
                    ri++;
                }
            }
        }

        boolean secondRow = rightCount > 0 && !sideFits;
        footerH = STATUS_H + 4 + BTN_H + 4 + (secondRow ? GAP + BTN_H : 0);
    }

    private int count(Place p) {
        int c = 0;
        for (Tool t : tools) if (t.place() == p) c++;
        return c;
    }

    boolean click(int mx, int my) {
        for (int i = 0; i < tools.size(); i++) {
            if (Ui.in(mx, my, xs[i], ys[i], ws[i], heightOf(i))) {
                tools.get(i).action().run();
                return true;
            }
        }
        return false;
    }

    private int heightOf(int i) {
        return tools.get(i).place() == Place.TOP_RIGHT ? X_SIZE : BTN_H;
    }

    void draw(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
        int hovered = -1;
        for (int i = 0; i < tools.size(); i++) {
            Tool t = tools.get(i);
            int h = heightOf(i);
            boolean hover = Ui.in(mx, my, xs[i], ys[i], ws[i], h);
            if (hover) hovered = i;
            Ui.textButton(g, font, xs[i], ys[i], ws[i], h, t.label(), hover, t.tint());
        }
        if (hovered >= 0) {
            Ui.tip(g, font, tools.get(hovered).tip(), mx, my, screenW, screenH);
        }
    }
}
