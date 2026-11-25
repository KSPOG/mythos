package com.ksp.serverrestart;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

record BroadcastConfig(String sender,String scheduled,String warning,String cancelled,String restarting,String aborted,String bossbar,long[] warningSeconds) {
    private static final String DEFAULT_FILE="""
        # Server Restart broadcast settings. UTF-8.
        # Minecraft formatting: &0-&9, &a-&f, &k-&o, &r. Use && for literal ampersand.
        # Placeholders: {sender}, {time} (HH:mm:ss), {seconds}.
        # Empty message disables that broadcast.
        sender=&6[Server]&r
        scheduled={sender} &eA server restart has been scheduled in &c{time}&e.
        warning={sender} &eServer restart in &c{time}&e.
        cancelled={sender} &aThe scheduled restart has been cancelled.
        restarting={sender} &cServer restarting now. Saving the world...
        aborted={sender} &cRestart aborted. The server remains online.
        bossbar=Server restart in {time}
        warningSeconds=300,60,30,10,5,4,3,2,1
        """;
    static BroadcastConfig defaults() {
        try { var p=new Properties(); p.load(new java.io.StringReader(DEFAULT_FILE)); return parse(p); }
        catch(IOException ex) { throw new IllegalStateException(ex); }
    }
    static BroadcastConfig load(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if(Files.notExists(path)) {
            try { Files.writeString(path,DEFAULT_FILE,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW); }
            catch(FileAlreadyExistsException ignored) {}
        }
        var p=new Properties();
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) { p.load(reader); }
        return parse(p);
    }
    private static BroadcastConfig parse(Properties p) throws IOException {
        var d=new Properties(); d.load(new java.io.StringReader(DEFAULT_FILE));
        for(String key:d.stringPropertyNames()) {
            if(!p.containsKey(key)) p.setProperty(key,d.getProperty(key));
            if(p.getProperty(key).length()>2048) throw new IOException("Setting too long: "+key);
        }
        var times=new TreeSet<Long>(Comparator.reverseOrder());
        String raw=p.getProperty("warningSeconds").trim();
        if(!raw.isEmpty()) for(String item:raw.split(",",-1)) {
            try { long n=Long.parseLong(item.trim()); if(n<1||n>365L*86400) throw new NumberFormatException(); times.add(n); }
            catch(NumberFormatException ex) { throw new IOException("Invalid warningSeconds entry: "+item); }
        }
        return new BroadcastConfig(p.getProperty("sender"),p.getProperty("scheduled"),p.getProperty("warning"),p.getProperty("cancelled"),p.getProperty("restarting"),p.getProperty("aborted"),p.getProperty("bossbar"),times.stream().mapToLong(Long::longValue).toArray());
    }
    String message(String event) {
        return switch(event) {case "scheduled"->scheduled;case "warning"->warning;case "cancelled"->cancelled;case "restarting"->restarting;case "aborted"->aborted;default->throw new IllegalArgumentException("Unknown event");};
    }
    String render(String template,long seconds) {
        return template.replace("{sender}",sender).replace("{time}",DurationParser.format(seconds)).replace("{seconds}",Long.toString(seconds));
    }
}
