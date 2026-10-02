package me.aleksilassila.litematica.printer.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.litematica.materials.MaterialListBase;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

public class GuiShulkerPackingPlan extends GuiBase {
    private static final int ROWS_PER_PAGE = 14;
    private static final int COLOR_HEADER = 0xFF80FF80;
    private static final int COLOR_ROW = 0xFFFFFFFF;

    private final MaterialListBase materialList;
    private final Screen parent;
    private final List<ShulkerPackingPlanner.Load> loads;
    private int page;

    public GuiShulkerPackingPlan(MaterialListBase materialList, Screen parent) {
        this.materialList = materialList;
        this.parent = parent;
        this.loads = ShulkerPackingPlanner.plan(materialList);
        setTitle("Shulker packing plan");
    }

    @Override
    public void initGui() {
        super.initGui();

        int pageCount = Math.max(1, (this.loads.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        if (this.page >= pageCount) this.page = pageCount - 1;
        final int pages = pageCount;

        int multiplier = Math.max(1, this.materialList.getMultiplier());
        String header = this.loads.isEmpty()
                ? "Nothing is missing - no shulkers needed."
                : this.loads.size() + " shulker" + (this.loads.size() == 1 ? "" : "s")
                        + " needed (missing x" + multiplier + ")";
        addLabel(12, 26, getScreenWidth() - 24, 12, COLOR_HEADER, header);

        int y = 44;
        int start = this.page * ROWS_PER_PAGE;
        int end = Math.min(start + ROWS_PER_PAGE, this.loads.size());
        for (int i = start; i < end; i++) {
            addLabel(12, y, getScreenWidth() - 24, 12, COLOR_ROW, this.loads.get(i).describe(i + 1));
            y += 11;
        }

        int buttonsY = getScreenHeight() - 30;

        ButtonGeneric prev = new ButtonGeneric(12, buttonsY, -1, 20, "< Prev");
        addButton(prev, (button, mouseButton) -> {
            if (this.page > 0) {
                this.page--;
                initGui();
            }
        });

        ButtonGeneric next = new ButtonGeneric(12 + prev.getWidth() + 2, buttonsY, -1, 20, "Next >");
        addButton(next, (button, mouseButton) -> {
            if (this.page < pages - 1) {
                this.page++;
                initGui();
            }
        });

        String pageText = "Page " + (this.page + 1) + "/" + pages;
        int pageTextWidth = getStringWidth(pageText);
        addLabel(getScreenWidth() / 2 - pageTextWidth / 2, buttonsY + 6, pageTextWidth + 4, 12,
                COLOR_ROW, pageText);

        int backWidth = getStringWidth("Back") + 10;
        ButtonGeneric back = new ButtonGeneric(getScreenWidth() - backWidth - 10, buttonsY, backWidth, 20, "Back");
        addButton(back, (button, mouseButton) -> GuiBase.openGui(this.parent));
    }
}
