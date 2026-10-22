package com.farmlist;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Drawing helpers shared by all the screens: creative-inventory style panels, item slots,
 * real check boxes, text, scroll bars, and the hover label.
 * Every GuiGraphicsExtractor call lives here, so if a 26.x method name is off, fix it once in this file.
 */
final class Ui {

    private Ui() {
    }

    // System palette: deep navy windows with bright cyan edges
    static final int SYS_CYAN = 0xFF38E1FF;
    static final int SYS_CYAN_DIM = 0xFF2AA8C7;
    static final int SYS_NAVY = 0xFF0A1624;
    static final int SYS_NAVY_LIGHT = 0xFF12324A;
    static final int SYS_NAVY_HOVER = 0xFF1F5F80;
    static final int SYS_EDGE_DIM = 0xFF1B4A66;

    static final int TEXT_DARK = 0xFFE6F7FF;     // main text (light, because windows are now dark)
    static final int TEXT_MUTED = 0xFF7FB7CC;
    static final int WHITE = 0xFFFFFFFF;
    static final int GREEN = 0xFF55FF55;
    static final int GOLD = 0xFFFFD755;

    // ---------- text ----------

    static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, boolean shadow) {
        g.text(font, Component.literal(s), x, y, color, shadow);
    }

    static void textRight(GuiGraphicsExtractor g, Font font, String s, int rightX, int y, int color, boolean shadow) {
        text(g, font, s, rightX - font.width(s), y, color, shadow);
    }

    static void textCenter(GuiGraphicsExtractor g, Font font, String s, int centerX, int y, int color, boolean shadow) {
        text(g, font, s, centerX - font.width(s) / 2, y, color, shadow);
    }

    /** Shortens a string with ".." so it fits in maxWidth pixels. */
    static String fit(Font font, String s, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(s) <= maxWidth) return s;
        return font.plainSubstrByWidth(s, Math.max(0, maxWidth - font.width(".."))) + "..";
    }

    // ---------- shapes ----------

    static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------- opening flash ----------

    private static final int FLASH_MS = 250;
    private static volatile long flashStart = 0;

    /** Call when a system window opens: starts the cyan flash and plays a short level-up chime. */
    static void startFlash() {
        flashStart = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.4F));
        }
    }

    /** A translucent cyan wash over the window that fades out in a quarter second. */
    static void flash(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (flashStart == 0) return;
        long elapsed = System.currentTimeMillis() - flashStart;
        if (elapsed < 0 || elapsed >= FLASH_MS) return;
        int alpha = (int) (0x90 * (1.0 - elapsed / (double) FLASH_MS));
        g.fill(x, y, x + w, y + h, (alpha << 24) | (SYS_CYAN & 0x00FFFFFF));
    }

    /** The system window: deep navy with a cyan border and a cyan line under the title. */
    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, SYS_NAVY);
        frame(g, x, y, w, h, SYS_CYAN);
        g.fill(x + 2, y + 18, x + w - 2, y + 19, SYS_EDGE_DIM);
    }

    /** A sunken rectangle (dark blue edge around the face). */
    static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h, int face) {
        g.fill(x, y, x + w, y + h, SYS_EDGE_DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
    }

    /** One 18x18 inventory slot. */
    static void slot(GuiGraphicsExtractor g, int x, int y) {
        inset(g, x, y, 18, 18, 0xFF8B8B8B);
    }

    /** A 1px outline. */
    static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** A small button drawn by hand, used for the < and > arrows. */
    static void miniButton(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String label, boolean hover) {
        g.fill(x, y, x + w, y + h, SYS_CYAN);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? SYS_NAVY_HOVER : SYS_NAVY_LIGHT);
        textCenter(g, font, label, x + w / 2, y + (h - 9) / 2, TEXT_DARK, false);
    }

    /** A button with a cyan edge; lights up when hovered. tint (ARGB) replaces the face color when not 0. */
    static void textButton(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String label,
                           boolean hover, int tint) {
        g.fill(x, y, x + w, y + h, SYS_CYAN);
        int face = tint != 0 ? tint : (hover ? SYS_NAVY_HOVER : SYS_NAVY_LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        textCenter(g, font, fit(font, label, w - 4), x + w / 2, y + (h - 9) / 2, TEXT_DARK, false);
    }

    /** A real 12x12 check box with a check mark. */
    static void checkbox(GuiGraphicsExtractor g, int x, int y, boolean checked) {
        g.fill(x, y, x + 12, y + 12, 0xFF000000);
        g.fill(x + 1, y + 1, x + 11, y + 11, checked ? 0xFF3FA34D : 0xFFDADADA);
        if (!checked) {
            g.fill(x + 1, y + 1, x + 11, y + 2, 0xFF8B8B8B);   // inner shadow, top
            g.fill(x + 1, y + 1, x + 2, y + 11, 0xFF8B8B8B);   // inner shadow, left
            return;
        }
        int[][] dots = { {2, 5}, {3, 6}, {4, 7}, {5, 6}, {6, 5}, {7, 4}, {8, 3} };
        for (int[] d : dots) {
            g.fill(x + d[0], y + d[1], x + d[0] + 2, y + d[1] + 2, WHITE);
        }
    }

    /** A 12x12 colored square with a black outline (used for the checklist status). */
    static void statusSquare(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y, x + 12, y + 12, 0xFF000000);
        g.fill(x + 1, y + 1, x + 11, y + 11, color);
    }

    /** Draws the item icon with its top-left corner at x, y (items are 16x16). */
    static void icon(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        g.item(stack, x, y);
    }

    /** Scroll bar width. Wide enough to grab easily. */
    static final int SB_W = 10;

    private static int thumbH(int h, int content, int view) {
        return Math.max(16, (h - 2) * view / Math.max(1, content));
    }

    /** A wide vertical scroll bar. scroll/content/view can be pixels or rows, as long as they match. */
    static void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, int scroll, int content, int view) {
        inset(g, x, y, SB_W, h, SYS_NAVY_LIGHT);
        if (content <= view || content <= 0) return;
        int thumbH = thumbH(h, content, view);
        int range = content - view;
        int thumbY = y + 1 + (h - 2 - thumbH) * Math.min(scroll, range) / range;
        g.fill(x + 1, thumbY, x + SB_W - 1, thumbY + thumbH, SYS_CYAN_DIM);
    }

    /** Scroll value for a mouse y while dragging the scroll bar thumb. */
    static int scrollFromY(int my, int y, int h, int content, int view) {
        int range = content - view;
        if (range <= 0) return 0;
        int thumbH = thumbH(h, content, view);
        int track = h - 2 - thumbH;
        if (track <= 0) return 0;
        double t = (my - y - 1 - thumbH / 2.0) / track;
        t = Math.max(0, Math.min(1, t));
        return (int) Math.round(t * range);
    }

    /** A pencil icon button (16x16) used to edit a row. */
    static void pencilButton(GuiGraphicsExtractor g, int x, int y, boolean hover) {
        g.fill(x, y, x + 16, y + 16, 0xFF000000);
        g.fill(x + 1, y + 1, x + 15, y + 15, hover ? 0xFFDDDDDD : 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + 14, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + 14, 0xFFFFFFFF);
        g.fill(x + 1, y + 14, x + 15, y + 15, 0xFF555555);
        g.fill(x + 14, y + 2, x + 15, y + 15, 0xFF555555);
        // diagonal pencil: eraser, body, tip
        g.fill(x + 10, y + 3, x + 13, y + 6, 0xFFE07A7A);
        for (int i = 0; i < 6; i++) {
            g.fill(x + 9 - i, y + 4 + i, x + 12 - i, y + 7 + i, 0xFFF2C230);
        }
        g.fill(x + 3, y + 11, x + 5, y + 13, 0xFF404040);
    }

    /** A square raised button for an icon (tint 0 = normal, otherwise a color behind the icon). */
    static void iconButton(GuiGraphicsExtractor g, int x, int y, int size, boolean hover, int tint) {
        g.fill(x, y, x + size, y + size, 0xFF000000);
        int face = tint != 0 ? tint : (hover ? 0xFFDDDDDD : 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, face);
        g.fill(x + 1, y + 1, x + size - 2, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + size - 2, 0xFFFFFFFF);
        g.fill(x + 1, y + size - 2, x + size - 1, y + size - 1, 0xFF555555);
        g.fill(x + size - 2, y + 2, x + size - 1, y + size - 1, 0xFF555555);
        if (hover && tint == 0) g.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0x30FFFFFF);
    }

    /** One-line hover tip near the mouse. */
    static void tip(GuiGraphicsExtractor g, Font font, String text, int mx, int my, int screenW, int screenH) {
        int w = font.width(text) + 8;
        int h = 14;
        int x = Math.max(2, Math.min(mx + 10, screenW - w - 2));
        int y = Math.max(2, Math.min(my + 12, screenH - h - 2));
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, SYS_CYAN);
        g.fill(x, y, x + w, y + h, 0xF00A1624);
        text(g, font, text, x + 4, y + 3, WHITE, true);
    }

    /** A tiny up or down arrow button (10 wide, 8 tall) for the quantity boxes. */
    static void arrowButton(GuiGraphicsExtractor g, int x, int y, boolean up, boolean hover) {
        g.fill(x, y, x + 12, y + 10, 0xFF000000);
        g.fill(x + 1, y + 1, x + 11, y + 9, hover ? 0xFFDDDDDD : 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + 10, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 8, x + 11, y + 9, 0xFF555555);
        for (int i = 0; i < 3; i++) {
            int w = up ? 1 + i * 2 : 5 - i * 2;
            int ty = up ? y + 3 + i : y + 3 + i;
            g.fill(x + 6 - w / 2 - (w % 2 == 0 ? 0 : 0), ty, x + 6 - w / 2 + w, ty + 1, 0xFF404040);
        }
    }

    /** Hover label near the mouse: item name on top, item ID underneath. */
    static void label(GuiGraphicsExtractor g, Font font, String name, String sub, int mx, int my, int screenW, int screenH) {
        int w = Math.max(font.width(name), font.width(sub)) + 8;
        int h = 24;
        int x = Math.min(mx + 10, screenW - w - 2);
        int y = Math.max(2, Math.min(my - 6, screenH - h - 2));
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, SYS_CYAN);
        g.fill(x, y, x + w, y + h, 0xF00A1624);
        text(g, font, name, x + 4, y + 3, WHITE, true);
        text(g, font, sub, x + 4, y + 13, 0xFFAAAAAA, true);
    }
}
