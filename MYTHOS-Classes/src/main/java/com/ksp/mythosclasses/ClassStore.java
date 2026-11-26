package com.ksp.mythosclasses;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import com.ksp.mythosclasses.ClassRules.*;

final class ClassStore {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    static final class Profile {
        String role;
        long xp;
        int power,vitality,agility,spirit;
        Role selected(){return Role.parse(role);}
        int spent(){return power+vitality+agility+spirit;}
        int stat(Stat s){return switch(s){case POWER->power;case VITALITY->vitality;case AGILITY->agility;case SPIRIT->spirit;};}
        void allocate(Stat s,int n){switch(s){case POWER->power+=n;case VITALITY->vitality+=n;case AGILITY->agility+=n;case SPIRIT->spirit+=n;}}
    }
    private static final class Data {Map<String,Profile> players=new HashMap<>();}
    private Data data=new Data();
    private Path file;
    boolean ready;
    void load(Path path)throws Exception{
        ready=false;file=path;
        data=Files.exists(path)?JSON.fromJson(Files.readString(path),Data.class):new Data();
        if(data==null||data.players==null)throw new IllegalStateException("Invalid class store");
        for(var entry:data.players.entrySet()){
            UUID.fromString(entry.getKey());Profile p=entry.getValue();
            if(p==null||p.selected()==null||p.xp<0||p.xp>MAX_XP)throw new IllegalStateException("Invalid class profile");
            for(Stat s:Stat.values())if(p.stat(s)<0||p.stat(s)>MAX_STAT)throw new IllegalStateException("Invalid stat");
            if(p.spent()>points(p.xp))throw new IllegalStateException("Invalid stat budget");
        }
        ready=true;
    }
    Profile get(UUID id){return data.players.get(id.toString());}
    void put(UUID id,Profile p){data.players.put(id.toString(),p);}
    void remove(UUID id){data.players.remove(id.toString());}
    void save()throws Exception{
        if(!ready)throw new IllegalStateException("Class storage unavailable");
        Path temp=file.resolveSibling(file.getFileName()+".tmp");
        try{
            Files.writeString(temp,JSON.toJson(data));
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception e){ready=false;throw e;}
    }
}
