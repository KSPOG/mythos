package com.ksp.mythosclasses;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Call on the logical server thread. Equipment definitions remain in their owning mod. */
public final class ClassApi {
    private static ClassStore store;
    private ClassApi(){}
    static void attach(ClassStore value){store=value;}
    static void detach(ClassStore value){if(store==value)store=null;}
    public static boolean available(){return store!=null&&store.ready;}
    public static Optional<String> classId(UUID player){
        if(!available()||player==null)return Optional.empty();
        var role=store.get(player);
        return role==null?Optional.empty():Optional.of(role.id());
    }
    public record StatValue(int base,int allocated,int total){}
    public record Stats(String classId,int earnedPoints,int availablePoints,java.util.Map<String,StatValue> values){}
    public static Optional<Stats> stats(UUID player){
        if(!available()||player==null)return Optional.empty();
        var role=store.get(player);var points=store.points(player);
        if(role==null||points==null)return Optional.empty();
        var values=new java.util.LinkedHashMap<String,StatValue>();
        for(var stat:ClassRules.Stat.values()){
            int base=role.base(stat),allocated=points.bonus(stat);
            values.put(stat.id(),new StatValue(base,allocated,base+allocated));
        }
        return Optional.of(new Stats(role.id(),points.totalEarned(),points.available(),java.util.Collections.unmodifiableMap(values)));
    }
    /** Trusted server reward integrations only; does not expose a client packet. */
    public static boolean grantPoints(UUID player,int amount){
        if(!available()||player==null)return false;
        try{store.grant(player,amount);return true;}catch(Exception ex){return false;}
    }
    public record Progression(int level,int maxLevel,int xp,int nextLevelXp,int xpIntoLevel,int xpRequired,int levelPoints){}
    public record XpReward(int awarded,int xp,int oldLevel,int newLevel,int statPoints){}
    public static Optional<Progression> progression(UUID player){
        if(!available()||player==null)return Optional.empty();
        var p=store.points(player);if(p==null)return Optional.empty();
        int level=ClassRules.level(p.xp),start=ClassRules.threshold(level),next=ClassRules.threshold(level+1);
        return Optional.of(new Progression(level,ClassRules.MAX_LEVEL,p.xp,next,p.xp-start,next-start,ClassRules.levelPoints(p.xp)));
    }
    /** Trusted server-thread reward hook. Returns actual credited XP and levels gained. */
    public static Optional<XpReward> grantXp(UUID player,int amount){
        if(!available()||player==null)return Optional.empty();
        try{var r=store.grantXp(player,amount);return Optional.of(new XpReward(r.awarded(),r.xp(),r.oldLevel(),r.newLevel(),r.points()));}
        catch(Exception ex){return Optional.empty();}
    }
    public static boolean validClass(String id){return ClassRules.Role.parse(id)!=null;}
    /** Empty allowed set means unrestricted; null policy fails closed. */
    public static boolean canUse(UUID player,Collection<String> allowedClasses){
        if(allowedClasses==null)return false;
        if(allowedClasses.isEmpty())return true;
        for(String id:allowedClasses)if(!validClass(id))return false;
        return classId(player).map(id->allowedClasses.stream().anyMatch(value->id.equalsIgnoreCase(value))).orElse(false);
    }
}
