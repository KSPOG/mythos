package com.npcarchitects;
public final class WorkCadenceTest {
    public static void main(String[] args){
        if(WorkCadence.interval(0)!=2||WorkCadence.interval(100)!=20||WorkCadence.interval(4)!=4)throw new AssertionError("clamping");
        for(int interval=2;interval<=20;interval++)for(int seconds:new int[]{1,15,60,3600}){
            int attempts=WorkCadence.attempts(seconds,interval);
            if(attempts*interval<seconds*20||(attempts-1)*interval>=seconds*20)throw new AssertionError("timeout conversion");
        }
        if(WorkCadence.attempts(15,4)!=75||WorkCadence.attempts(60,4)!=300)throw new AssertionError("default timeouts");
        System.out.println("Work cadence and timeout conversion passed");
    }
}
