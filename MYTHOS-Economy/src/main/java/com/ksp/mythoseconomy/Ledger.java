package com.ksp.mythoseconomy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Single-writer ledger. All mutations persist before becoming visible. */
public final class Ledger {
    public static final long MAX_BALANCE=9_000_000_000_000L;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public record Entry(long sequence,long time,String kind,String actor,String from,String to,long amount,String reason){}
    public record Receipt(boolean success,String message,long sequence){}
    private static final class State {
        long sequence;
        Map<String,Map<String,Long>> tokens=new HashMap<>();
        Map<String,Set<String>> entitlements=new HashMap<>();
        Map<String,CreatureOffer> creatureOffers=new HashMap<>();
        Map<String,Entry> merchantReceipts=new HashMap<>();
        Map<String,Long> balances=new HashMap<>();
        List<Entry> history=new ArrayList<>();
    }
    public static final Set<String> CURRENCIES=Set.of("gold","mount_token","pet_token");
    public record CreatureOffer(String currency,long price,int stock,int max,boolean unlimited){}
    private static boolean creatureKey(String key){return key!=null&&key.matches("(mount|pet):[a-z0-9_]{1,40}");}
    private static boolean validOffer(CreatureOffer o,String key){return o!=null&&CURRENCIES.contains(o.currency)&&("gold".equals(o.currency)||o.currency.equals(key.startsWith("mount:")?"mount_token":"pet_token"))&&o.price>0&&o.price<=MAX_BALANCE&&o.stock>=0&&o.stock<=o.max&&o.max<=1000000;}
    private final Path path;
    private final Consumer<Exception> errors;
    private State state;
    private boolean locked;
    public Ledger(Path path,Consumer<Exception> errors)throws IOException{
        this.path=path;this.errors=errors;
        try {
            state=Files.exists(path)?JSON.fromJson(Files.readString(path),State.class):new State();
            validate(state);
        }catch(RuntimeException ex){throw new IOException("Invalid economy ledger",ex);}
    }
    private static void validate(State s){
        if(s==null||s.sequence<0||s.balances==null||s.history==null||s.history.size()>1000)throw new IllegalArgumentException("Invalid ledger");
        for(var b:s.balances.entrySet()){
            if(!UUID.fromString(b.getKey()).toString().equals(b.getKey())||b.getValue()==null||b.getValue()<0||b.getValue()>MAX_BALANCE)throw new IllegalArgumentException("Invalid balance");
        }
        if(s.merchantReceipts==null)s.merchantReceipts=new HashMap<>();
        for(var receipt:s.merchantReceipts.entrySet()){
            UUID.fromString(receipt.getKey());var e=receipt.getValue();
            if(e==null||e.sequence()<1||e.sequence()>s.sequence||e.amount()<1||e.amount()>MAX_BALANCE||!Set.of("MINT","BURN").contains(e.kind())||!validReason(e.reason()))throw new IllegalArgumentException("Invalid merchant receipt");
            UUID.fromString(e.actor());
            if(e.kind().startsWith("MINT")){if(e.from()!=null||e.to()==null)throw new IllegalArgumentException("Invalid mint receipt");UUID.fromString(e.to());}
            else{if(e.to()!=null||e.from()==null)throw new IllegalArgumentException("Invalid burn receipt");UUID.fromString(e.from());}
        }
        if(s.tokens==null||s.entitlements==null||s.creatureOffers==null)throw new IllegalArgumentException("Invalid extension state");
        for(var e:s.tokens.entrySet()){
            if(!Set.of("mount_token","pet_token").contains(e.getKey())||e.getValue()==null)throw new IllegalArgumentException("Invalid currency");
            for(var b:e.getValue().entrySet()){UUID.fromString(b.getKey());if(b.getValue()==null||b.getValue()<0||b.getValue()>MAX_BALANCE)throw new IllegalArgumentException("Invalid token balance");}
        }
        for(var e:s.entitlements.entrySet()){UUID.fromString(e.getKey());if(e.getValue()==null||e.getValue().stream().anyMatch(k->!creatureKey(k)))throw new IllegalArgumentException("Invalid ownership");}
        for(var e:s.creatureOffers.entrySet()){var parts=e.getKey().split("/",-1);if(parts.length!=2||!creatureKey(parts[1])||!validOffer(e.getValue(),parts[1]))throw new IllegalArgumentException("Invalid creature offer");UUID.fromString(parts[0]);}
        long prev=s.sequence-s.history.size();
        if(prev<0)throw new IllegalArgumentException("Invalid history length");
        for(var e:s.history){
            if(e==null||e.sequence()!=++prev||e.amount()<=0||e.amount()>MAX_BALANCE||!validKind(e.kind())||!validReason(e.reason()))throw new IllegalArgumentException("Invalid history entry");
            UUID.fromString(e.actor());
            if(e.from()!=null)UUID.fromString(e.from());
            if(e.to()!=null)UUID.fromString(e.to());
            if((e.kind().equals("TRANSFER")&&(e.from()==null||e.to()==null||e.from().equals(e.to())))||(e.kind().startsWith("MINT")&&(e.from()!=null||e.to()==null))||(e.kind().startsWith("BURN")&&(e.from()==null||e.to()!=null)))throw new IllegalArgumentException("Invalid endpoints");
        }
    }
    private static boolean validKind(String kind){return kind!=null&&Set.of("TRANSFER","MINT","BURN","MINT:mount_token","BURN:mount_token","MINT:pet_token","BURN:pet_token").contains(kind);}
    public static String currency(Entry e){int i=e.kind().indexOf(':');return i<0?"gold":e.kind().substring(i+1);}
    private static boolean validReason(String reason){return reason!=null&&!reason.isBlank()&&reason.length()<=160&&reason.chars().noneMatch(Character::isISOControl);}
    public synchronized boolean available(){return !locked;}
    public synchronized long balance(UUID account){if(locked)throw new IllegalStateException("Economy locked");return state.balances.getOrDefault(account.toString(),0L);}
    public synchronized List<Entry> recent(UUID account,int limit){
        if(locked)throw new IllegalStateException("Economy locked");
        var result=new ArrayList<Entry>();
        for(int i=state.history.size()-1;i>=0&&result.size()<Math.min(20,Math.max(0,limit));i--){
            var e=state.history.get(i);
            if(account.toString().equals(e.from())||account.toString().equals(e.to()))result.add(e);
        }
        return List.copyOf(result);
    }
    public synchronized Receipt transfer(UUID from,UUID to,long amount,String reason){return change("TRANSFER",from,from,to,amount,reason);}
    public synchronized Receipt mint(UUID actor,UUID to,long amount,String reason){return change("MINT",actor,null,to,amount,reason);}
    public synchronized Receipt burn(UUID actor,UUID from,long amount,String reason){return change("BURN",actor,from,null,amount,reason);}
    private Receipt reject(String message){return new Receipt(false,message,state.sequence);}
    public synchronized Entry merchantReceipt(UUID transaction){
        if(locked)throw new IllegalStateException("Economy locked");
        return state.merchantReceipts.get(transaction.toString());
    }
    public synchronized Receipt merchantChange(UUID transaction,UUID player,long amount,boolean credit,String reason){
        if(transaction==null||player==null)return reject("Invalid merchant transaction");
        if(locked)return reject("Economy storage locked");
        var existing=state.merchantReceipts.get(transaction.toString());
        String kind=credit?"MINT":"BURN";
        if(existing!=null){
            if(existing.kind().equals(kind)&&existing.amount()==amount&&existing.actor().equals(player.toString())&&existing.reason().equals(reason))
                return new Receipt(true,"Already committed",existing.sequence());
            return reject("Transaction ID conflict");
        }
        return change(kind,player,credit?null:player,credit?player:null,amount,reason,transaction);
    }
    private Receipt change(String kind,UUID actor,UUID from,UUID to,long amount,String reason){
        return change(kind,actor,from,to,amount,reason,null);
    }
    private Receipt change(String kind,UUID actor,UUID from,UUID to,long amount,String reason,UUID transaction){
        if(locked)return reject("Economy storage locked");
        if(actor==null||amount<=0||amount>MAX_BALANCE||!validReason(reason)||from!=null&&from.equals(to)||state.sequence==Long.MAX_VALUE)return reject("Invalid transaction");
        if(kind.equals("TRANSFER")&&(from==null||to==null)||kind.equals("MINT")&&to==null||kind.equals("BURN")&&from==null)return reject("Invalid accounts");
        long debit=from==null?0:balance(from),credit=to==null?0:balance(to);
        if(from!=null&&debit<amount)return reject("Insufficient gold");
        if(to!=null&&credit>MAX_BALANCE-amount)return reject("Balance limit exceeded");
        var next=copyState();next.sequence=state.sequence+1;
        if(from!=null)next.balances.put(from.toString(),debit-amount);
        if(to!=null)next.balances.put(to.toString(),credit+amount);
        next.history.add(new Entry(next.sequence,System.currentTimeMillis(),kind,actor.toString(),from==null?null:from.toString(),to==null?null:to.toString(),amount,reason));
        if(transaction!=null)next.merchantReceipts.put(transaction.toString(),next.history.get(next.history.size()-1));
        if(next.history.size()>1000)next.history.remove(0);
        try{
            persist(next);
            state=next;
            return new Receipt(true,"Committed",next.sequence);
        }catch(IOException|RuntimeException ex){
            locked=true;errors.accept(ex);return reject("Storage error; economy locked");
        }
    }
    private State copyState(){
        var n=new State();n.sequence=state.sequence;n.balances.putAll(state.balances);n.history.addAll(state.history);n.merchantReceipts.putAll(state.merchantReceipts);n.creatureOffers.putAll(state.creatureOffers);
        state.tokens.forEach((k,v)->n.tokens.put(k,new HashMap<>(v)));state.entitlements.forEach((k,v)->n.entitlements.put(k,new HashSet<>(v)));return n;
    }
    private Receipt commit(State n,String message){
        try{persist(n);state=n;return new Receipt(true,message,n.sequence);}
        catch(IOException|RuntimeException ex){locked=true;errors.accept(ex);return reject("Storage error; economy locked");}
    }
    public synchronized long balance(UUID player,String currency){
        if(locked||!CURRENCIES.contains(currency))throw new IllegalStateException("Currency unavailable");
        return currency.equals("gold")?balance(player):state.tokens.getOrDefault(currency,Map.of()).getOrDefault(player.toString(),0L);
    }
    private void setBalance(State n,UUID player,String currency,long value){
        if(currency.equals("gold"))n.balances.put(player.toString(),value);
        else n.tokens.computeIfAbsent(currency,k->new HashMap<>()).put(player.toString(),value);
    }
    private void record(State n,UUID actor,UUID player,String currency,long amount,boolean credit,String reason){
        String id=player.toString(),kind=credit?"MINT":"BURN";
        n.history.add(new Entry(++n.sequence,System.currentTimeMillis(),currency.equals("gold")?kind:kind+":"+currency,actor.toString(),credit?null:id,credit?id:null,amount,reason));
        if(n.history.size()>1000)n.history.remove(0);
    }
    public synchronized Receipt adjust(UUID actor,UUID player,String currency,long amount,boolean credit,String reason){
        if(locked||actor==null||player==null||!CURRENCIES.contains(currency)||amount<1||amount>MAX_BALANCE||!validReason(reason)||state.sequence==Long.MAX_VALUE)return reject("Invalid currency transaction");
        long old=balance(player,currency);
        if(credit?old>MAX_BALANCE-amount:old<amount)return reject("Insufficient funds or balance limit");
        var n=copyState();setBalance(n,player,currency,old+(credit?amount:-amount));record(n,actor,player,currency,amount,credit,reason);return commit(n,"Committed");
    }
    public synchronized boolean ownsCreature(UUID player,String key){return !locked&&state.entitlements.getOrDefault(player.toString(),Set.of()).contains(key);}
    public synchronized Map<String,CreatureOffer> creatureOffers(UUID shop){
        if(locked)throw new IllegalStateException("Economy locked");
        var out=new TreeMap<String,CreatureOffer>();String prefix=shop+"/";state.creatureOffers.forEach((k,v)->{if(k.startsWith(prefix))out.put(k.substring(prefix.length()),v);});return Collections.unmodifiableMap(out);
    }
    public synchronized Receipt configureCreature(UUID shop,String key,CreatureOffer offer){
        if(locked||shop==null||!creatureKey(key)||offer!=null&&!validOffer(offer,key))return reject("Invalid offer");
        var n=copyState();String id=shop+"/"+key;
        if(offer==null){if(n.creatureOffers.remove(id)==null)return reject("Offer missing");}
        else {if(!n.creatureOffers.containsKey(id)&&creatureOffers(shop).size()>=45)return reject("Offer limit");n.creatureOffers.put(id,offer);}
        return commit(n,"Offer saved");
    }
    public synchronized Receipt replaceCreatureOffers(UUID shop,Map<String,CreatureOffer> offers){
        if(locked||shop==null||offers==null||offers.size()>45||offers.entrySet().stream().anyMatch(e->!creatureKey(e.getKey())||!validOffer(e.getValue(),e.getKey())))return reject("Invalid creature offers");
        var n=copyState();String prefix=shop+"/";n.creatureOffers.keySet().removeIf(k->k.startsWith(prefix));
        offers.forEach((k,v)->n.creatureOffers.put(prefix+k,v));return commit(n,"Creature catalog saved");
    }
    public synchronized Receipt purchaseCreature(UUID shop,UUID player,String key,String currency,long price){
        if(locked||shop==null||player==null||!creatureKey(key)||state.sequence==Long.MAX_VALUE)return reject("Invalid purchase");
        String id=shop+"/"+key;var offer=state.creatureOffers.get(id);
        if(offer==null||!offer.currency.equals(currency)||offer.price!=price)return reject("Offer changed");
        if(ownsCreature(player,key))return reject("Already owned");
        if(!offer.unlimited&&offer.stock==0)return reject("Out of stock");
        long old=balance(player,currency);if(old<price)return reject("Insufficient "+currency);
        var n=copyState();setBalance(n,player,currency,old-price);
        n.entitlements.computeIfAbsent(player.toString(),k->new HashSet<>()).add(key);
        if(!offer.unlimited)n.creatureOffers.put(id,new CreatureOffer(currency,price,offer.stock-1,offer.max,false));
        record(n,player,player,currency,price,false,"Creature "+key+" at "+shop);return commit(n,"Purchased "+key);
    }
    private void persist(State next)throws IOException{
        var tmp=path.resolveSibling(path.getFileName()+".tmp");
        var bytes=ByteBuffer.wrap(JSON.toJson(next).getBytes(StandardCharsets.UTF_8));
        try(var channel=FileChannel.open(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){
            while(bytes.hasRemaining())channel.write(bytes);
            channel.force(true);
        }
        // Never fall back to a non-atomic overwrite. Unsupported filesystems fail closed.
        Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
}
