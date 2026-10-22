package com.farmlist;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The checklist picker. Each checklist has a color square (gray = not started, yellow = in progress,
 * green = complete), its checked/total count, and a few item icons from inside it.
 */
public class PickerScreen extends Screen {

    private static final int ROW_H = 26;
    private static final int FOOTER_H = 50;   // button row (34) plus the status line above the legend

    static final int COLOR_NOT_STARTED = 0xFF888888;
    static final int COLOR_IN_PROGRESS = 0xFFFFD755;
    static final int COLOR_COMPLETE = 0xFF38E1FF;

    private record Entry(String file, String name, int checked, int total, int color, List<ItemStack> icons) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private int scroll = 0;
    private boolean dragging = false;

    private int headerH;
    private ToolBar bar;
    private int px, py, pw, ph;
    private int listX, listY, listW, listH;

    public PickerScreen() {
        super(Component.literal("Farm Checklist"));
        Ui.startFlash();
    }

    @Override
    protected void init() {
        pw = Math.min(this.width - 16, 260);
        ph = Math.min(this.height - 16, 240);
        px = (this.width - pw) / 2;           // centered on screen
        py = (this.height - ph) / 2;

        bar = new ToolBar()
                .add("New", "New checklist", ToolBar.Place.BOTTOM, () -> this.minecraft.gui.setScreen(new NameScreen()))
                .add("Import", "Import from clipboard (text, share or collaborate code)", ToolBar.Place.BOTTOM, () -> {
                    String imported = FarmListClient.importFromClipboard();
                    if (imported != null) {
                        this.minecraft.gui.setScreen(FarmListClient.pendingReview.isEmpty()
                                ? new ChecklistScreen(imported) : new ReviewScreen(imported));
                    }
                });
        bar.layout(px, py, pw, ph, this.width);

        headerH = 26;
        listX = px + 8;
        listW = pw - 16 - Ui.SB_W - 2;
        listY = py + headerH;
        listH = ph - headerH - FOOTER_H;

        loadEntries();
        clampScroll();
    }

    private void loadEntries() {
        entries.clear();
        for (String file : FarmListClient.listTranscriptions()) {
            String name = FarmListClient.baseName(file);
            Progress progress = new Progress(name);
            Map<String, Integer> required = FarmListClient.parseRequired(
                    FarmListClient.TRANSCRIPTION_DIR.resolve(file));

            int checked = 0;
            List<ItemStack> icons = new ArrayList<>();
            for (String id : required.keySet()) {
                if (progress.isChecked(id)) checked++;
                if (icons.size() >= 4) continue;
                if (FarmListClient.isPlaceholder(id) && FarmListClient.entryFor(FarmListClient.countedId(id, progress)) == null) continue;
                FarmListClient.CatalogEntry entry = FarmListClient.entryFor(FarmListClient.countedId(id, progress));
                if (entry != null) icons.add(entry.stack());
            }

            int color;
            if (progress.isComplete()) {
                color = COLOR_COMPLETE;
            } else if (checked > 0) {
                color = COLOR_IN_PROGRESS;
            } else {
                color = COLOR_NOT_STARTED;
            }
            entries.add(new Entry(file, name, checked, required.size(), color, icons));
        }
    }

    // ---------- the three-dot menu on each row ----------

    private static final int MENU_W = 66;
    private static final int MENU_ITEM_H = 18;

    /** Checklist whose three-dot menu is open, or null. */
    private String menuFile = null;
    /** Checklist whose Delete was clicked once; the next click deletes it. */
    private String deleteArmedFile = null;

    private int dotsX() {
        return listX + listW - 14;
    }

    private int rowY(int index) {
        return listY + index * ROW_H - scroll;
    }

