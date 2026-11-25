package com.npcarchitects;
final class WorkCadence {
    private WorkCadence(){}
    static int interval(int ticks){return Math.max(2,Math.min(20,ticks));}
    static int attempts(int seconds,int interval){return (seconds*20+interval-1)/interval;}
}
