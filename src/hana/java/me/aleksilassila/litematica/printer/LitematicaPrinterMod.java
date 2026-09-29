package me.aleksilassila.litematica.printer;

import fi.dy.masa.malilib.event.InitializationHandler;
import me.aleksilassila.litematica.printer.runtime.PrinterRuntime;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class LitematicaPrinterMod implements ModInitializer, ClientModInitializer {
    @Override
    public void onInitialize() {
    }

    @Override
    public void onInitializeClient() {
        PrinterRuntime runtime = new PrinterRuntime();
        RuntimeAccess.install(runtime);
        ClientTickEvents.END_CLIENT_TICK.register(runtime::tick);
        InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
    }
}
