package com.ksp.mythoseconomy;

import net.minecraft.server.MinecraftServer;
import java.util.UUID;

/** Public API for trusted server mods. All calls must run on the server thread.
 * Currency-only operations; a debit is NOT an atomic purchase of another mod's asset.
 */
public final class EconomyApi {
    private static MinecraftServer server;
    private static Ledger ledger;
    public record Creature(String key,String name,String description,boolean shop){}
    public interface CreatureProvider {
        java.util.List<Creature> catalog();
        boolean available();
        boolean legacyOwns(UUID player,String key);
    }
    private static final java.util.Map<String,CreatureProvider> providers=new java.util.HashMap<>();
    public static void registerCreatures(String kind,CreatureProvider provider){
        if(!java.util.Set.of("mount","pet").contains(kind)||providers.putIfAbsent(kind,provider)!=null)throw new IllegalStateException("Invalid provider");
    }
    private static CreatureProvider provider(String key){int i=key.indexOf(':');return i<0?null:providers.get(key.substring(0,i));}
    public static java.util.List<Creature> creatures(MinecraftServer s,String kind){require(s);var p=providers.get(kind);return p==null||!p.available()?java.util.List.of():java.util.List.copyOf(p.catalog());}
    public static boolean ownsCreature(UUID player,String key){return ledger!=null&&server!=null&&server.isSameThread()&&ledger.ownsCreature(player,key);}
    public static boolean creatureOwned(MinecraftServer s,UUID player,String key){var l=require(s);var p=provider(key);return l.ownsCreature(player,key)||p!=null&&p.available()&&p.legacyOwns(player,key);}
    public static java.util.Map<String,Ledger.CreatureOffer> creatureOffers(MinecraftServer s,UUID shop){return require(s).creatureOffers(shop);}
    private static boolean saleAllowed(CreatureProvider p,String key){return p!=null&&p.available()&&p.catalog().stream().anyMatch(c->c.key().equals(key)&&c.shop());}
    public static Ledger.Receipt configureCreature(MinecraftServer s,UUID shop,String key,Ledger.CreatureOffer offer){var l=require(s);if(offer!=null&&!saleAllowed(provider(key),key))return new Ledger.Receipt(false,"Not available for shop purchase",-1);return l.configureCreature(shop,key,offer);}
    public static Ledger.Receipt replaceCreatureOffers(MinecraftServer s,UUID shop,java.util.Map<String,Ledger.CreatureOffer> offers){
        var l=require(s);if(offers==null||offers.keySet().stream().anyMatch(k->k==null||!saleAllowed(provider(k),k)))return new Ledger.Receipt(false,"Invalid or unavailable creature",-1);
        return l.replaceCreatureOffers(shop,offers);
    }
    public static Ledger.Receipt purchaseCreature(MinecraftServer s,UUID shop,UUID player,String key,String currency,long price){var l=require(s);var p=provider(key);if(!saleAllowed(p,key))return new Ledger.Receipt(false,"Creature unavailable",-1);if(p.legacyOwns(player,key))return new Ledger.Receipt(false,"Already owned",-1);return l.purchaseCreature(shop,player,key,currency,price);}
    public static long balance(MinecraftServer s,UUID player,String currency){return require(s).balance(player,currency);}
    public static Ledger.Receipt adjust(MinecraftServer s,UUID actor,UUID player,String currency,long amount,boolean credit,String reason){return require(s).adjust(actor,player,currency,amount,credit,reason);}
    private EconomyApi(){}
    static void attach(MinecraftServer s,Ledger l){server=s;ledger=l;}
    static void detach(){server=null;ledger=null;}
    private static Ledger require(MinecraftServer s){
        if(s==null||!s.isSameThread()||server!=s||ledger==null||!ledger.available())throw new IllegalStateException("Economy unavailable or called outside server thread");
        return ledger;
    }
    public static boolean available(MinecraftServer s){return s!=null&&s.isSameThread()&&server==s&&ledger!=null&&ledger.available();}
    public static Ledger.Entry merchantReceipt(MinecraftServer s,UUID transaction){return require(s).merchantReceipt(transaction);}
    public static Ledger.Receipt merchantChange(MinecraftServer s,UUID transaction,UUID player,long amount,boolean credit,String reason){return require(s).merchantChange(transaction,player,amount,credit,reason);}
    public static long balance(MinecraftServer s,UUID player){return require(s).balance(player);}
    public static Ledger.Receipt transfer(MinecraftServer s,UUID from,UUID to,long amount,String reason){return require(s).transfer(from,to,amount,reason);}
    public static Ledger.Receipt debit(MinecraftServer s,UUID actor,UUID player,long amount,String reason){return require(s).burn(actor,player,amount,reason);}
    public static Ledger.Receipt credit(MinecraftServer s,UUID actor,UUID player,long amount,String reason){return require(s).mint(actor,player,amount,reason);}
}
