package com.ksp.mythoscharacters;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import com.mojang.logging.LogUtils;
@Mod.EventBusSubscriber(modid=MythosCharacters.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class CharacterClient {
    private CharacterClient(){}
    @SubscribeEvent
    public static void install(EntityRenderersEvent.AddLayers event){
        CharacterOptions.load();
        if(Boolean.getBoolean("mythoscharacters.disable"))return;
        try{
            var field=ObfuscationReflectionHelper.findField(LivingEntityRenderer.class,"f_115290_");
            for(var skin:event.getSkins()){
                PlayerRenderer renderer=event.getPlayerSkin(skin);
                if(renderer==null)continue;
                boolean slim=skin==PlayerSkin.Model.SLIM;
                var root=event.getEntityModels().bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER);
                field.set(renderer,new CinematicPlayerModel(root,slim));
            }
            LogUtils.getLogger().info("MYTHOS Characters: installed cinematic player models");
        }catch(ReflectiveOperationException|RuntimeException e){
            LogUtils.getLogger().error("MYTHOS Characters: player model replacement failed; existing renderer retained where possible",e);
        }
    }
}