    private int menuRowIndex() {
        if (menuFile == null) return -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).file().equals(menuFile)) return i;
        }
        return -1;
    }

    private int menuX() {
        return Math.min(dotsX() + 10 - MENU_W, px + pw - 8 - MENU_W);
    }

    /** Top of the menu: below the row, or above it if there is no room below. */
    private int menuY(int index, int height) {
        int below = rowY(index) + ROW_H - 2;
        if (below + height > listY + listH) return rowY(index) - height + 2;
        return below;
    }

    private void drawMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int i = menuRowIndex();
        if (i < 0) return;
        Entry e = entries.get(i);
        boolean complete = new Progress(e.name()).isComplete();
        int rows = complete ? 2 : 1;
        int height = rows * (MENU_ITEM_H + 2) + 2;
        int x = menuX();
        int y = menuY(i, height);

        g.fill(x - 2, y - 2, x + MENU_W + 2, y + height, Ui.SYS_CYAN);
        g.fill(x - 1, y - 1, x + MENU_W + 1, y + height - 1, 0xFF0A1624);

        int iy = y;
        if (complete) {
            boolean hover = Ui.in(mouseX, mouseY, x, iy, MENU_W, MENU_ITEM_H);
            Ui.textButton(g, this.font, x, iy, MENU_W, MENU_ITEM_H, "Reopen", hover, 0);
            iy += MENU_ITEM_H + 2;
        }
        boolean armed = e.file().equals(deleteArmedFile);
        boolean hover = Ui.in(mouseX, mouseY, x, iy, MENU_W, MENU_ITEM_H);
        Ui.textButton(g, this.font, x, iy, MENU_W, MENU_ITEM_H, armed ? "Sure?" : "Delete", hover,
                armed ? 0xFFE05050 : 0);
    }

    private void toggleMenu(String file) {
        deleteArmedFile = null;
        menuFile = file.equals(menuFile) ? null : file;
    }

    private void reopen(Entry e) {
        new Progress(e.name()).setComplete(false);
        FarmListClient.message("[SYSTEM] Collection reopened: " + e.name());
        menuFile = null;
        deleteArmedFile = null;
        loadEntries();
        clampScroll();
    }

    private void deleteFromMenu(Entry e) {
        if (!e.file().equals(deleteArmedFile)) {
            deleteArmedFile = e.file();   // first click: button turns into "Sure?"
            return;
        }
        FarmListClient.deleteChecklist(e.file());
        menuFile = null;
        deleteArmedFile = null;
        loadEntries();
        clampScroll();
    }

    // ---------- scrolling and clicking ----------

    private int maxScroll() {
        return Math.max(0, entries.size() * ROW_H - listH);
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
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
        if (event.button() == 0 && menuFile != null) {
            int i = menuRowIndex();
            if (i >= 0) {
                Entry e = entries.get(i);
                boolean complete = new Progress(e.name()).isComplete();
                int x = menuX();
                int y = menuY(i, (complete ? 2 : 1) * (MENU_ITEM_H + 2) + 2);
                if (complete && Ui.in(mx, my, x, y, MENU_W, MENU_ITEM_H)) {
                    reopen(e);
                    return true;
                }
                int deleteY = complete ? y + MENU_ITEM_H + 2 : y;
                if (Ui.in(mx, my, x, deleteY, MENU_W, MENU_ITEM_H)) {
                    deleteFromMenu(e);
                    return true;
                }
            }
            menuFile = null;          // a click anywhere else closes the menu
            deleteArmedFile = null;
        }
        if (event.button() == 0 && bar.click(mx, my)) return true;
        if (event.button() == 0 && Ui.in(mx, my, listX + listW + 2, listY, Ui.SB_W, listH)) {
            dragging = true;
            scroll = Ui.scrollFromY(my, listY, listH, entries.size() * ROW_H, listH);
            return true;
        }
        if (event.button() == 0 && Ui.in(mx, my, listX, listY, listW, listH)) {
            int index = (my - listY + scroll) / ROW_H;
            if (index >= 0 && index < entries.size()
                    && Ui.in(mx, my, dotsX() - 2, rowY(index) + 4, 12, 12)) {
                toggleMenu(entries.get(index).file());
                return true;
            }
            if (index >= 0 && index < entries.size()
                    && (my - listY + scroll) % ROW_H < ROW_H - 2) {
                Entry opened = entries.get(index);
                if (new Progress(opened.name()).isComplete()) {
                    FarmListClient.message("[SYSTEM] Collection complete. Reopen it from the three-dot menu to open it.");
                    return true;
                }
                this.minecraft.gui.setScreen(new ChecklistScreen(opened.file()));
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            scroll = Ui.scrollFromY((int) event.y(), listY, listH, entries.size() * ROW_H, listH);
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

    // ---------- drawing ----------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Ui.panel(g, px, py, pw, ph);
        Ui.flash(g, px, py, pw, ph);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);   // the two buttons

        Ui.textCenter(g, this.font, "[SYSTEM] Your Checklists", px + pw / 2, py + 7, Ui.TEXT_DARK, false);

        if (entries.isEmpty()) {
            Ui.textCenter(g, this.font, "No checklists yet.", px + pw / 2, listY + 12, Ui.TEXT_DARK, false);
            Ui.textCenter(g, this.font, "Make a new one or import from the clipboard.", px + pw / 2, listY + 24, Ui.TEXT_MUTED, false);
        }

        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < entries.size(); i++) {
            int y = listY + i * ROW_H - scroll;
            if (y + ROW_H < listY || y > listY + listH) continue;
            drawEntry(g, entries.get(i), listX, y, listW, mouseX, mouseY);
        }
        g.disableScissor();
        Ui.scrollbar(g, listX + listW + 2, listY, listH, scroll, entries.size() * ROW_H, listH);

        bar.draw(g, this.font, mouseX, mouseY, this.width, this.height);
        drawMenu(g, mouseX, mouseY);

        // status line and color legend
        if (!FarmListClient.status.isEmpty()) {
            Ui.text(g, this.font, Ui.fit(this.font, FarmListClient.status, pw - 16),
                    px + 8, py + ph - 47, Ui.TEXT_DARK, false);
        }
        int lx = px + 8;
        int ly = py + ph - 15;
        lx = legend(g, lx, ly, COLOR_NOT_STARTED, "Not started");
        lx = legend(g, lx + 8, ly, COLOR_IN_PROGRESS, "In progress");
        legend(g, lx + 8, ly, COLOR_COMPLETE, "Complete");
    }

    /** Draws one legend item and returns the x where it ended. */
    private int legend(GuiGraphicsExtractor g, int x, int y, int color, String label) {
        g.fill(x, y, x + 8, y + 8, 0xFF000000);
        g.fill(x + 1, y + 1, x + 7, y + 7, color);
        Ui.text(g, this.font, label, x + 11, y, Ui.TEXT_DARK, false);
        return x + 11 + this.font.width(label);
    }

    private void drawEntry(GuiGraphicsExtractor g, Entry e, int x, int y, int w, int mouseX, int mouseY) {
        boolean hover = Ui.in(mouseX, mouseY, listX, listY, listW, listH)
                && Ui.in(mouseX, mouseY, x, y, w, ROW_H - 2);
        Ui.inset(g, x, y, w, ROW_H - 2, hover ? 0xFF1B4560 : 0xFF10283B);
        Ui.statusSquare(g, x + 6, y + 6, e.color());

        String countText = e.checked() + "/" + e.total();
        int countX = x + w - 18 - this.font.width(countText);
        Ui.text(g, this.font, countText, countX, y + 8, Ui.WHITE, true);

        // three pixel dots, the row's menu button
        boolean dotsHover = Ui.in(mouseX, mouseY, dotsX() - 2, y + 4, 12, 12);
        int dotColor = dotsHover ? 0xFF55FFFF : 0xFFFFFFFF;
        int dx = x + w - 12;
        for (int k = 0; k < 3; k++) {
            g.fill(dx, y + 4 + k * 3, dx + 2, y + 6 + k * 3, dotColor);
        }

        int iconsX = countX - 8 - e.icons().size() * 18;
        for (int i = 0; i < e.icons().size(); i++) {
            Ui.icon(g, e.icons().get(i), iconsX + i * 18, y + 4);
        }

        int nameX = x + 24;
        int nameMax = iconsX - nameX - 6;
        Ui.text(g, this.font, Ui.fit(this.font, e.name(), nameMax), nameX, y + 8, Ui.WHITE, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
