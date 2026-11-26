package com.ksp.mythospets;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("mythospets")
public final class Pets {
    public Pets(FMLJavaModLoadingContext context) {
        PetEntities.register(context.getModEventBus());
        // Economy is intentionally not installed on multiplayer clients.
        // The controller is loaded only where its dependency is present.
        if (ModList.get().isLoaded("mythoseconomy")) {
            new PetServer();
        }
    }
}
