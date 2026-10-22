package com.farmlist;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Share one checklist: choose "list only" or "with progress", then copy a code, or send it to players.
 * Players are picked from the online list (click to tick), or typed in the box.
 */
public class ShareScreen extends Screen {

    private static final int PANEL_W = 200;
    private static final int PANEL_H = 236;
    private static final int MAX_PICK = 8;        // online players shown as buttons (2 columns x 4 rows)
    private static final int ROW_H = 18;

    private final String fileName;
    private boolean withProgress = false;
    private final Set<String> picked = new LinkedHashSet<>();
    private List<String> online = new ArrayList<>();
    private String typed = "";

    private Button modeButton;
    private int px, py;

    public ShareScreen(String fileName) {
        super(Component.literal("Share"));
        Ui.startFlash();
        this.fileName = fileName;
    }

    private Component modeLabel() {
        return Component.literal(withProgress ? "Sharing: list + progress" : "Sharing: list only");
    }

    @Override
    protected void init() {
        px = (this.width - PANEL_W) / 2;
        py = (this.height - PANEL_H) / 2;
        int inner = PANEL_W - 20;
        online = ChatShare.onlineNames();

        modeButton = Button.builder(modeLabel(), b -> {
            withProgress = !withProgress;
            b.setMessage(modeLabel());
        }).bounds(px + 10, py + 22, inner, 20).build();
        addRenderableWidget(modeButton);

        addRenderableWidget(Button.builder(Component.literal("Copy code"), b ->
                Share.copyCode(fileName, false, withProgress)
        ).bounds(px + 10, py + 46, inner, 20).build());

        // Online players: click to tick or untick. Up to MAX_PICK are shown.
        int colW = (inner - 4) / 2;
        int shown = Math.min(MAX_PICK, online.size());
        for (int i = 0; i < shown; i++) {
            String name = online.get(i);
            int col = i % 2;
            int row = i / 2;
            Button btn = Button.builder(pickLabel(name), b -> {
                if (!picked.remove(name)) picked.add(name);
                b.setMessage(pickLabel(name));
            }).bounds(px + 10 + col * (colW + 4), py + 84 + row * ROW_H, colW, 16).build();
            addRenderableWidget(btn);
        }

        EditBox nameBox = new EditBox(this.font, px + 10, py + 170, inner, 20, Component.literal("Player names"));
        nameBox.setMaxLength(120);
        nameBox.setValue(typed);
        nameBox.setResponder(t -> typed = t);
        addRenderableWidget(nameBox);

        int bw = (inner - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("Send"), b ->
                ChatShare.send(fileName, withProgress, names())
        ).bounds(px + 10, py + 196, bw, 20).build());

        addRenderableWidget(Button.builder(Component.literal("All online"), b -> {
            if (online.isEmpty()) {
                FarmListClient.message("No other players are online.");
            } else {
                ChatShare.send(fileName, withProgress, new ArrayList<>(online));
            }
        }).bounds(px + 10 + bw + 4, py + 196, bw, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), b ->
                this.minecraft.gui.setScreen(new ChecklistScreen(fileName))
        ).bounds(px + 10 + 2 * (bw + 4), py + 196, bw, 20).build());
    }

    private Component pickLabel(String name) {
        return Component.literal((picked.contains(name) ? "[x] " : "[ ] ")
                + Ui.fit(this.font, name, (PANEL_W - 20 - 4) / 2 - 26));
    }

    /** Picked players first, then anything typed in the box. Duplicates removed. */
    private List<String> names() {
        Set<String> out = new LinkedHashSet<>(picked);
        for (String n : typed.trim().split("[,\\s]+")) {
            if (!n.isEmpty()) out.add(n);
        }
        return new ArrayList<>(out);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Ui.panel(g, px, py, PANEL_W, PANEL_H);
        Ui.flash(g, px, py, PANEL_W, PANEL_H);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        Ui.textCenter(g, this.font, "[SYSTEM] Share \"" + Ui.fit(this.font, FarmListClient.baseName(fileName), PANEL_W - 110) + "\"",
                px + PANEL_W / 2, py + 8, Ui.TEXT_DARK, false);

        if (online.isEmpty()) {
            Ui.text(g, this.font, "No other players online", px + 10, py + 84, Ui.TEXT_MUTED, false);
        } else {
            String label = online.size() > MAX_PICK
                    ? "Online (first " + MAX_PICK + " of " + online.size() + "):"
                    : "Online (click to pick):";
            Ui.text(g, this.font, label, px + 10, py + 72, Ui.TEXT_MUTED, false);
        }
        Ui.text(g, this.font, "Or type names:", px + 10, py + 158, Ui.TEXT_MUTED, false);

        if (!FarmListClient.status.isEmpty()) {
            Ui.text(g, this.font, Ui.fit(this.font, FarmListClient.status, PANEL_W - 20),
                    px + 10, py + PANEL_H - 12, Ui.TEXT_DARK, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
