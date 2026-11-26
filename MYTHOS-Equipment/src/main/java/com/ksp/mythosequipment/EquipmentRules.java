package com.ksp.mythosequipment;

import java.util.*;
import java.util.random.RandomGenerator;

final class EquipmentRules {
    enum Stat { ATK, STR, DEF, VIT, DEX, INT, SPI }
    enum Slot { WEAPON, HEAD, CHEST, LEGS, FEET, SHIELD }
    enum Tier {
        ADVENTURER(1,1,3), TEMPERED(10,2,5), VETERAN(20,3,7), CHAMPION(30,4,9), DRAGON(40,6,12), ASCENDANT(50,9,16);
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
            case WARRIOR -> switch(t){case ADVENTURER->new Profile("Iron Vanguard","Oathbreaker",Stat.STR,Effect.BATTLE_FURY);case VETERAN->new Profile("Crimson Legion","Bloodcleaver",Stat.ATK,Effect.BLOOD_PRICE);case DRAGON->new Profile("Dragon Vanguard","Wyrmbane",Stat.STR,Effect.DRAGONFIRE);case TEMPERED->new Profile("Steel Vanguard","Iron Oath",Stat.STR,Effect.BATTLE_FURY);case CHAMPION->new Profile("Royal Legion","Crown's Wrath",Stat.ATK,Effect.BLOOD_PRICE);case ASCENDANT->new Profile("Eternal Conqueror","Emperor's Verdict",Stat.STR,Effect.DRAGONFIRE);};
            case HEAVY_KNIGHT -> switch(t){case ADVENTURER->new Profile("Stone Bastion","Stonebreaker",Stat.DEF,Effect.STONE_GUARD);case VETERAN->new Profile("Iron Citadel","Siegebreaker",Stat.VIT,Effect.CITADEL_BULWARK);case DRAGON->new Profile("Dragon Bastion","Titanfall",Stat.DEF,Effect.TITAN_GUARD);case TEMPERED->new Profile("Granite Ward","Granite Maul",Stat.DEF,Effect.STONE_GUARD);case CHAMPION->new Profile("Royal Fortress","Citadel Maul",Stat.VIT,Effect.CITADEL_BULWARK);case ASCENDANT->new Profile("Immortal Aegis","Atlas",Stat.DEF,Effect.TITAN_GUARD);};
            case RANGER -> switch(t){case ADVENTURER->new Profile("Wildwood Pathfinder","Thornstring",Stat.DEX,Effect.THORN_SNARE);case VETERAN->new Profile("Stormwatch","Galeheart",Stat.ATK,Effect.WINDRUNNER);case DRAGON->new Profile("Dragon Pathfinder","Skyfang",Stat.DEX,Effect.SKY_HUNTER);case TEMPERED->new Profile("Greenwarden","Briarshot",Stat.DEX,Effect.THORN_SNARE);case CHAMPION->new Profile("Tempest Hunter","Thunderstring",Stat.ATK,Effect.WINDRUNNER);case ASCENDANT->new Profile("Celestial Huntsman","Heavenpiercer",Stat.DEX,Effect.SKY_HUNTER);};
            case MAGE -> switch(t){case ADVENTURER->new Profile("Ember Arcanist","Cinderstaff",Stat.INT,Effect.EMBER_BRAND);case VETERAN->new Profile("Frostweave","Winterheart",Stat.SPI,Effect.FROSTBIND);case DRAGON->new Profile("Dragon Arcanist","Starfall",Stat.INT,Effect.ASTRAL_SURGE);case TEMPERED->new Profile("Runesilk","Rune Scepter",Stat.INT,Effect.EMBER_BRAND);case CHAMPION->new Profile("Astral Covenant","Astral Rod",Stat.SPI,Effect.FROSTBIND);case ASCENDANT->new Profile("Eternal Archmage","Cosmic Scepter",Stat.INT,Effect.ASTRAL_SURGE);};
            case ROGUE -> switch(t){case ADVENTURER->new Profile("Duskwalker","Whisperfang",Stat.DEX,Effect.SHADOWSTEP);case VETERAN->new Profile("Venomveil","Viper's Kiss",Stat.STR,Effect.VENOM_EDGE);case DRAGON->new Profile("Dragon Nightblade","Eclipse",Stat.ATK,Effect.ECLIPSE_VEIL);case TEMPERED->new Profile("Silent Veil","Silent Fang",Stat.DEX,Effect.SHADOWSTEP);case CHAMPION->new Profile("Phantom Court","Phantom Fang",Stat.STR,Effect.VENOM_EDGE);case ASCENDANT->new Profile("Eternal Shadow","Night's End",Stat.ATK,Effect.ECLIPSE_VEIL);};
            case CLERIC -> switch(t){case ADVENTURER->new Profile("Dawn Sanctified","Dawnlight",Stat.SPI,Effect.DAWN_MERCY);case VETERAN->new Profile("Silver Covenant","Mercy's Hand",Stat.VIT,Effect.SACRED_SHELTER);case DRAGON->new Profile("Dragon Sanctified","Seraph's Grace",Stat.SPI,Effect.SERAPHIC_RENEWAL);case TEMPERED->new Profile("Ivory Chapel","Ivory Light",Stat.SPI,Effect.DAWN_MERCY);case CHAMPION->new Profile("Radiant Covenant","Radiant Scepter",Stat.VIT,Effect.SACRED_SHELTER);case ASCENDANT->new Profile("Celestial Seraph","Divine Scepter",Stat.SPI,Effect.SERAPHIC_RENEWAL);};
            case NECROMANCER -> switch(t){case ADVENTURER->new Profile("Graveborn","Gravetouch",Stat.INT,Effect.GRAVE_CHILL);case VETERAN->new Profile("Bone Covenant","Marrowbinder",Stat.VIT,Effect.BONE_WARD);case DRAGON->new Profile("Dragon Graveborn","Soulreaper",Stat.SPI,Effect.SOUL_HARVEST);case TEMPERED->new Profile("Crypt Warden","Crypt Rod",Stat.INT,Effect.GRAVE_CHILL);case CHAMPION->new Profile("Ossuary Lord","Ossuary Staff",Stat.VIT,Effect.BONE_WARD);case ASCENDANT->new Profile("Eternal Lich","Death's Scepter",Stat.SPI,Effect.SOUL_HARVEST);};
            case HEXSLINGER -> switch(t){case ADVENTURER->new Profile("Ash Hexweaver","Ashmark",Stat.DEX,Effect.ASH_CURSE);case VETERAN->new Profile("Runebound","Runebreaker",Stat.INT,Effect.RUNE_FRACTURE);case DRAGON->new Profile("Dragon Hexweaver","Doomsigil",Stat.ATK,Effect.DOOM_MARK);case TEMPERED->new Profile("Rune Outlaw","Rune Caster",Stat.DEX,Effect.ASH_CURSE);case CHAMPION->new Profile("Void Marshal","Void Caster",Stat.INT,Effect.RUNE_FRACTURE);case ASCENDANT->new Profile("Eternal Hex","Fate Caster",Stat.ATK,Effect.DOOM_MARK);};
        };
    }
    enum WeaponKind {
        SWORD(5,1.6F,false), GREATSWORD(10,0.9F,true), SHORTSWORD(3,1.9F,false),
        DAGGER(2,2.4F,false), AXE(7,1.2F,false), GREATAXE(12,0.8F,true),
        HAMMER(8,1.0F,false), GREAT_HAMMER(13,0.7F,true), MACE(6,1.3F,false),
        STAFF(3,1.0F,true), WAND(1,1.8F,false), BOW(0,0,true), CROSSBOW(0,0,true), CASTER(2,1.5F,false), SHIELD(0,0,false);
        final int damage;final float speed;final boolean twoHanded;
        WeaponKind(int damage,float speed,boolean twoHanded){this.damage=damage;this.speed=speed;this.twoHanded=twoHanded;}
    }
    record Definition(String id,String name,ClassSet owner,Tier tier,Slot slot,WeaponKind kind,int level){
        Definition(String id,String name,ClassSet owner,Tier tier,Slot slot){
            this(id,name,owner,tier,slot,slot==Slot.WEAPON?switch(owner){
                case WARRIOR->WeaponKind.SWORD;case HEAVY_KNIGHT->WeaponKind.GREAT_HAMMER;
                case RANGER->WeaponKind.BOW;case ROGUE->WeaponKind.DAGGER;
                case MAGE,NECROMANCER,CLERIC->WeaponKind.STAFF;case HEXSLINGER->WeaponKind.CASTER;
            }:null,tier.level);
        }
        Profile profile(){return EquipmentRules.profile(owner,tier);}
        Effect effect(){return profile().effect();}
    }
    static final List<Definition> CATALOG=make();
    private static List<Definition> make(){
        List<Definition> out=new ArrayList<>();
        for(ClassSet c:ClassSet.values())for(Tier t:Tier.values())for(Slot s:Slot.values()){
            if(s==Slot.SHIELD&&c!=ClassSet.WARRIOR&&c!=ClassSet.HEAVY_KNIGHT&&c!=ClassSet.CLERIC)continue;
            String tier=t.name().toLowerCase(Locale.ROOT),slot=s.name().toLowerCase(Locale.ROOT);
            Profile p=profile(c,t);
            String item=switch(s){case WEAPON->p.weapon();case HEAD->p.name()+" Helm";case CHEST->p.name()+" Armor";case LEGS->p.name()+" Leggings";case FEET->p.name()+" Boots";case SHIELD->p.name()+" Shield";};
            out.add(s==Slot.SHIELD?new Definition(tier+"_"+c.id+"_"+slot,item,c,t,s,WeaponKind.SHIELD,t.level):new Definition(tier+"_"+c.id+"_"+slot,item,c,t,s));
        }
        add(out,ClassSet.WARRIOR,WeaponKind.SWORD,10,"Ironfang");
        add(out,ClassSet.WARRIOR,WeaponKind.SWORD,30,"Kingsblade");
        add(out,ClassSet.WARRIOR,WeaponKind.SWORD,50,"Sovereign's Edge");
        add(out,ClassSet.WARRIOR,WeaponKind.GREATSWORD,1,"Iron Colossus");
        add(out,ClassSet.WARRIOR,WeaponKind.GREATSWORD,10,"Executioner");
        add(out,ClassSet.WARRIOR,WeaponKind.GREATSWORD,20,"Dreadblade");
        add(out,ClassSet.WARRIOR,WeaponKind.GREATSWORD,30,"Warforged");
        add(out,ClassSet.WARRIOR,WeaponKind.GREATSWORD,40,"Dragon's Dominion");
        add(out,ClassSet.WARRIOR,WeaponKind.GREATSWORD,50,"Worldender");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.GREATSWORD,1,"Bastion Blade");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.GREATSWORD,10,"Stormbringer");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.GREATSWORD,20,"Blood Oath");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.GREATSWORD,30,"Frostmourner");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.GREATSWORD,40,"Dragonheart");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.GREATSWORD,50,"Celestial Judgement");
        add(out,ClassSet.WARRIOR,WeaponKind.AXE,1,"Iron Rend");
        add(out,ClassSet.WARRIOR,WeaponKind.AXE,10,"Ravager");
        add(out,ClassSet.WARRIOR,WeaponKind.AXE,20,"Bloodreaver");
        add(out,ClassSet.WARRIOR,WeaponKind.AXE,30,"King's Cleaver");
        add(out,ClassSet.WARRIOR,WeaponKind.AXE,40,"Dragon's Fang");
        add(out,ClassSet.WARRIOR,WeaponKind.AXE,50,"Tyrant's Wrath");
        add(out,ClassSet.WARRIOR,WeaponKind.GREATAXE,1,"Iron Reaper");
        add(out,ClassSet.WARRIOR,WeaponKind.GREATAXE,10,"Skullsplitter");
        add(out,ClassSet.WARRIOR,WeaponKind.GREATAXE,20,"Bloodstorm");
        add(out,ClassSet.WARRIOR,WeaponKind.GREATAXE,30,"Dread Executioner");
        add(out,ClassSet.WARRIOR,WeaponKind.GREATAXE,40,"Dragon Apocalypse");
        add(out,ClassSet.WARRIOR,WeaponKind.GREATAXE,50,"Ragnarok");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.GREATAXE,1,"Citadel Reaper");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.GREATAXE,10,"Siege Fang");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.GREATAXE,20,"Bastion Cleaver");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.GREATAXE,30,"Iron Tempest");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.GREATAXE,40,"Titan's Axe");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.GREATAXE,50,"Mountainfall");
        add(out,ClassSet.WARRIOR,WeaponKind.HAMMER,1,"Ironbreaker");
        add(out,ClassSet.WARRIOR,WeaponKind.HAMMER,20,"Bloodforge");
        add(out,ClassSet.WARRIOR,WeaponKind.HAMMER,40,"Worldsplitter");
        add(out,ClassSet.CLERIC,WeaponKind.HAMMER,1,"Dawnbringer");
        add(out,ClassSet.CLERIC,WeaponKind.HAMMER,20,"Light's Judgment");
        add(out,ClassSet.CLERIC,WeaponKind.HAMMER,40,"Divine Reckoning");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.MACE,1,"Ironclad");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.MACE,10,"Fortress Breaker");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.MACE,20,"Dreadcrusher");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.MACE,30,"Blacksteel Morningstar");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.MACE,40,"Dragon's Wrath");
        add(out,ClassSet.HEAVY_KNIGHT,WeaponKind.MACE,50,"Titan's Judgment");
        add(out,ClassSet.WARRIOR,WeaponKind.MACE,1,"Skullcrusher");
        add(out,ClassSet.WARRIOR,WeaponKind.MACE,10,"Bloodthorn");
        add(out,ClassSet.WARRIOR,WeaponKind.MACE,20,"Warbringer");
        add(out,ClassSet.WARRIOR,WeaponKind.MACE,30,"Crimson Morningstar");
        add(out,ClassSet.WARRIOR,WeaponKind.MACE,40,"Wyrmcrusher");
        add(out,ClassSet.WARRIOR,WeaponKind.MACE,50,"Kingsfall");
        add(out,ClassSet.CLERIC,WeaponKind.MACE,1,"Dawnstar");
        add(out,ClassSet.CLERIC,WeaponKind.MACE,10,"Sacred Justice");
        add(out,ClassSet.CLERIC,WeaponKind.MACE,20,"Lightbringer");
        add(out,ClassSet.CLERIC,WeaponKind.MACE,30,"Radiant Judgment");
        add(out,ClassSet.CLERIC,WeaponKind.MACE,40,"Seraph's Wrath");
        add(out,ClassSet.CLERIC,WeaponKind.MACE,50,"Hand of Divinity");
        add(out,ClassSet.ROGUE,WeaponKind.DAGGER,10,"Serpent's Tooth");
        add(out,ClassSet.ROGUE,WeaponKind.DAGGER,30,"Nightpiercer");
        add(out,ClassSet.ROGUE,WeaponKind.DAGGER,50,"Death's Embrace");
        add(out,ClassSet.ROGUE,WeaponKind.SHORTSWORD,1,"Steel Talon");
        add(out,ClassSet.ROGUE,WeaponKind.SHORTSWORD,10,"Quickfang");
        add(out,ClassSet.ROGUE,WeaponKind.SHORTSWORD,20,"Crimson Edge");
        add(out,ClassSet.ROGUE,WeaponKind.SHORTSWORD,30,"Phantom Blade");
        add(out,ClassSet.ROGUE,WeaponKind.SHORTSWORD,40,"Dragonfang");
        add(out,ClassSet.ROGUE,WeaponKind.SHORTSWORD,50,"Eternity's Edge");
        add(out,ClassSet.WARRIOR,WeaponKind.SHORTSWORD,1,"Iron Talon");
        add(out,ClassSet.WARRIOR,WeaponKind.SHORTSWORD,10,"Swiftsteel");
        add(out,ClassSet.WARRIOR,WeaponKind.SHORTSWORD,20,"Crimson Talon");
        add(out,ClassSet.WARRIOR,WeaponKind.SHORTSWORD,30,"Royal Fang");
        add(out,ClassSet.WARRIOR,WeaponKind.SHORTSWORD,40,"Wyrm Talon");
        add(out,ClassSet.WARRIOR,WeaponKind.SHORTSWORD,50,"Crownblade");
        add(out,ClassSet.MAGE,WeaponKind.STAFF,10,"Frostspire");
        add(out,ClassSet.MAGE,WeaponKind.STAFF,30,"Stormcaller");
        add(out,ClassSet.MAGE,WeaponKind.STAFF,50,"Astral Sovereign");
        add(out,ClassSet.MAGE,WeaponKind.WAND,1,"Emberlight");
        add(out,ClassSet.MAGE,WeaponKind.WAND,10,"Frostwhisper");
        add(out,ClassSet.MAGE,WeaponKind.WAND,20,"Arcane Whisper");
        add(out,ClassSet.MAGE,WeaponKind.WAND,30,"Thunderbite");
        add(out,ClassSet.MAGE,WeaponKind.WAND,40,"Voidshard");
        add(out,ClassSet.MAGE,WeaponKind.WAND,50,"Celestial Prism");
        add(out,ClassSet.NECROMANCER,WeaponKind.STAFF,10,"Bonecaller");
        add(out,ClassSet.NECROMANCER,WeaponKind.STAFF,30,"Deathspeaker");
        add(out,ClassSet.NECROMANCER,WeaponKind.STAFF,50,"Oblivion");
        add(out,ClassSet.NECROMANCER,WeaponKind.WAND,1,"Bone Whisper");
        add(out,ClassSet.NECROMANCER,WeaponKind.WAND,10,"Rotfang");
        add(out,ClassSet.NECROMANCER,WeaponKind.WAND,20,"Soulthorn");
        add(out,ClassSet.NECROMANCER,WeaponKind.WAND,30,"Gravebind");
        add(out,ClassSet.NECROMANCER,WeaponKind.WAND,40,"Dreadshard");
        add(out,ClassSet.NECROMANCER,WeaponKind.WAND,50,"Eternal Night");
        add(out,ClassSet.CLERIC,WeaponKind.STAFF,10,"Lightkeeper");
        add(out,ClassSet.CLERIC,WeaponKind.STAFF,30,"Sunward");
        add(out,ClassSet.CLERIC,WeaponKind.STAFF,50,"Divine Ascension");
        add(out,ClassSet.CLERIC,WeaponKind.WAND,1,"Light's Touch");
        add(out,ClassSet.CLERIC,WeaponKind.WAND,10,"Silver Grace");
        add(out,ClassSet.CLERIC,WeaponKind.WAND,20,"Sacred Bloom");
        add(out,ClassSet.CLERIC,WeaponKind.WAND,30,"Radiant Star");
        add(out,ClassSet.CLERIC,WeaponKind.WAND,40,"Angel's Blessing");
        add(out,ClassSet.CLERIC,WeaponKind.WAND,50,"Heaven's Light");
        add(out,ClassSet.HEXSLINGER,WeaponKind.WAND,1,"Hexthorn");
        add(out,ClassSet.HEXSLINGER,WeaponKind.WAND,10,"Curseweaver");
        add(out,ClassSet.HEXSLINGER,WeaponKind.WAND,20,"Rune Needle");
        add(out,ClassSet.HEXSLINGER,WeaponKind.WAND,30,"Malice");
        add(out,ClassSet.HEXSLINGER,WeaponKind.WAND,40,"Doom Needle");
        add(out,ClassSet.HEXSLINGER,WeaponKind.WAND,50,"Fatebreaker");
        add(out,ClassSet.RANGER,WeaponKind.CROSSBOW,1,"Ironbolt");
        add(out,ClassSet.RANGER,WeaponKind.CROSSBOW,10,"Windpiercer");
        add(out,ClassSet.RANGER,WeaponKind.CROSSBOW,20,"Thornbolt");
        add(out,ClassSet.RANGER,WeaponKind.CROSSBOW,30,"Stormbolt");
        add(out,ClassSet.RANGER,WeaponKind.CROSSBOW,40,"Dragonbolt");
        add(out,ClassSet.RANGER,WeaponKind.CROSSBOW,50,"Skybreaker");
        add(out,ClassSet.WARRIOR,WeaponKind.HAMMER,10,"Forgehand");
        add(out,ClassSet.WARRIOR,WeaponKind.HAMMER,30,"Crowncrusher");
        add(out,ClassSet.WARRIOR,WeaponKind.HAMMER,50,"Empirefall");
        add(out,ClassSet.CLERIC,WeaponKind.HAMMER,10,"Silver Bell");
        add(out,ClassSet.CLERIC,WeaponKind.HAMMER,30,"Solar Verdict");
        add(out,ClassSet.CLERIC,WeaponKind.HAMMER,50,"Heavenfall");
        return List.copyOf(out);
    }
    private static void add(List<Definition> out,ClassSet owner,WeaponKind kind,int level,String name){
        Tier tier=Arrays.stream(Tier.values()).filter(t->t.level==level).findFirst().orElseThrow();
        String id=owner.id+"_"+kind.name().toLowerCase(Locale.ROOT)+"_"+level;
        out.add(new Definition(id,name,owner,tier,Slot.WEAPON,kind,level));
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
