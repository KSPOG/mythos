package com.ksp.mythoscharacters;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;

final class CharacterSettingsScreen extends Screen {
    private final Screen parent;
    CharacterSettingsScreen(Screen parent){super(Component.literal("MYTHOS Characters"));this.parent=parent;}
    @Override protected void init(){
        int x=width/2-105,y=height/2-70;
        addRenderableWidget(Button.builder(Component.literal("Quality: "+CharacterOptions.quality),b->{
            var values=CharacterOptions.Quality.values();CharacterOptions.quality=values[(CharacterOptions.quality.ordinal()+1)%values.length];b.setMessage(Component.literal("Quality: "+CharacterOptions.quality));
        }).bounds(x,y,210,20).build());
        addRenderableWidget(Button.builder(Component.literal("Body: "+CharacterOptions.physique),b->{
            var values=CharacterOptions.Physique.values();CharacterOptions.physique=values[(CharacterOptions.physique.ordinal()+1)%values.length];b.setMessage(Component.literal("Body: "+CharacterOptions.physique));
        }).bounds(x,y+24,210,20).build());
        addRenderableWidget(Button.builder(Component.literal("Hair volume: "+CharacterOptions.hair),b->{CharacterOptions.hair=!CharacterOptions.hair;b.setMessage(Component.literal("Hair volume: "+CharacterOptions.hair));}).bounds(x,y+48,210,20).build());
        addRenderableWidget(Button.builder(Component.literal("Secondary motion: "+CharacterOptions.motion),b->{CharacterOptions.motion=!CharacterOptions.motion;b.setMessage(Component.literal("Secondary motion: "+CharacterOptions.motion));}).bounds(x,y+72,210,20).build());
        addRenderableWidget(Button.builder(Component.literal("Custom renderer: "+CharacterOptions.enabled),b->{CharacterOptions.enabled=!CharacterOptions.enabled;b.setMessage(Component.literal("Custom renderer: "+CharacterOptions.enabled));}).bounds(x,y+96,210,20).build());
        addRenderableWidget(Button.builder(Component.literal("Save and close"),b->onClose()).bounds(x,y+124,210,20).build());
    }
    @Override public void onClose(){CharacterOptions.save();if(minecraft!=null)minecraft.setScreen(parent);}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
        renderBackground(graphics,mouseX,mouseY,partialTick);
        graphics.drawCenteredString(font,title,width/2,height/2-98,0xffffff);
        super.render(graphics,mouseX,mouseY,partialTick);
    }
}
