package com.vortex;

import com.mojang.logging.LogUtils;
import com.vortex.commands.ChestSearchCommand;
import com.vortex.commands.RegearCommand;
import com.vortex.modules.Aura;
import com.vortex.modules.AutoRegear;
import com.vortex.modules.CAura;
import com.vortex.modules.ChestIndex;
import com.vortex.modules.ChestSearchModule;
import com.vortex.modules.ElytraJet;
import com.vortex.modules.NoGlitchBlocks;
import com.vortex.modules.PingSpoof;
import com.vortex.modules.ShulkerRegear;
import com.vortex.modules.SkinBlink;
import com.vortex.modules.Spear;
import com.vortex.printer.modules.CarpetPrinter;
import com.vortex.printer.modules.MapNamer;
import com.vortex.printer.modules.StaircasedPrinter;
import com.vortex.printer.utils.MapAreaCache;
import com.vortex.printer.utils.SlaveSystem;
import com.vortex.printer.utils.Utils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class Vortex extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Vortex");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Vortex");

        // Subscribe printer utility classes to events
        MeteorClient.EVENT_BUS.subscribe(Utils.class);
        MeteorClient.EVENT_BUS.subscribe(MapAreaCache.class);
        MeteorClient.EVENT_BUS.subscribe(SlaveSystem.class);

        Modules.get().add(new Aura());
        Modules.get().add(new CAura());
        Modules.get().add(new AutoRegear());
        Modules.get().add(new ElytraJet());
        Modules.get().add(new ShulkerRegear());
        Modules.get().add(new ChestSearchModule());
        Modules.get().add(new ChestIndex());
        Modules.get().add(new NoGlitchBlocks());
        Modules.get().add(new PingSpoof());
        Modules.get().add(new SkinBlink());
        Modules.get().add(new Spear());

        // Printer modules
        Modules.get().add(new CarpetPrinter());
        Modules.get().add(new StaircasedPrinter());
        Modules.get().add(new MapNamer());

        Commands.add(new RegearCommand());
        Commands.add(new ChestSearchCommand());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.vortex";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("vortex", "vortex-addon", "main", null);
    }
}