package com.ksp.mythosequipment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

final class EquipmentLifecycle {
    private static EquipmentServer active;
    static void start(ServerStartedEvent e){
        if(active!=null)MinecraftForge.EVENT_BUS.unregister(active);
        active=new EquipmentServer();
        MinecraftForge.EVENT_BUS.register(active);
    }
    static void stop(ServerStoppingEvent e){
        if(active!=null){active.cleanup(e.getServer());MinecraftForge.EVENT_BUS.unregister(active);active=null;}
    }
}
