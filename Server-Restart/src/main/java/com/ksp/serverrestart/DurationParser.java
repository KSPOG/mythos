package com.ksp.serverrestart;

import java.util.Locale;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern PART = Pattern.compile("([0-9]+)([dhms])");
    private DurationParser() {}
    public static long seconds(String input) {
        String value=input.toLowerCase(Locale.ROOT);
        var matcher=PART.matcher(value);
        long total=0; int end=0, previous=4;
        while(matcher.find()) {
            if(matcher.start()!=end) throw new IllegalArgumentException("Use durations like 50m, 1h30m or 30s.");
            int rank="smhd".indexOf(matcher.group(2));
            if(rank>=previous) throw new IllegalArgumentException("Use each unit once, largest first.");
            previous=rank;
            long multiplier=switch(rank) { case 0->1;case 1->60;case 2->3600;case 3->86400;default->throw new IllegalArgumentException(); };
            try { total=Math.addExact(total,Math.multiplyExact(Long.parseLong(matcher.group(1)),multiplier)); }
            catch(ArithmeticException | NumberFormatException ex) { throw new IllegalArgumentException("Duration is too large."); }
            end=matcher.end();
        }
        if(end!=value.length()||total<1||total>365L*86400) throw new IllegalArgumentException("Duration must be between 1s and 365d.");
        return total;
    }
    public static String format(long seconds) {
        long d=seconds/86400,h=seconds/3600%24,m=seconds/60%60,s=seconds%60;
        return (d>0?d+"d ":"")+String.format(Locale.ROOT,"%02d:%02d:%02d",h,m,s);
    }
}
