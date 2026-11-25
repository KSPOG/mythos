package com.ksp.serverrestart;
import java.nio.file.*;
import java.util.Arrays;
public class BroadcastConfigTest {
    public static void main(String[] args) throws Exception {
        var dir=Files.createTempDirectory("restart-config-test");
        var file=dir.resolve("serverrestart.properties");
        try {
            var c=BroadcastConfig.load(file);
            if(!Files.exists(file)||!c.render(c.scheduled(),3000).contains("00:50:00")) throw new AssertionError("defaults");
            Files.writeString(file,"sender=&b[MYTHOS]\nscheduled={sender} {seconds}\nwarningSeconds=1,60,60,5\ncancelled=\n");
            c=BroadcastConfig.load(file);
            if(!c.render(c.scheduled(),3000).equals("&b[MYTHOS] 3000")||!Arrays.equals(c.warningSeconds(),new long[]{60,5,1})||!c.cancelled().isEmpty()) throw new AssertionError("custom config");
            Files.writeString(file,"warningSeconds=-1");
            try { BroadcastConfig.load(file); throw new AssertionError("invalid warning accepted"); } catch(java.io.IOException expected) {}
            System.out.println("Broadcast config tests passed");
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(dir); }
    }
}
