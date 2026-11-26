package com.ksp.mythosmerchants;
import com.google.gson.*;
import com.ksp.mythoseconomy.Ledger;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.*;
import java.util.*;

final class Presets {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    record Preset(String type,GoldShops.Shop items,Map<String,Ledger.CreatureOffer> creatures){}
    private final Path dir=FMLPaths.CONFIGDIR.get().resolve("mythos/merchants/presets");
    private Map<String,Preset> entries=new TreeMap<>();
    static boolean id(String id){return id!=null&&id.matches("[a-z0-9_-]{1,32}");}
    static boolean valid(Preset p){
        if(p==null||!id(p.type)||!GoldShops.validShop(p.items)||p.creatures==null||p.creatures.size()>45)return false;
        String kind=p.type.equals("stable")?"mount":p.type.equals("pet")?"pet":"";
        if(kind.isEmpty())return p.creatures.isEmpty();
        return p.items.offers.isEmpty()&&p.creatures.entrySet().stream().allMatch(e->{var o=e.getValue();return e.getKey()!=null&&e.getKey().matches(kind+":[a-z0-9_]{1,40}")&&o!=null&&("gold".equals(o.currency())||(kind+"_token").equals(o.currency()))&&o.price()>0&&o.price()<=Ledger.MAX_BALANCE&&o.stock()>=0&&o.stock()<=o.max()&&o.max()<=1000000;});
    }
    void defaults(Map<String,Preset> defaults)throws Exception{
        Files.createDirectories(dir);
        for(var e:defaults.entrySet())if(!Files.exists(dir.resolve(e.getKey()+".json")))write(e.getKey(),e.getValue());
        reload();
    }
    void reload()throws Exception{
        var next=new TreeMap<String,Preset>();Files.createDirectories(dir);
        try(var stream=Files.list(dir)){
            for(var f:stream.filter(x->x.getFileName().toString().endsWith(".json")).toList()){
                String key=f.getFileName().toString();key=key.substring(0,key.length()-5);
                if(!id(key)||Files.size(f)>1000000)throw new IllegalArgumentException("Invalid preset file "+f);
                var p=JSON.fromJson(Files.readString(f),Preset.class);
                if(!valid(p))throw new IllegalArgumentException("Invalid preset "+key);next.put(key,p);
            }
        }
        entries=next;
    }
    private void write(String id,Preset p)throws Exception{
        if(!id(id)||!valid(p))throw new IllegalArgumentException("Invalid preset");
        Files.createDirectories(dir);var target=dir.resolve(id+".json");var tmp=dir.resolve(id+".tmp");
        Files.writeString(tmp,JSON.toJson(p));Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    void save(String id,Preset p)throws Exception{write(id,p);entries.put(id,p);}
    void delete(String id)throws Exception{if(!id(id)||!entries.containsKey(id))throw new IllegalArgumentException("Unknown preset");Files.delete(dir.resolve(id+".json"));entries.remove(id);}
    Preset get(String id){return entries.get(id);}
    Set<String> ids(){return Collections.unmodifiableSet(entries.keySet());}
}
