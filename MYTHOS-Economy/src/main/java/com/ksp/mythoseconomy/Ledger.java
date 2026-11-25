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
        Map<String,Long> balances=new HashMap<>();
        List<Entry> history=new ArrayList<>();
    }
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
        long prev=s.sequence-s.history.size();
        if(prev<0)throw new IllegalArgumentException("Invalid history length");
        for(var e:s.history){
            if(e==null||e.sequence()!=++prev||e.amount()<=0||e.amount()>MAX_BALANCE||!Set.of("TRANSFER","MINT","BURN").contains(e.kind())||!validReason(e.reason()))throw new IllegalArgumentException("Invalid history entry");
            UUID.fromString(e.actor());
            if(e.from()!=null)UUID.fromString(e.from());
            if(e.to()!=null)UUID.fromString(e.to());
            if((e.kind().equals("TRANSFER")&&(e.from()==null||e.to()==null||e.from().equals(e.to())))||(e.kind().equals("MINT")&&(e.from()!=null||e.to()==null))||(e.kind().equals("BURN")&&(e.from()==null||e.to()!=null)))throw new IllegalArgumentException("Invalid endpoints");
        }
    }
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
    private Receipt change(String kind,UUID actor,UUID from,UUID to,long amount,String reason){
        if(locked)return reject("Economy storage locked");
        if(actor==null||amount<=0||amount>MAX_BALANCE||!validReason(reason)||from!=null&&from.equals(to)||state.sequence==Long.MAX_VALUE)return reject("Invalid transaction");
        if(kind.equals("TRANSFER")&&(from==null||to==null)||kind.equals("MINT")&&to==null||kind.equals("BURN")&&from==null)return reject("Invalid accounts");
        long debit=from==null?0:balance(from),credit=to==null?0:balance(to);
        if(from!=null&&debit<amount)return reject("Insufficient gold");
        if(to!=null&&credit>MAX_BALANCE-amount)return reject("Balance limit exceeded");
        var next=new State();
        next.sequence=state.sequence+1;next.balances.putAll(state.balances);next.history.addAll(state.history);
        if(from!=null)next.balances.put(from.toString(),debit-amount);
        if(to!=null)next.balances.put(to.toString(),credit+amount);
        next.history.add(new Entry(next.sequence,System.currentTimeMillis(),kind,actor.toString(),from==null?null:from.toString(),to==null?null:to.toString(),amount,reason));
        if(next.history.size()>1000)next.history.remove(0);
        try{
            persist(next);
            state=next;
            return new Receipt(true,"Committed",next.sequence);
        }catch(IOException|RuntimeException ex){
            locked=true;errors.accept(ex);return reject("Storage error; economy locked");
        }
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
