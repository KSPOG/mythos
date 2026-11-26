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
    public static boolean validClass(String id){return ClassRules.Role.parse(id)!=null;}
    /** Empty allowed set means unrestricted; null policy fails closed. */
    public static boolean canUse(UUID player,Collection<String> allowedClasses){
        if(allowedClasses==null)return false;
        if(allowedClasses.isEmpty())return true;
        for(String id:allowedClasses)if(!validClass(id))return false;
        return classId(player).map(id->allowedClasses.stream().anyMatch(value->id.equalsIgnoreCase(value))).orElse(false);
    }
}
