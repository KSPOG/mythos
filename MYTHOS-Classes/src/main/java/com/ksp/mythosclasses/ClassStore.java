package com.ksp.mythosclasses;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import com.ksp.mythosclasses.ClassRules.Role;

final class ClassStore {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final class Data {Map<String,String> players=new HashMap<>();}
    private Data data=new Data();
    private Path file;
    boolean ready;
    void load(Path path)throws Exception{
        ready=false;file=path;
        data=Files.exists(path)?JSON.fromJson(Files.readString(path),Data.class):new Data();
        if(data==null||data.players==null)throw new IllegalStateException("Invalid class store");
        for(var entry:data.players.entrySet()){
            UUID.fromString(entry.getKey());
            if(Role.parse(entry.getValue())==null)throw new IllegalStateException("Invalid class");
        }
        ready=true;
    }
    Role get(UUID id){return ready?Role.parse(data.players.get(id.toString())):null;}
    void assign(UUID id,Role role)throws Exception{
        if(!ready)throw new IllegalStateException("Class storage unavailable");
        String key=id.toString(),old=data.players.get(key);
        if(role==null)data.players.remove(key);else data.players.put(key,role.id());
        Path temp=file.resolveSibling(file.getFileName()+".tmp");
        try{
            Files.writeString(temp,JSON.toJson(data));
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception e){
            if(old==null)data.players.remove(key);else data.players.put(key,old);
            ready=false;throw e;
        }
    }
}
