package com.npcarchitects;
import java.nio.file.*;
import java.util.*;
public final class ProgressJournalTest{
    public static void main(String[] args)throws Exception{
        Path path=Files.createTempFile("architect-journal",".log");
        try{
            try(var j=new ProgressJournal(path)){j.append("first");j.append("second");j.force();}
            long valid=Files.size(path);
            Files.write(path,new byte[]{0,0,0,12,0,0,0,0,1},StandardOpenOption.APPEND);
            var seen=new ArrayList<String>();
            try(var j=new ProgressJournal(path)){j.replay(seen::add);if(!seen.equals(List.of("first","second"))||Files.size(path)!=valid)throw new AssertionError("Torn tail");j.append("third");j.force();}
            seen.clear();
            try(var j=new ProgressJournal(path)){j.replay(seen::add);if(!seen.equals(List.of("first","second","third")))throw new AssertionError("Replay");j.reset();}
            if(Files.size(path)!=0)throw new AssertionError("Reset");
            try(var j=new ProgressJournal(path)){j.append("bad");j.force();}
            byte[] broken=Files.readAllBytes(path);broken[broken.length-1]^=1;Files.write(path,broken);
            try(var j=new ProgressJournal(path)){try{j.replay(x->{});throw new AssertionError("Corruption accepted");}catch(java.io.IOException expected){}}
            System.out.println("Journal replay, torn tail, reset and corruption tests passed");
        }finally{Files.deleteIfExists(path);}
    }
}
