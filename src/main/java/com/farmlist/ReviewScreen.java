package com.farmlist;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Shown after an import when some lines were not real items (for example "temporary_blocks", or a misspelled id).
 * Those lines are NOT on the checklist. If there is a close match you can press Use to add it, or Skip to drop the line.
 */
public class ReviewScreen extends Screen {

    private static final int ROW_H = 30;
    private static final int HEADER_H = 40;
    private static final int FOOTER_H = 34;
    private static final int BTN_W = 34;

    private final String fileName;
    private final List<FarmListClient.Rejected> items = new ArrayList<>(FarmListClient.pendingReview);
    private int scroll = 0;
    private boolean dragging = false;

    private int px, py, pw, ph;
    private int listX, listY, listW, listH;

    public ReviewScreen(String fileName) {
        super(Component.literal("Review Import"));
        Ui.startFlash();
        this.fileName = fileName;
    }

    @Override
    protected void init() {
        pw = Math.min(this.width - 16, 320);
        ph = Math.min(this.height - 16, 340);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        listX = px + 8;
        listW = pw - 16 - Ui.SB_W - 2;
        listY = py + HEADER_H;
        listH = ph - HEADER_H - FOOTER_H;
        clampScroll();

        addRenderableWidget(Button.builder(Component.literal("Continue"), b -> {
            FarmListClient.pendingReview = new ArrayList<>();
            this.minecraft.gui.setScreen(new ChecklistScreen(fileName));
        }).bounds(px + 8, py + ph - 28, pw - 16, 20).build());
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, items.size() * ROW_H - listH)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (Ui.in((int) mouseX, (int) mouseY, px, listY, pw, listH)) {
            scroll -= (int) (scrollY * ROW_H);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int mx = (int) event.x();
        int my = (int) event.y();
        if (event.button() == 0 && Ui.in(mx, my, listX + listW + 2, listY, Ui.SB_W, listH)) {
            dragging = true;
            scroll = Ui.scrollFromY(my, listY, listH, items.size() * ROW_H, listH);
            return true;
        }
        if (event.button() == 0 && Ui.in(mx, my, listX, listY, listW, listH)) {
            int index = (my - listY + scroll) / ROW_H;
            if (index >= 0 && index < items.size()) {
                int ry = listY + index * ROW_H - scroll;
                FarmListClient.Rejected r = items.get(index);
                int btnY = ry + 5;
                int skipX = listX + listW - BTN_W - 4;
                int useX = skipX - BTN_W - 4;
                if (Ui.in(mx, my, skipX, btnY, BTN_W, 16)) {
                    items.remove(index);
                    clampScroll();
                    return true;
                }
                if (r.suggestion() != null && Ui.in(mx, my, useX, btnY, BTN_W, 16)) {
                    FarmListClient.appendRow(fileName, r.suggestion(), r.count());
                    items.remove(index);
                    clampScroll();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            scroll = Ui.scrollFromY((int) event.y(), listY, listH, items.size() * ROW_H, listH);
            clampScroll();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Ui.panel(g, px, py, pw, ph);
        Ui.flash(g, px, py, pw, ph);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        Ui.textCenter(g, this.font, "Not real items (left out of the list)", px + pw / 2, py + 7, Ui.TEXT_DARK, false);
        Ui.textCenter(g, this.font, "These lines have no valid item id, so they were not added.",
                px + pw / 2, py + 20, Ui.TEXT_MUTED, false);

        if (items.isEmpty()) {
            Ui.textCenter(g, this.font, "All clear.", px + pw / 2, listY + 12, Ui.TEXT_DARK, false);
        }
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < items.size(); i++) {
            int ry = listY + i * ROW_H - scroll;
            if (ry + ROW_H < listY || ry > listY + listH) continue;
            drawItem(g, items.get(i), listX, ry, mouseX, mouseY);
        }
        g.disableScissor();
        Ui.scrollbar(g, listX + listW + 2, listY, listH, scroll, items.size() * ROW_H, listH);
    }

    private void drawItem(GuiGraphicsExtractor g, FarmListClient.Rejected r, int x, int y, int mouseX, int mouseY) {
        int h = ROW_H - 2;
        Ui.inset(g, x, y, listW, h, 0xFF373737);
        int skipX = x + listW - BTN_W - 4;
        int useX = skipX - BTN_W - 4;
        boolean inList = Ui.in(mouseX, mouseY, listX, listY, listW, listH);
        Ui.miniButton(g, this.font, skipX, y + 5, BTN_W, 16, "Skip", inList && Ui.in(mouseX, mouseY, skipX, y + 5, BTN_W, 16));
        int textMax = (r.suggestion() != null ? useX : skipX) - x - 14;
        if (r.suggestion() != null) {
            Ui.miniButton(g, this.font, useX, y + 5, BTN_W, 16, "Use", inList && Ui.in(mouseX, mouseY, useX, y + 5, BTN_W, 16));
        }
        Ui.text(g, this.font, Ui.fit(this.font, r.text(), textMax), x + 6, y + 4, 0xFFFF8888, true);
        String sub = r.suggestion() != null ? "Did you mean " + r.suggestion() + " x" + r.count() + "?" : "No close match";
        Ui.text(g, this.font, Ui.fit(this.font, sub, textMax), x + 6, y + 14, 0xFFAAAAAA, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
