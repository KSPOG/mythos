package com.ksp.mythoscharacters;

import java.nio.file.*;
import java.util.Properties;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;

/** Local preferences, deliberately independent of server gameplay and identity. */
final class CharacterOptions {
    enum Quality { CINEMATIC, BALANCED, PERFORMANCE }
    enum Physique { STANDARD, SLENDER, ATHLETIC }
    static Quality quality=Quality.CINEMATIC;
    static Physique physique=Physique.STANDARD;
    static boolean hair=false, motion=true, enabled=true;
    private static Path path(){return FMLPaths.CONFIGDIR.get().resolve("mythos-characters.properties");}
    static void load(){
        Properties p=new Properties();
        try{
            if(!Files.exists(path()))return;
            try(var in=Files.newInputStream(path())){p.load(in);}
            quality=Quality.valueOf(p.getProperty("quality","CINEMATIC"));
            physique=Physique.valueOf(p.getProperty("physique","STANDARD"));
            hair=Boolean.parseBoolean(p.getProperty("hair","false"));
            motion=Boolean.parseBoolean(p.getProperty("motion","true"));
            enabled=Boolean.parseBoolean(p.getProperty("enabled","true"));
        }catch(Exception ex){LogUtils.getLogger().warn("MYTHOS Characters preferences could not be read",ex);}
    }
    static void save(){
        Properties p=new Properties();
        p.setProperty("quality",quality.name());p.setProperty("physique",physique.name());
        p.setProperty("hair",Boolean.toString(hair));p.setProperty("motion",Boolean.toString(motion));p.setProperty("enabled",Boolean.toString(enabled));
        try{Files.createDirectories(path().getParent());try(var out=Files.newOutputStream(path())){p.store(out,"MYTHOS Characters client preferences");}}
        catch(Exception ex){LogUtils.getLogger().warn("MYTHOS Characters preferences could not be saved",ex);}
    }
    static int lod(double distanceSquared){
        int lod=distanceSquared<=9?0:distanceSquared<=64?1:distanceSquared<=400?2:3;
        return Math.min(3,lod+quality.ordinal());
    }
}
