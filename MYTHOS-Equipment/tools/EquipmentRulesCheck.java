package com.ksp.mythosequipment;
import java.util.*;
import com.ksp.mythosequipment.EquipmentRules.*;
public final class EquipmentRulesCheck {
    public static void main(String[] args){
        if(EquipmentRules.CATALOG.size()!=120)throw new AssertionError("catalog");
        if(EquipmentRules.CATALOG.stream().map(Definition::id).distinct().count()!=120)throw new AssertionError("duplicate id");
        if(Arrays.stream(Rarity.values()).mapToInt(r->r.weight).sum()!=10000)throw new AssertionError("weights");
        if(EquipmentRules.CATALOG.stream().map(Definition::name).distinct().count()!=120)throw new AssertionError("unique names");
        if(Arrays.stream(Rarity.values()).filter(Rarity::hasEffect).count()!=4||Rarity.COMMON.effectRank()!=0||Rarity.UNCOMMON.effectRank()!=0||Rarity.MYTHICAL.effectRank()!=4)throw new AssertionError("effect gate");
        if(EquipmentRules.CATALOG.stream().map(Definition::effect).distinct().count()!=24)throw new AssertionError("signature catalog");
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
