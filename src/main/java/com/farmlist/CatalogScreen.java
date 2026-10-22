package com.farmlist;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A creative-inventory style item browser: a grid of item icons you can search and scroll.
 * Tabs: All, Blocks, Items, and Types (fence, wool, concrete, slab, stairs, wall...). Click a type to see
 * its variants (wood types, colors, materials) and pick one.
 * Two modes:
 *  - add mode (editKey == null): click an item, set Need (and optionally Have), press Add.
 *  - edit mode (editKey set): change an existing row to another item, change Need / Have, or remove it.
 * Need and Have have up/down arrows (Shift = 10) and also react to the Up/Down keys.
 */
public class CatalogScreen extends Screen {

    private static final int SLOT = 18;
    private static final int M = 10;           // inner margin of the panel
    private static final int FIXED_H = 140;    // panel height not counting the grid rows
    private static final int TAB_W = 38;
    private static final String[] TAB_NAMES = { "All", "Blocks", "Items", "Types" };

    /** One thing in the grid: an item (key = item id), or a family card on the Types tab (family != null). */
    private record Choice(ItemStack stack, String name, String sub, String key, Families.Family family) {
    }

    private final String fileName;
    private final String editKey;

    private EditBox searchBox;
    private EditBox needBox;
    private EditBox haveBox;
    private Stepper needStep;
    private Stepper haveStep;
    private String query = "";
    private String needText = "1";
    private String haveText = "";
    private String origHave = "";
    private int tab = 0;
    private Families.Family activeFamily = null;

    private List<Choice> view = new ArrayList<>();
    private Choice selected = null;
    private int scrollRow = 0;
    private boolean dragging = false;
    private boolean prepared = false;

    private int px, py, pw, ph;
    private int cols, rowsVisible, gridX, gridY, by;

    public CatalogScreen(String fileName) {
        this(fileName, null);
    }

    /** editKey is the row being edited (an item id, "building_block", or a bare family like "wall"), or null to add. */
    public CatalogScreen(String fileName, String editKey) {
        super(Component.literal(editKey == null ? "Add Item" : "Edit Item"));
        Ui.startFlash();
        this.fileName = fileName;
        this.editKey = editKey;
    }

    private boolean editing() {
        return editKey != null;
    }

    /** First time only: fill in the boxes and starting tab from the row being edited. */
    private void prepareEdit() {
        if (prepared) return;
        prepared = true;
        if (!editing()) return;
        Progress progress = new Progress(FarmListClient.baseName(fileName));
        Integer need = FarmListClient.parseRequired(FarmListClient.TRANSCRIPTION_DIR.resolve(fileName)).get(editKey);
        needText = need == null ? "1" : Integer.toString(need);
        Integer have = progress.getHave(editKey);
        haveText = have == null ? "" : Integer.toString(have);
        origHave = haveText;

        String current = FarmListClient.countedId(editKey, progress);
        Families.Family bare = Families.byKey(editKey);
        FarmListClient.CatalogEntry entry = FarmListClient.entryFor(current);
        if (entry != null) {
            selected = itemChoice(entry);
            Families.Family f = Families.ofItem(entry.id());
            if (f != null) {
                activeFamily = f;
                tab = 3;
            }
        } else if (bare != null) {
            activeFamily = bare;
            tab = 3;
        }
    }

    private static Choice itemChoice(FarmListClient.CatalogEntry e) {
        return new Choice(e.stack(), e.name(), e.id(), e.id(), null);
    }

