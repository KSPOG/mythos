package com.ksp.mythosequipment;
import java.util.*;
import com.ksp.mythosequipment.EquipmentRules.*;
public class ArmorVisualCheck {
    public static void main(String[] args){
        Set<ArmorDesign.Profile> profiles=new HashSet<>();
        int pieces=0,max=0;
        for(var c:ClassSet.values())for(var t:Tier.values()){
            if(!profiles.add(ArmorDesign.of(c,t)))throw new AssertionError("Duplicate profile");
            int[] total=new int[3];
            for(var d:EquipmentRules.CATALOG)if(d.owner()==c&&d.tier()==t&&d.kind()==null){
                pieces++;
                for(int lod=0;lod<3;lod++){
                    var mesh=ArmorPolygon.build(d,lod);
                    int count=mesh.stream().mapToInt(p->p.faces().size()).sum();
                    if(count<=0)throw new AssertionError(d.id());
                    for(var part:mesh)for(var f:part.faces())if(!Float.isFinite(f.nx()+f.ny()+f.nz()+f.na().x()+f.na().y()+f.na().z()+f.nb().x()+f.nb().y()+f.nb().z()+f.nc().x()+f.nc().y()+f.nc().z()))throw new AssertionError("Invalid normal");
                    total[lod]+=count;
                }
            }
            if(total[0]>24000||total[1]>7000||total[2]>2500||total[0]<=total[1]||total[1]<=total[2])throw new AssertionError(Arrays.toString(total));
            max=Math.max(max,total[0]);
            System.out.println(EquipmentRules.profile(c,t).name()+" "+Arrays.toString(total));
        }
        if(profiles.size()!=54||pieces!=216)throw new AssertionError("Incomplete catalog");
        System.out.println("54 profiles, 216 pieces; max close triangles "+max);
    }
}
