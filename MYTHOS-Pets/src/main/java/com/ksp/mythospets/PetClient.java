package com.ksp.mythospets;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "mythospets", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PetClient {
    private static final ModelLayerLocation LAYER = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath("mythospets", "mooncat"), "main");

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER, MooncatModel::createLayer);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PetEntities.MOONCAT.get(), MooncatRenderer::new);
    }

    private static final class MooncatRenderer extends MobRenderer<MooncatEntity, MooncatModel> {
        private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("mythospets", "textures/entity/mooncat.png");

        MooncatRenderer(EntityRendererProvider.Context context) {
            super(context, new MooncatModel(context.bakeLayer(LAYER)), 0.3F);
        }

        @Override public ResourceLocation getTextureLocation(MooncatEntity entity) { return TEXTURE; }
    }

    private static final class MooncatModel extends HierarchicalModel<MooncatEntity> {
        private final ModelPart root, head, tail;
        private final ModelPart[] legs = new ModelPart[4];

        MooncatModel(ModelPart root) {
            this.root = root;
            head = root.getChild("head");
            tail = root.getChild("tail");
            for (int i = 0; i < legs.length; i++) legs[i] = root.getChild("leg" + i);
        }

        static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3, -4, -5, 6, 7, 11)
                .texOffs(0, 20).addBox(-3.5F, -4.5F, -4, 7, 3, 7),
                PartPose.offset(0, 16, 1));
            PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-4, -5, -4, 8, 7, 7)
                .texOffs(32, 16).addBox(-2, -1, -5, 4, 3, 2),
                PartPose.offset(0, 13, -5));
            head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-1.5F, -4, -1, 3, 4, 2), PartPose.offsetAndRotation(2.8F, -4, 0, 0, 0, 0.18F));
            head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-1.5F, -4, -1, 3, 4, 2), PartPose.offsetAndRotation(-2.8F, -4, 0, 0, 0, -0.18F));
            head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(20, 32)
                .addBox(-1, -1, -1, 2, 2, 2), PartPose.offsetAndRotation(0, -3, -4, 0, 0, 0.785F));
            for (int i = 0; i < 4; i++) {
                float x = i % 2 == 0 ? -2F : 2F;
                float z = i < 2 ? -2F : 5F;
                root.addOrReplaceChild("leg" + i, CubeListBuilder.create().texOffs(48, 32)
                    .addBox(-1, 0, -1, 2, 6, 2)
                    .texOffs(48, 44).addBox(-1.5F, 5, -2, 3, 1, 3), PartPose.offset(x, 18, z));
            }
            PartDefinition tail = root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 48)
                .addBox(-1, -1, 0, 2, 2, 7), PartPose.offsetAndRotation(0, 15, 6, 0.45F, 0, 0));
            tail.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(20, 48)
                .addBox(-1.5F, -1.5F, 0, 3, 3, 4), PartPose.offsetAndRotation(0, 0, 6, 0.4F, 0, 0));
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override public ModelPart root() { return root; }

        @Override public void setupAnim(MooncatEntity entity, float limbSwing, float limbSwingAmount,
                                        float ageInTicks, float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            head.yRot = Mth.clamp(netHeadYaw, -45, 45) * Mth.DEG_TO_RAD;
            head.xRot = Mth.clamp(headPitch, -30, 30) * Mth.DEG_TO_RAD;
            tail.yRot = Mth.sin(ageInTicks * 0.11F) * 0.25F;
            for (int i = 0; i < legs.length; i++) {
                float phase = (i == 0 || i == 3) ? 0 : Mth.PI;
                legs[i].xRot = Mth.cos(limbSwing * 0.8F + phase) * 0.65F * limbSwingAmount;
            }
        }
    }
}
