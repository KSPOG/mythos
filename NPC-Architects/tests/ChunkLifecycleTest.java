package com.npcarchitects;
import java.util.*;
/** Exercise the durable contiguous-prefix plus out-of-order representation across mixed phases. */
public final class ChunkLifecycleTest {
    static int record(Set<Integer> done,int cursor,int index){if(index<cursor)return cursor;done.add(index);while(done.remove(cursor))cursor++;return cursor;}
    public static void main(String[] args){
        Set<Integer> cleared=new HashSet<>(),built=new HashSet<>();int clear=0,build=0;
        // Chunk B clears and builds before chunk A finishes clearing.
        for(int i:new int[]{2,3,6,7})clear=record(cleared,clear,i);
        for(int i:new int[]{1,3})build=record(built,build,i);
        if(clear!=0||build!=0||cleared.size()!=4||built.size()!=2)throw new AssertionError("Out-of-order progress");
        // Simulate persisted state reconstruction.
        cleared=new HashSet<>(cleared);built=new HashSet<>(built);
        for(int i:new int[]{0,1,4,5})clear=record(cleared,clear,i);
        for(int i:new int[]{0,2})build=record(built,build,i);
        if(clear!=8||build!=4||!cleared.isEmpty()||!built.isEmpty())throw new AssertionError("Progress merge");
        System.out.println("Mixed chunk phase progress reconstruction passed");
    }
}
