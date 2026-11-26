package com.ksp.mythosclasses;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/** Vanilla team suffixes appear above players and in the tab list. */
final class LevelNameplates {
    private static final String PREFIX="mythos_lv_";
    private static final String OWNER="MYTHOS Classes level ";
    private LevelNameplates(){}
    private static String id(int level){return PREFIX+level;}
    private static String title(int level){return OWNER+level;}
    private static boolean owned(PlayerTeam team){
        if(team==null||!team.getName().startsWith(PREFIX))return false;
        try{
            int l=Integer.parseInt(team.getName().substring(PREFIX.length()));
            return l>=1&&l<=ClassRules.MAX_LEVEL&&team.getName().equals(id(l))
                &&team.getDisplayName().getString().equals(title(l));
        }catch(NumberFormatException e){return false;}
    }
    static void refresh(ServerPlayer player){
        Scoreboard board=player.serverLevel().getScoreboard();
        String name=player.getScoreboardName();
        PlayerTeam current=board.getPlayersTeam(name);
        var progress=ClassApi.progression(player.getUUID());
        if(progress.isEmpty()){
            if(owned(current))board.removePlayerFromTeam(name,current);
            return;
        }
        // A player can belong to only one vanilla team. Never replace a clan,
        // rank or administrator-managed team or modify its shared suffix.
        if(current!=null&&!owned(current))return;
        int level=progress.get().level();
        PlayerTeam target=board.getPlayerTeam(id(level));
        if(target!=null&&!owned(target)){
            if(owned(current))board.removePlayerFromTeam(name,current);
            return;
        }
        if(target==null){
            target=board.addPlayerTeam(id(level));
            target.setDisplayName(Component.literal(title(level)));
        }
        Component suffix=Component.literal(" [Lv. "+level+"]").withStyle(ChatFormatting.GRAY);
        if(!target.getPlayerSuffix().equals(suffix))target.setPlayerSuffix(suffix);
        if(current!=target)board.addPlayerToTeam(name,target);
    }
    static void refreshAll(MinecraftServer server){
        for(ServerPlayer player:server.getPlayerList().getPlayers())refresh(player);
    }
    static void cleanup(MinecraftServer server){
        Scoreboard board=server.overworld().getScoreboard();
        for(PlayerTeam team:java.util.List.copyOf(board.getPlayerTeams()))
            if(owned(team))board.removePlayerTeam(team);
    }
}