    @Override
    protected void init() {
        prepareEdit();
        cols = this.width >= 300 ? 11 : 10;
        rowsVisible = Math.max(2, Math.min(7, (this.height - 16 - FIXED_H) / SLOT));
        pw = 2 * M + cols * SLOT + 3 + Ui.SB_W;
        ph = FIXED_H + rowsVisible * SLOT;
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        gridX = px + M;
        gridY = py + 46;
        by = gridY + rowsVisible * SLOT + 6;   // top of the bottom section

        // search box
        searchBox = new EditBox(this.font, px + M, py + 24, pw - 2 * M, 18, Component.literal("Search"));
        searchBox.setValue(query);
        searchBox.setResponder(text -> {
            query = text;
            scrollRow = 0;
            refresh();
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        // page buttons (top right, next to the tabs)
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            scrollRow -= rowsVisible;
            clampScroll();
        }).bounds(px + pw - M - 34, py + 6, 16, 14).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            scrollRow += rowsVisible;
            clampScroll();
        }).bounds(px + pw - M - 16, py + 6, 16, 14).build());

        // Need and Have boxes, each with up/down arrows
        int rowY = by + 24;
        needBox = new EditBox(this.font, px + M + 32, rowY, 38, 20, Component.literal("Need"));
        needBox.setValue(needText);
        needBox.setMaxLength(5);
        needBox.setResponder(text -> needText = text);
        addRenderableWidget(needBox);
        needStep = new Stepper(needBox, 1);

        int haveX = px + M + 32 + 38 + Stepper.ARROW_W + 36;
        haveBox = new EditBox(this.font, haveX, rowY, 38, 20, Component.literal("Have"));
        haveBox.setValue(haveText);
        haveBox.setMaxLength(5);
        haveBox.setResponder(text -> haveText = text);
        addRenderableWidget(haveBox);
        haveStep = new Stepper(haveBox, 0);

        // buttons
        int btnY = by + 48;
        int n = editing() ? 3 : 2;
        int bw = (pw - 2 * M - 4 * (n - 1)) / n;
        int bx = px + M;
        addRenderableWidget(Button.builder(Component.literal(editing() ? "Save" : "Add"),
                b -> { if (editing()) saveEdit(); else addSelected(); }).bounds(bx, btnY, bw, 20).build());
        if (editing()) {
            bx += bw + 4;
            addRenderableWidget(Button.builder(Component.literal("Remove"), b -> removeRow())
                    .bounds(bx, btnY, bw, 20).build());
        }
        bx += bw + 4;
        addRenderableWidget(Button.builder(Component.literal("Back"), b ->
                this.minecraft.gui.setScreen(new ChecklistScreen(fileName))
        ).bounds(bx, btnY, bw, 20).build());

        refresh();
    }

    // ---------- filtering and scrolling ----------

    private void refresh() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        String qId = q.replace(' ', '_');
        view = new ArrayList<>();
        if (activeFamily != null) {
            for (FarmListClient.CatalogEntry e : Families.variants(activeFamily)) {
                if (matches(e, q, qId)) view.add(itemChoice(e));
            }
        } else if (tab == 3) {
            for (Families.Family f : Families.ALL) {
                ItemStack icon = Families.iconFor(f);
                if (icon == null) continue;
                if (!q.isEmpty() && !f.label().toLowerCase(Locale.ROOT).contains(q) && !f.key().contains(qId)) continue;
                view.add(new Choice(icon, f.label(), "Click to choose a type", null, f));
            }
        } else {
            for (FarmListClient.CatalogEntry e : FarmListClient.catalog()) {
                if (tab == 1 && !e.block()) continue;
                if (tab == 2 && e.block()) continue;
                if (matches(e, q, qId)) view.add(itemChoice(e));
            }
        }
        clampScroll();
    }

    private static boolean matches(FarmListClient.CatalogEntry e, String q, String qId) {
        return q.isEmpty() || e.id().contains(qId) || e.nameLower().contains(q);
    }

    private int totalRows() {
        return (view.size() + cols - 1) / cols;
    }

    private void clampScroll() {
        int max = Math.max(0, totalRows() - rowsVisible);
        scrollRow = Math.max(0, Math.min(scrollRow, max));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (Ui.in((int) mouseX, (int) mouseY, px, gridY, pw, rowsVisible * SLOT)) {
            scrollRow -= (int) Math.signum(scrollY);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int barX() {
        return gridX + cols * SLOT + 3;
    }

    /** Index into view of the slot under the mouse, or -1. */
    private int slotAt(int mx, int my) {
        if (!Ui.in(mx, my, gridX, gridY, cols * SLOT, rowsVisible * SLOT)) return -1;
        int c = (mx - gridX) / SLOT;
        int r = (my - gridY) / SLOT;
        int i = (scrollRow + r) * cols + c;
        return i < view.size() ? i : -1;
    }

    // ---------- clicking and keys ----------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int mx = (int) event.x();
        int my = (int) event.y();
        if (event.button() == 0) {
            // scroll bar
            if (Ui.in(mx, my, barX(), gridY, Ui.SB_W, rowsVisible * SLOT)) {
                dragging = true;
                scrollRow = Ui.scrollFromY(my, gridY, rowsVisible * SLOT, totalRows(), rowsVisible);
                clampScroll();
                return true;
            }
            // quantity arrows
            if (needStep.click(mx, my, event.hasShiftDown()) || haveStep.click(mx, my, event.hasShiftDown())) {
                return true;
            }
            // tabs
            for (int t = 0; t < TAB_NAMES.length; t++) {
                if (Ui.in(mx, my, px + M + t * (TAB_W + 2), py + 6, TAB_W, 14)) {
                    tab = t;
                    activeFamily = null;   // clicking Types again goes back to the list of types
                    scrollRow = 0;
                    refresh();
                    return true;
                }
            }
            // item slots
            int i = slotAt(mx, my);
            if (i >= 0) {
                Choice c = view.get(i);
                if (c.family() != null) {
                    activeFamily = c.family();
                    scrollRow = 0;
                    refresh();
                } else {
                    selected = c;
                    FarmListClient.message("Selected " + c.name() + (editing() ? ". Press Save." : ". Set Need and press Add."));
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            scrollRow = Ui.scrollFromY((int) event.y(), gridY, rowsVisible * SLOT, totalRows(), rowsVisible);
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
    public boolean keyPressed(KeyEvent event) {
        if (needStep.key(event.key(), event.hasShiftDown()) || haveStep.key(event.key(), event.hasShiftDown())) {
            return true;
        }
        return super.keyPressed(event);
    }

    // ---------- actions ----------

    /** Need must be 1 or more. Returns -1 (and sets a status message) if it is not a good number. */
    private int parseNeed() {
        int need = needStep.value();
        if (need < 1) {
            FarmListClient.message("Need must be a number, 1 or more.");
            return -1;
        }
        return need;
    }

    /** Have is optional. Returns -2 if left blank, -1 if it is not a number, otherwise the number. */
    private int parseHave() {
        if (haveStep.isBlank()) return -2;
        int have = haveStep.value();
        if (have < 0) {
            FarmListClient.message("Have must be a number, 0 or more.");
            return -1;
        }
        return have;
    }

    private void addSelected() {
        if (selected == null) {
            FarmListClient.message(activeFamily != null || tab == 3
                    ? "Choose a type first, then click the variant you want."
                    : "Pick an item from the grid first.");
            return;
        }
        int need = parseNeed();
        if (need < 0) return;
        int have = parseHave();
        if (have == -1) return;
        FarmListClient.appendRow(fileName, selected.key(), need);   // sets the "Added ..." status
        if (have >= 0) applyHave(new Progress(FarmListClient.baseName(fileName)), selected.key(), have);
        // Option A: go straight back to the checklist so the new item is visible right away
        this.minecraft.gui.setScreen(new ChecklistScreen(fileName));
    }

    /** Stores a hand-typed Have and ticks the row if it already covers what is needed. */
    private void applyHave(Progress progress, String key, int have) {
        progress.setManualHave(key, have);
        Integer need = FarmListClient.parseRequired(FarmListClient.TRANSCRIPTION_DIR.resolve(fileName)).get(key);
        if (need != null && have >= need) progress.set(key, true);
    }

    private void saveEdit() {
        int need = parseNeed();
        if (need < 0) return;
        int have = parseHave();
        if (have == -1) return;
        String newKey = selected != null ? selected.key() : editKey;
        if (FarmListClient.isPlaceholder(newKey)) {
            FarmListClient.message("Pick the actual item first.");
            return;
        }
        Progress progress = new Progress(FarmListClient.baseName(fileName));
        FarmListClient.replaceRow(fileName, editKey, newKey, need);
        progress.rename(editKey, newKey);
        if (have >= 0 && !haveText.trim().equals(origHave)) applyHave(progress, newKey, have);
        FarmListClient.message("Saved.");
        this.minecraft.gui.setScreen(new ChecklistScreen(fileName));
    }

    private void removeRow() {
        FarmListClient.removeRow(fileName, editKey);
        new Progress(FarmListClient.baseName(fileName)).clearRow(editKey);
        this.minecraft.gui.setScreen(new ChecklistScreen(fileName));
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
        super.extractRenderState(g, mouseX, mouseY, partialTick);   // search box, boxes, buttons

        // tabs
        for (int t = 0; t < TAB_NAMES.length; t++) {
            int tx = px + M + t * (TAB_W + 2);
            boolean active = t == tab;
            Ui.inset(g, tx, py + 6, TAB_W, 14, active ? 0xFFC6C6C6 : 0xFF6B6B6B);
            Ui.textCenter(g, this.font, TAB_NAMES[t], tx + TAB_W / 2, py + 9,
                    active ? Ui.TEXT_DARK : 0xFFE0E0E0, !active);
            if (active) {
                g.fill(tx + 2, py + 17, tx + TAB_W - 2, py + 19, Ui.GOLD);
            }
        }

        // the grid
        int hovered = slotAt(mouseX, mouseY);
        for (int r = 0; r < rowsVisible; r++) {
            for (int c = 0; c < cols; c++) {
                int i = (scrollRow + r) * cols + c;
                int sx = gridX + c * SLOT;
                int sy = gridY + r * SLOT;
                Ui.slot(g, sx, sy);
                if (i >= view.size()) continue;
                Choice ch = view.get(i);
                Ui.icon(g, ch.stack(), sx + 1, sy + 1);
                if (selected != null && ch.key() != null && ch.key().equals(selected.key())) {
                    Ui.frame(g, sx, sy, SLOT, SLOT, Ui.GOLD);
                }
                if (i == hovered) {
                    g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0x80FFFFFF);
                }
            }
        }
        if (view.isEmpty()) {
            Ui.textCenter(g, this.font, "No items match.", gridX + cols * SLOT / 2, gridY + rowsVisible * SLOT / 2 - 4,
                    Ui.WHITE, true);
        }
        Ui.scrollbar(g, barX(), gridY, rowsVisible * SLOT, scrollRow, totalRows(), rowsVisible);

        // bottom section: selected item, Need / Have labels and arrows, status
        int textMax = pw - 2 * M - 26;
        Ui.slot(g, px + M, by);
        if (selected != null) {
            Ui.icon(g, selected.stack(), px + M + 1, by + 1);
            Ui.text(g, this.font, Ui.fit(this.font, selected.name(), textMax), px + M + 24, by + 1, Ui.TEXT_DARK, false);
            Ui.text(g, this.font, Ui.fit(this.font, selected.sub(), textMax), px + M + 24, by + 10, Ui.TEXT_MUTED, false);
        } else if (activeFamily != null) {
            Ui.text(g, this.font, Ui.fit(this.font, "Choose a " + activeFamily.label().toLowerCase(Locale.ROOT)
                    + " type above", textMax), px + M + 24, by + 5, Ui.TEXT_MUTED, false);
        } else {
            Ui.text(g, this.font, "Click an item to select it", px + M + 24, by + 5, Ui.TEXT_MUTED, false);
        }

        int labelY = by + 24 + 6;
        Ui.text(g, this.font, "Need", px + M, labelY, Ui.TEXT_DARK, false);
        int haveLabelX = px + M + 32 + 38 + Stepper.ARROW_W + 4;
        Ui.text(g, this.font, "Have", haveLabelX, labelY, Ui.TEXT_DARK, false);
        needStep.draw(g, mouseX, mouseY);
        haveStep.draw(g, mouseX, mouseY);

        int pages = Math.max(1, (totalRows() + rowsVisible - 1) / rowsVisible);
        int page = Math.min(pages, scrollRow / rowsVisible + 1);
        String pageText = page + "/" + pages;
        int statusY = by + 74;
        Ui.textRight(g, this.font, pageText, px + pw - M, statusY, Ui.TEXT_MUTED, false);
        if (!FarmListClient.status.isEmpty()) {
            Ui.text(g, this.font,
                    Ui.fit(this.font, FarmListClient.status, pw - 2 * M - this.font.width(pageText) - 8),
                    px + M, statusY, Ui.TEXT_DARK, false);
        }

        // hover label goes last so it sits on top of everything
        if (hovered >= 0) {
            Choice c = view.get(hovered);
            Ui.label(g, this.font, c.name(), c.sub(), mouseX, mouseY, this.width, this.height);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
