package com.ksp.serverrestart;
public class DurationTest {
    public static void main(String[] args) {
        check("50m",3000); check("1h30m",5400); check("1d2h3m4s",93784); check("30s",30);
        for(String s:new String[]{"","0s","50","-5m","1m2h","1m1m","1hX","999999999999999999999h","366d"}) {
            try { DurationParser.seconds(s); throw new AssertionError("Accepted "+s); } catch(IllegalArgumentException expected) {}
        }
        if(!DurationParser.format(3000).equals("00:50:00")) throw new AssertionError("Format");
        System.out.println("Duration tests passed");
    }
    private static void check(String input,long expected) { if(DurationParser.seconds(input)!=expected) throw new AssertionError(input); }
}
