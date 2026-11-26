package com.ksp.mythosequipment;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

final class EquipmentLifecycle {
    private static final EquipmentServer SERVER=new EquipmentServer();
    static void register(){
        var bus=MinecraftForge.EVENT_BUS;
        bus.addListener(SERVER::commands);
        bus.addListener(SERVER::attack);
        bus.addListener(SERVER::use);
        bus.addListener(SERVER::block);
        bus.addListener(SERVER::projectile);
        bus.addListener(SERVER::damage);
        bus.addListener(SERVER::tick);
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,SERVER::hurt);
        bus.addListener(EquipmentLifecycle::stop);
    }
    private static void stop(ServerStoppingEvent e){SERVER.cleanup(e.getServer());}
}
