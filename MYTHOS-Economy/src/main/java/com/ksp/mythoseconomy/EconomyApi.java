package com.ksp.mythoseconomy;

import net.minecraft.server.MinecraftServer;
import java.util.UUID;

/** Public API for trusted server mods. All calls must run on the server thread.
 * Currency-only operations; a debit is NOT an atomic purchase of another mod's asset.
 */
public final class EconomyApi {
    private static MinecraftServer server;
    private static Ledger ledger;
    private EconomyApi(){}
    static void attach(MinecraftServer s,Ledger l){server=s;ledger=l;}
    static void detach(){server=null;ledger=null;}
    private static Ledger require(MinecraftServer s){
        if(s==null||!s.isSameThread()||server!=s||ledger==null||!ledger.available())throw new IllegalStateException("Economy unavailable or called outside server thread");
        return ledger;
    }
    public static boolean available(MinecraftServer s){return s!=null&&s.isSameThread()&&server==s&&ledger!=null&&ledger.available();}
    public static long balance(MinecraftServer s,UUID player){return require(s).balance(player);}
    public static Ledger.Receipt transfer(MinecraftServer s,UUID from,UUID to,long amount,String reason){return require(s).transfer(from,to,amount,reason);}
    public static Ledger.Receipt debit(MinecraftServer s,UUID actor,UUID player,long amount,String reason){return require(s).burn(actor,player,amount,reason);}
    public static Ledger.Receipt credit(MinecraftServer s,UUID actor,UUID player,long amount,String reason){return require(s).mint(actor,player,amount,reason);}
}
