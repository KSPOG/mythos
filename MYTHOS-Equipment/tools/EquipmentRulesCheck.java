package com.ksp.mythosequipment;
import java.util.*;
import com.ksp.mythosequipment.EquipmentRules.*;
public final class EquipmentRulesCheck {
    public static void main(String[] args){
        if(EquipmentRules.CATALOG.size()!=120)throw new AssertionError("catalog");
        if(EquipmentRules.CATALOG.stream().map(Definition::id).distinct().count()!=120)throw new AssertionError("duplicate id");
        if(Arrays.stream(Rarity.values()).mapToInt(r->r.weight).sum()!=10000)throw new AssertionError("weights");
        var rng=new Random(12345);
        for(var d:EquipmentRules.CATALOG)for(var r:Rarity.values()){
            var variants=new HashSet<Map<Stat,Integer>>();
            for(int i=0;i<100;i++){
                var roll=EquipmentRules.roll(d,r,rng);
                if(!EquipmentRules.valid(d,r,roll))throw new AssertionError("invalid roll");
                variants.add(roll);
            }
            if(variants.size()<2)throw new AssertionError("no variation");
        }
        System.out.println("Catalog and all six rarity roll checks passed");
    }
}
