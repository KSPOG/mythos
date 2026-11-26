package com.ksp.mythospets;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "mythospets", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PetClient {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PetEntities.DRAGON.get(), DragonRenderer::new);
        event.registerEntityRenderer(PetEntities.MOONCAT.get(), MooncatRenderer::new);
    }

    private static final class DragonRenderer extends MobRenderer<DragonEntity, DragonMeshModel> {
        DragonRenderer(EntityRendererProvider.Context context) {
            super(context, new DragonMeshModel(), 0.4F);
        }
        @Override public ResourceLocation getTextureLocation(DragonEntity entity) {
            return ResourceLocation.fromNamespaceAndPath("mythospets", "textures/entity/ember_dragon.png");
        }
    }

    private static final class MooncatRenderer extends MobRenderer<MooncatEntity, MooncatMeshModel> {
        private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("mythospets", "textures/entity/mooncat.png");

        MooncatRenderer(EntityRendererProvider.Context context) {
            super(context, new MooncatMeshModel(), 0.3F);
        }

        @Override public ResourceLocation getTextureLocation(MooncatEntity entity) { return TEXTURE; }
    }
}
