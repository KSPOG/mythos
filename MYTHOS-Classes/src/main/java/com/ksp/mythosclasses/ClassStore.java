package com.ksp.mythosclasses;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import com.ksp.mythosclasses.ClassRules.*;

final class ClassStore {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    static final class Points {
        int earned=ClassRules.START_POINTS;
        Map<String,Integer> allocated=new HashMap<>();
        int spent(){return allocated.values().stream().mapToInt(Integer::intValue).sum();}
        int available(){return earned-spent();}
        int bonus(Stat stat){return allocated.getOrDefault(stat.id(),0);}
        Points copy(){Points p=new Points();p.earned=earned;p.allocated.putAll(allocated);return p;}
    }
    private static final class Data {
        Map<String,String> players=new HashMap<>();
        Map<String,Points> points=new HashMap<>();
    }
    private Data data=new Data();
    private Path file;
    boolean ready;
    void load(Path path)throws Exception{
        ready=false;file=path;
        data=Files.exists(path)?JSON.fromJson(Files.readString(path),Data.class):new Data();
        if(data==null||data.players==null)throw new IllegalStateException("Invalid class store");
        if(data.points==null)data.points=new HashMap<>();
        for(var entry:data.players.entrySet()){
            UUID.fromString(entry.getKey());
            Role role=Role.parse(entry.getValue());
            if(role==null)throw new IllegalStateException("Invalid class");
            Points p=data.points.computeIfAbsent(entry.getKey(),k->new Points());
            validate(role,p);
        }
        for(String key:data.points.keySet())if(!data.players.containsKey(key))throw new IllegalStateException("Orphan stat profile");
        ready=true;
    }
    private static void validate(Role role,Points p){
        if(p==null||p.allocated==null||p.earned<ClassRules.START_POINTS||p.earned>ClassRules.MAX_POINTS)throw new IllegalStateException("Invalid point budget");
        for(var e:p.allocated.entrySet()){
            Stat stat=Stat.parse(e.getKey());
            if(stat==null||!stat.id().equals(e.getKey())||e.getValue()==null||e.getValue()<0||e.getValue()>ClassRules.MAX_STAT-role.base(stat))throw new IllegalStateException("Invalid stat allocation");
        }
        if(p.spent()>p.earned)throw new IllegalStateException("Overspent points");
    }
    Role get(UUID id){return ready?Role.parse(data.players.get(id.toString())):null;}
    Points points(UUID id){return get(id)==null?null:data.points.get(id.toString());}
    private void requireReady(){if(!ready)throw new IllegalStateException("Class storage unavailable");}
    void assign(UUID id,Role role)throws Exception{
        requireReady();String key=id.toString(),old=data.players.get(key);Points before=data.points.get(key);
        if(role==null){data.players.remove(key);data.points.remove(key);}
        else{
            data.players.put(key,role.id());
            Points next=before==null?new Points():before.copy();
            if(old!=null&&!role.id().equals(old))next.allocated.clear();
            data.points.put(key,next);
        }
        try{save();}catch(Exception e){
            if(old==null)data.players.remove(key);else data.players.put(key,old);
            restore(key,before);throw e;
        }
    }
    private void restore(String key,Points old){if(old==null)data.points.remove(key);else data.points.put(key,old);}
    private void update(UUID id,Points next)throws Exception{
        requireReady();Role role=get(id);if(role==null)throw new IllegalArgumentException("Select a class first.");
        validate(role,next);String key=id.toString();Points old=data.points.put(key,next);
        try{save();}catch(Exception e){restore(key,old);throw e;}
    }
    void allocate(UUID id,Stat stat,int amount)throws Exception{
        Role role=get(id);Points p=points(id);
        if(role==null||p==null||stat==null||amount<=0)throw new IllegalArgumentException("Invalid allocation or no class.");
        if(amount>p.available())throw new IllegalArgumentException("Not enough unspent points.");
        if(amount>ClassRules.MAX_STAT-role.base(stat)-p.bonus(stat))throw new IllegalArgumentException("Stat cap is "+ClassRules.MAX_STAT+".");
        Points next=p.copy();next.allocated.merge(stat.id(),amount,Integer::sum);update(id,next);
    }
    void grant(UUID id,int amount)throws Exception{
        Points p=points(id);if(p==null||amount<=0||amount>ClassRules.MAX_POINTS-p.earned)throw new IllegalArgumentException("Invalid point grant or no class.");
        Points next=p.copy();next.earned+=amount;update(id,next);
    }
    void reset(UUID id)throws Exception{
        Points p=points(id);if(p==null)throw new IllegalArgumentException("No class selected.");
        Points next=p.copy();next.allocated.clear();update(id,next);
    }
    private void save()throws Exception{
        Path temp=file.resolveSibling(file.getFileName()+".tmp");
        try{Files.writeString(temp,JSON.toJson(data));Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(Exception e){ready=false;throw e;}
    }
}
