package com.example.addon;

import com.example.addon.modules.AntiPhase;
import com.example.addon.modules.AutoTotemPlus;
import com.example.addon.modules.AutoWebPlus;
import com.example.addon.modules.BedrockNuker;
import com.example.addon.modules.DeathSounds;
import com.example.addon.modules.FastWeb;
import com.example.addon.modules.LiquidFiller;
import com.example.addon.modules.LogStripper;
import com.example.addon.modules.MassInstaMine;
import com.example.addon.modules.Speed;
import com.example.addon.modules.SurroundPlus;
import com.example.addon.modules.BetterTabPlus;



import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class QuinnAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("QuinnAddon");

    @Override
    public void onInitialize() {
        Modules modules = Modules.get();

        modules.add(new AntiPhase());
        modules.add(new AutoTotemPlus());
        modules.add(new AutoWebPlus());
        modules.add(new BedrockNuker());
        modules.add(new DeathSounds());
        modules.add(new FastWeb());
        modules.add(new LiquidFiller());
        modules.add(new LogStripper());
        modules.add(new MassInstaMine());
        modules.add(new Speed());
        modules.add(new SurroundPlus());
        modules.add(new BetterTabPlus());


    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("quinn", "QuinnAddon");
    }
}