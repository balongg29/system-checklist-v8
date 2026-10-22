package com.farmlist;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Asks for a name, then creates an empty checklist with that name. */
public class NameScreen extends Screen {

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 96;

    private EditBox nameBox;
    private int px, py;

    public NameScreen() {
        super(Component.literal("New Checklist"));
        Ui.startFlash();
    }

    @Override
    protected void init() {
        px = (this.width - PANEL_W) / 2;
        py = (this.height - PANEL_H) / 2;

        nameBox = new EditBox(this.font, px + 10, py + 26, PANEL_W - 20, 20, Component.literal("Name"));
        nameBox.setMaxLength(60);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        int bw = (PANEL_W - 20 - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal("Create"), b -> {
            String name = nameBox.getValue().trim();
            if (name.isEmpty()) {
                FarmListClient.message("Type a name first.");
                return;
            }
            String file = FarmListClient.saveNamedChecklist(name, "# " + name + "\n");
            if (file != null) this.minecraft.gui.setScreen(new ChecklistScreen(file));
        }).bounds(px + 10, py + 54, bw, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), b ->
                this.minecraft.gui.setScreen(new PickerScreen())
        ).bounds(px + 10 + bw + 4, py + 54, bw, 20).build());
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
        Ui.textCenter(g, this.font, "Name your checklist", this.width / 2, py + 10, Ui.TEXT_DARK, false);
        if (!FarmListClient.status.isEmpty()) {
            Ui.text(g, this.font, Ui.fit(this.font, FarmListClient.status, PANEL_W - 20),
                    px + 10, py + PANEL_H - 16, Ui.TEXT_DARK, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
