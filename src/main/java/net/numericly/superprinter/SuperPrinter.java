package net.numericly.superprinter;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.numericly.superprinter.modules.CarpetMap;
import net.numericly.superprinter.modules.ModulePrinter;
import net.numericly.superprinter.utils.InventoryManager;
import org.slf4j.Logger;

public class SuperPrinter extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Super Printer");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Super Printer");

        InventoryManager.init();

        // Modules
        Modules.get().add(new ModulePrinter());
        Modules.get().add(new CarpetMap());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "net.numericly.superprinter";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("numericly", "meteor-litematic-superprinter");
    }
}