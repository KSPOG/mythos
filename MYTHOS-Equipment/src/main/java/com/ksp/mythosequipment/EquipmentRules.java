package com.ksp.mythosequipment;

import java.util.*;
import java.util.random.RandomGenerator;

final class EquipmentRules {
    enum Stat { ATK, STR, DEF, VIT, DEX, INT, SPI }
    enum Slot { WEAPON, HEAD, CHEST, LEGS, FEET }
    enum Tier {
        ADVENTURER(1,1,3), VETERAN(20,3,7), DRAGON(40,6,12);
        final int level,min,max;
        Tier(int level,int min,int max){this.level=level;this.min=min;this.max=max;}
    }
    enum ClassSet {
        WARRIOR("warrior","Vanguard","Longsword",0xB44C35,Stat.STR,Stat.ATK,Stat.DEF),
        HEAVY_KNIGHT("heavy_knight","Bastion","Warhammer",0x718298,Stat.DEF,Stat.VIT,Stat.STR),
        RANGER("ranger","Pathfinder","Longbow",0x53874D,Stat.DEX,Stat.ATK,Stat.VIT),
        MAGE("mage","Arcanist","Arcane Staff",0x8270DA,Stat.INT,Stat.ATK,Stat.SPI),
        ROGUE("rogue","Nightblade","Dagger",0x795471,Stat.DEX,Stat.STR,Stat.ATK),
        CLERIC("cleric","Sanctified","Holy Scepter",0xD6B759,Stat.SPI,Stat.VIT,Stat.INT),
        NECROMANCER("necromancer","Graveborn","Soul Staff",0x578B86,Stat.INT,Stat.SPI,Stat.VIT),
        HEXSLINGER("hexslinger","Hexweaver","Hex Caster",0xB65CD0,Stat.DEX,Stat.INT,Stat.ATK);
        final String id,label,weapon;final int color;final List<Stat> preferred;
        ClassSet(String id,String label,String weapon,int color,Stat... stats){this.id=id;this.label=label;this.weapon=weapon;this.color=color;preferred=List.of(stats);}
    }
    record Definition(String id,String name,ClassSet owner,Tier tier,Slot slot){}
    static final List<Definition> CATALOG=make();
    private static List<Definition> make(){
        List<Definition> out=new ArrayList<>();
        for(ClassSet c:ClassSet.values())for(Tier t:Tier.values())for(Slot s:Slot.values()){
            String tier=t.name().toLowerCase(Locale.ROOT),slot=s.name().toLowerCase(Locale.ROOT);
            String item=switch(s){case WEAPON->c.weapon;case HEAD->"Helm";case CHEST->"Armor";case LEGS->"Leggings";case FEET->"Boots";};
            String title=t==Tier.DRAGON?"Dragon":t==Tier.VETERAN?"Veteran":"Adventurer";
            out.add(new Definition(tier+"_"+c.id+"_"+slot,title+" "+c.label+" "+item,c,t,s));
        }
        return List.copyOf(out);
    }
    static Map<Stat,Integer> roll(Definition d,RandomGenerator rng){
        var result=new EnumMap<Stat,Integer>(Stat.class);
        var first=d.slot==Slot.WEAPON?d.owner.preferred.getFirst():Stat.DEF;
        result.put(first,rng.nextInt(d.tier.min,d.tier.max+1));
        var candidates=new ArrayList<>(Arrays.asList(Stat.values()));candidates.remove(first);
        // Two additional distinct stats; class-favored stats are weighted 3:1.
        for(int i=0;i<2;i++){
            var weighted=new ArrayList<Stat>();
            for(Stat stat:candidates){int weight=d.owner.preferred.contains(stat)?3:1;for(int j=0;j<weight;j++)weighted.add(stat);}
            Stat stat=weighted.get(rng.nextInt(weighted.size()));candidates.remove(stat);
            result.put(stat,rng.nextInt(d.tier.min,d.tier.max+1));
        }
        return Collections.unmodifiableMap(result);
    }
    static boolean valid(Definition d,Map<Stat,Integer> values){
        if(values==null||values.size()!=3||!values.containsKey(d.slot==Slot.WEAPON?d.owner.preferred.getFirst():Stat.DEF))return false;
        for(var e:values.entrySet())if(e.getKey()==null||e.getValue()==null||e.getValue()<d.tier.min||e.getValue()>d.tier.max)return false;
        return true;
    }
}
