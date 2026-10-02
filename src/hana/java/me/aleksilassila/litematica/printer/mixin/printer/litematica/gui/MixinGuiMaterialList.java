package me.aleksilassila.litematica.printer.mixin.printer.litematica.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListAreaAnalyzer;
import fi.dy.masa.litematica.materials.MaterialListBase;
import me.aleksilassila.litematica.printer.gui.GuiShulkerPackingPlan;
import me.aleksilassila.litematica.printer.materials.SmartMaterialList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiMaterialList.class, remap = false)
public abstract class MixinGuiMaterialList extends GuiBase {

    @Unique
    private static final int VORTEX_PLACED_COLOR = 0xFF55FF55;

    @Unique
    private static String vortex$pendingStatus = "";

    @Inject(method = "initGui", at = @At("RETURN"), remap = false)
    private void vortex$afterInitGui(CallbackInfo ci) {
        final GuiMaterialList self = (GuiMaterialList) (Object) this;
        final MaterialListBase list = self.getMaterialList();

        if (SmartMaterialList.isRebuilding()) {
            vortex$addExtras(list, vortex$pendingStatus);
            vortex$pendingStatus = "";
            return;
        }

        final SmartMaterialList.Result result = SmartMaterialList.adjust(list, self);

        if (result == null) {
            vortex$addExtras(list, "");
            return;
        }

        final String status = SmartMaterialList.describe(result);
        vortex$pendingStatus = status;
        SmartMaterialList.setRebuilding(true);
        try {
            initGui();
        } finally {
            SmartMaterialList.setRebuilding(false);
            vortex$pendingStatus = "";
        }
        if (!status.isEmpty()) {
            addMessage(Message.MessageType.INFO, 3000, status);
        }
    }

    @Unique
    private void vortex$addExtras(MaterialListBase list, String status) {
        final ButtonGeneric packButton = new ButtonGeneric(12, 4, -1, 20, "Pack shulkers");
        packButton.setHoverStrings(status == null || status.isEmpty()
                ? "Plan shulker packing from this list's missing materials"
                : status);
        addButton(packButton, (button, mouseButton) ->
                GuiBase.openGui(new GuiShulkerPackingPlan(list, this)));

        vortex$addPlacedLabel(list);
    }

    @Unique
    private void vortex$addPlacedLabel(MaterialListBase list) {
        if (list instanceof MaterialListAreaAnalyzer) return;

        final long total = list.getCountTotal();
        if (total <= 0) return;

        final long placed = total - list.getCountMissing();
        final String text = "Placed: " + placed + " / " + total;
        final int textWidth = getStringWidth(text);
        final String menuLabel = StringUtils.translate(
                GuiMainMenu.ButtonListenerChangeMenu.ButtonType.MAIN_MENU.getLabelKey());
        final int menuWidth = getStringWidth(menuLabel) + 20;
        final int x = getScreenWidth() - menuWidth - 10 - textWidth - 10;
        if (x < 12) return;

        addLabel(x, getScreenHeight() - 36, textWidth + 4, 12, VORTEX_PLACED_COLOR, text);
    }
}
