package com.vortex;

import com.example.addon.QuinnAddon;
import com.mojang.logging.LogUtils;
import com.quiettee.utils.QuietteeUtils;
import com.vortex.commands.ChestSearchCommand;
import com.vortex.commands.RegearCommand;
import com.vortex.commands.StashFinderCommand;
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
import com.vortex.printer.modules.FullBlockPrinter;
import com.vortex.printer.modules.MapNamer;
import com.vortex.printer.modules.StaircasedPrinter;
import com.vortex.printer.utils.MapAreaCache;
import com.vortex.printer.utils.SlaveSystem;
import com.vortex.printer.utils.Utils;
import com.volytrafly.VolytraFlyAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.numericly.superprinter.SuperPrinter;
import org.slf4j.Logger;

public class Vortex extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Vortex");

    private final QuietteeUtils quietteeUtils = new QuietteeUtils();
    private final SuperPrinter superPrinter = new SuperPrinter();
    private final VolytraFlyAddon volytraFlyAddon = new VolytraFlyAddon();
    private final QuinnAddon quinnAddon = new QuinnAddon();

    @Override
    public void onInitialize() {
        LOG.info("Initializing Vortex");

        // Bundled addons are not fabric entrypoints anymore, so register their
        // orbit lambda factories ourselves (Meteor does this per entrypoint addon)
        for (String pkg : new String[]{"com.quiettee.utils", "net.numericly.superprinter", "com.volytrafly", "com.example.addon"}) {
            MeteorClient.EVENT_BUS.registerLambdaFactory(pkg, (method, clazz) ->
                (java.lang.invoke.MethodHandles.Lookup) method.invoke(null, clazz, java.lang.invoke.MethodHandles.lookup()));
        }

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
        Modules.get().add(new FullBlockPrinter());
        Modules.get().add(new StaircasedPrinter());
        Modules.get().add(new MapNamer());

        Commands.add(new RegearCommand());
        Commands.add(new ChestSearchCommand());
        Commands.add(new StashFinderCommand());

        // Delegated bundled addons (single meteor entrypoint)
        quietteeUtils.onInitialize();
        superPrinter.onInitialize();
        volytraFlyAddon.onInitialize();
        quinnAddon.onInitialize();
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
        quietteeUtils.onRegisterCategories();
        superPrinter.onRegisterCategories();
        volytraFlyAddon.onRegisterCategories();
        quinnAddon.onRegisterCategories();
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