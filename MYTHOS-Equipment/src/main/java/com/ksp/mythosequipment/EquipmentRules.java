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
    enum Rarity {
        COMMON(4500,1,0), UNCOMMON(2800,2,1), RARE(1600,3,2),
        EPIC(800,4,4), LEGENDARY(250,5,7), MYTHICAL(50,6,10);
        final int weight,count,bonus;
        Rarity(int weight,int count,int bonus){this.weight=weight;this.count=count;this.bonus=bonus;}
        boolean hasEffect(){return ordinal()>=RARE.ordinal();}
        int effectRank(){return hasEffect()?ordinal()-RARE.ordinal()+1:0;}
        String label(){String s=name().toLowerCase(Locale.ROOT);return Character.toUpperCase(s.charAt(0))+s.substring(1);}
        static Rarity random(RandomGenerator rng){
            int value=rng.nextInt(10000);
            for(Rarity r:values()){value-=r.weight;if(value<0)return r;}
            throw new IllegalStateException("Invalid rarity weights");
        }
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
    enum Effect {
        BATTLE_FURY("Battle Fury","Strength on hit","strength"),
        BLOOD_PRICE("Blood Price","Heal on hit","heal"),
        DRAGONFIRE("Dragonfire","Ignite target","burn"),
        STONE_GUARD("Stone Guard","Resistance on hit","resistance"),
        CITADEL_BULWARK("Citadel Bulwark","Absorption on hit","absorption"),
        TITAN_GUARD("Titan Guard","Resistance and absorption on hit","guard"),
        THORN_SNARE("Thorn Snare","Slow target","slow"),
        WINDRUNNER("Windrunner","Speed on hit","speed"),
        SKY_HUNTER("Sky Hunter","Speed and slow on hit","hunt"),
        EMBER_BRAND("Ember Brand","Ignite target","burn"),
        FROSTBIND("Frostbind","Slow and weaken target","frost"),
        ASTRAL_SURGE("Astral Surge","Strength and regeneration on hit","surge"),
        SHADOWSTEP("Shadowstep","Speed on hit","speed"),
        VENOM_EDGE("Venom Edge","Poison target","poison"),
        ECLIPSE_VEIL("Eclipse Veil","Invisibility and speed on hit","veil"),
        DAWN_MERCY("Dawn Mercy","Heal on hit","heal"),
        SACRED_SHELTER("Sacred Shelter","Resistance and regeneration on hit","shelter"),
        SERAPHIC_RENEWAL("Seraphic Renewal","Heal and regenerate on hit","renew"),
        GRAVE_CHILL("Grave Chill","Weakness on target","weakness"),
        BONE_WARD("Bone Ward","Absorption on hit","absorption"),
        SOUL_HARVEST("Soul Harvest","Wither target and heal","harvest"),
        ASH_CURSE("Ash Curse","Weakness and burning on target","curse"),
        RUNE_FRACTURE("Rune Fracture","Weakness and poison on target","fracture"),
        DOOM_MARK("Doom Mark","Wither and slowness on target","doom");
        final String label,description,kind;
        Effect(String label,String description,String kind){this.label=label;this.description=description;this.kind=kind;}
    }
    record Profile(String name,String weapon,Stat focus,Effect effect){}
    static Profile profile(ClassSet c,Tier t){
        return switch(c){
            case WARRIOR -> switch(t){case ADVENTURER->new Profile("Iron Vanguard","Oathbreaker",Stat.STR,Effect.BATTLE_FURY);case VETERAN->new Profile("Crimson Legion","Bloodcleaver",Stat.ATK,Effect.BLOOD_PRICE);case DRAGON->new Profile("Dragon Vanguard","Wyrmbane",Stat.STR,Effect.DRAGONFIRE);};
            case HEAVY_KNIGHT -> switch(t){case ADVENTURER->new Profile("Stone Bastion","Stonebreaker",Stat.DEF,Effect.STONE_GUARD);case VETERAN->new Profile("Iron Citadel","Siegebreaker",Stat.VIT,Effect.CITADEL_BULWARK);case DRAGON->new Profile("Dragon Bastion","Titanfall",Stat.DEF,Effect.TITAN_GUARD);};
            case RANGER -> switch(t){case ADVENTURER->new Profile("Wildwood Pathfinder","Thornstring",Stat.DEX,Effect.THORN_SNARE);case VETERAN->new Profile("Stormwatch","Galeheart",Stat.ATK,Effect.WINDRUNNER);case DRAGON->new Profile("Dragon Pathfinder","Skyfang",Stat.DEX,Effect.SKY_HUNTER);};
            case MAGE -> switch(t){case ADVENTURER->new Profile("Ember Arcanist","Cinderstaff",Stat.INT,Effect.EMBER_BRAND);case VETERAN->new Profile("Frostweave","Winterheart",Stat.SPI,Effect.FROSTBIND);case DRAGON->new Profile("Dragon Arcanist","Starfall",Stat.INT,Effect.ASTRAL_SURGE);};
            case ROGUE -> switch(t){case ADVENTURER->new Profile("Duskwalker","Whisperfang",Stat.DEX,Effect.SHADOWSTEP);case VETERAN->new Profile("Venomveil","Viper's Kiss",Stat.STR,Effect.VENOM_EDGE);case DRAGON->new Profile("Dragon Nightblade","Eclipse",Stat.ATK,Effect.ECLIPSE_VEIL);};
            case CLERIC -> switch(t){case ADVENTURER->new Profile("Dawn Sanctified","Dawnlight",Stat.SPI,Effect.DAWN_MERCY);case VETERAN->new Profile("Silver Covenant","Mercy's Hand",Stat.VIT,Effect.SACRED_SHELTER);case DRAGON->new Profile("Dragon Sanctified","Seraph's Grace",Stat.SPI,Effect.SERAPHIC_RENEWAL);};
            case NECROMANCER -> switch(t){case ADVENTURER->new Profile("Graveborn","Gravetouch",Stat.INT,Effect.GRAVE_CHILL);case VETERAN->new Profile("Bone Covenant","Marrowbinder",Stat.VIT,Effect.BONE_WARD);case DRAGON->new Profile("Dragon Graveborn","Soulreaper",Stat.SPI,Effect.SOUL_HARVEST);};
            case HEXSLINGER -> switch(t){case ADVENTURER->new Profile("Ash Hexweaver","Ashmark",Stat.DEX,Effect.ASH_CURSE);case VETERAN->new Profile("Runebound","Runebreaker",Stat.INT,Effect.RUNE_FRACTURE);case DRAGON->new Profile("Dragon Hexweaver","Doomsigil",Stat.ATK,Effect.DOOM_MARK);};
        };
    }
    record Definition(String id,String name,ClassSet owner,Tier tier,Slot slot){
        Profile profile(){return EquipmentRules.profile(owner,tier);}
        Effect effect(){return profile().effect();}
    }
    static final List<Definition> CATALOG=make();
    private static List<Definition> make(){
        List<Definition> out=new ArrayList<>();
        for(ClassSet c:ClassSet.values())for(Tier t:Tier.values())for(Slot s:Slot.values()){
            String tier=t.name().toLowerCase(Locale.ROOT),slot=s.name().toLowerCase(Locale.ROOT);
            Profile p=profile(c,t);
            String item=switch(s){case WEAPON->p.weapon();case HEAD->p.name()+" Helm";case CHEST->p.name()+" Armor";case LEGS->p.name()+" Leggings";case FEET->p.name()+" Boots";};
            out.add(new Definition(tier+"_"+c.id+"_"+slot,item,c,t,s));
        }
        return List.copyOf(out);
    }
    static int min(Definition d,Rarity rarity){return d.tier.min+rarity.bonus;}
    static int max(Definition d,Rarity rarity){return d.tier.max+rarity.bonus;}
    static Map<Stat,Integer> roll(Definition d,Rarity rarity,RandomGenerator rng){
        var result=new EnumMap<Stat,Integer>(Stat.class);
        var first=d.slot==Slot.WEAPON?d.owner.preferred.getFirst():Stat.DEF;
        result.put(first,rng.nextInt(min(d,rarity),max(d,rarity)+1));
        var candidates=new ArrayList<>(Arrays.asList(Stat.values()));candidates.remove(first);
        for(int i=1;i<rarity.count;i++){
            var weighted=new ArrayList<Stat>();
            for(Stat stat:candidates){int weight=stat==d.profile().focus()?6:d.owner.preferred.contains(stat)?3:1;for(int j=0;j<weight;j++)weighted.add(stat);}
            Stat stat=weighted.get(rng.nextInt(weighted.size()));candidates.remove(stat);
            result.put(stat,rng.nextInt(min(d,rarity),max(d,rarity)+1));
        }
        return Collections.unmodifiableMap(result);
    }
    static boolean valid(Definition d,Rarity rarity,Map<Stat,Integer> values){
        if(rarity==null||values==null||values.size()!=rarity.count||!values.containsKey(d.slot==Slot.WEAPON?d.owner.preferred.getFirst():Stat.DEF))return false;
        for(var e:values.entrySet())if(e.getKey()==null||e.getValue()==null||e.getValue()<min(d,rarity)||e.getValue()>max(d,rarity))return false;
        return true;
    }
}
