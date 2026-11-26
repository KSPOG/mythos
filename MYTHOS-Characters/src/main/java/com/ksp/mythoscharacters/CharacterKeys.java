package com.ksp.mythoscharacters;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid=MythosCharacters.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class CharacterKeys {
    private static final KeyMapping SETTINGS=new KeyMapping("key.mythoscharacters.settings",GLFW.GLFW_KEY_K,"key.categories.mythoscharacters");
    @SubscribeEvent public static void register(RegisterKeyMappingsEvent e){e.register(SETTINGS);}
    @Mod.EventBusSubscriber(modid=MythosCharacters.ID,bus=Mod.EventBusSubscriber.Bus.FORGE,value=Dist.CLIENT)
    public static final class Input {
        @SubscribeEvent public static void key(InputEvent.Key e){
            var mc=Minecraft.getInstance();
            if(SETTINGS.consumeClick()&&mc.player!=null&&mc.screen==null)mc.setScreen(new CharacterSettingsScreen(null));
        }
    }
}
