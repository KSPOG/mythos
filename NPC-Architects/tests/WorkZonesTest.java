package com.npcarchitects;
import java.util.*;
public final class WorkZonesTest {
    public static void main(String[] args){
        for(String mode:WorkZones.MODES)for(int count:new int[]{1,2,3,8,16,32,64}){
            Set<Integer> seen=new HashSet<>();
            for(int x=0;x<320;x++)for(int z=0;z<320;z++){
                int id=WorkZones.zone(mode,x,z,320,320,count);
                if(id<0||id>=count)throw new AssertionError(mode+" invalid zone "+id);
                seen.add(id);
            }
            if(seen.size()!=count)throw new AssertionError(mode+" unused zones "+count+" "+seen);
            for(int x=0;x<2;x++)for(int z=0;z<3;z++){
                int id=WorkZones.zone(mode,x,z,2,3,count);
                if(id<0||id>=count)throw new AssertionError("small footprint");
            }
        }
        System.out.println("WorkZones tests passed");
    }
}
