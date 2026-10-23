package com.thelema.thrive.client.model.entity;

import com.google.common.collect.ImmutableList;
import com.thelema.thrive.ThriveMod;
import com.thelema.thrive.generic.entity.GardenBee;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.ModelUtils;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GardenBeeModel<T extends GardenBee> extends AgeableListModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ThriveMod.MOD_ID, "garden_bee"), "main");

    /** 翅膀扇动角速度（度/tick）。原版 Bee 值。 */
    private static final float FLAP_DEGREES_PER_TICK = GardenBee.FLAP_DEGREES_PER_TICK;

    private static final float BEE_Y_BASE = 19.0F;
    private static final String BONE = "bone";
    private static final String STINGER = "stinger";
    private static final String LEFT_ANTENNA = "left_antenna";
    private static final String RIGHT_ANTENNA = "right_antenna";
    private static final String FRONT_LEGS = "front_legs";
    private static final String MIDDLE_LEGS = "middle_legs";
    private static final String BACK_LEGS = "back_legs";

    private final ModelPart bone;
    private final ModelPart rightWing;
    private final ModelPart leftWing;
    private final ModelPart frontLeg;
    private final ModelPart midLeg;
    private final ModelPart backLeg;
    private final ModelPart leftAntenna;
    private final ModelPart rightAntenna;
    private float rollAmount;

    public GardenBeeModel(ModelPart root) {
        super(false, 24.0F, 0.0F);
        this.bone = root.getChild(BONE);
        ModelPart body = this.bone.getChild("body");
        this.leftAntenna = body.getChild(LEFT_ANTENNA);
        this.rightAntenna = body.getChild(RIGHT_ANTENNA);
        this.rightWing = this.bone.getChild("right_wing");
        this.leftWing = this.bone.getChild("left_wing");
        this.frontLeg = this.bone.getChild(FRONT_LEGS);
        this.midLeg = this.bone.getChild(MIDDLE_LEGS);
        this.backLeg = this.bone.getChild(BACK_LEGS);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition bone = root.addOrReplaceChild(BONE,
                CubeListBuilder.create(), PartPose.offset(0.0F, BEE_Y_BASE, 0.0F));
        PartDefinition body = bone.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3.5F, -4.0F, -5.0F, 7.0F, 7.0F, 10.0F),
                PartPose.ZERO);
        body.addOrReplaceChild(STINGER,
                CubeListBuilder.create().texOffs(26, 7)
                        .addBox(0.0F, -1.0F, 5.0F, 0.0F, 1.0F, 2.0F),
                PartPose.ZERO);
        body.addOrReplaceChild(LEFT_ANTENNA,
                CubeListBuilder.create().texOffs(2, 0)
                        .addBox(1.5F, -2.0F, -3.0F, 1.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, -2.0F, -5.0F));
        body.addOrReplaceChild(RIGHT_ANTENNA,
                CubeListBuilder.create().texOffs(2, 3)
                        .addBox(-2.5F, -2.0F, -3.0F, 1.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, -2.0F, -5.0F));

        CubeDeformation inflate = new CubeDeformation(0.001F);
        bone.addOrReplaceChild("right_wing",
                CubeListBuilder.create().texOffs(0, 18)
                        .addBox(-9.0F, 0.0F, 0.0F, 9.0F, 0.0F, 6.0F, inflate),
                PartPose.offsetAndRotation(-1.5F, -4.0F, -3.0F, 0.0F, -0.2618F, 0.0F));
        bone.addOrReplaceChild("left_wing",
                CubeListBuilder.create().texOffs(0, 18).mirror()
                        .addBox(0.0F, 0.0F, 0.0F, 9.0F, 0.0F, 6.0F, inflate),
                PartPose.offsetAndRotation(1.5F, -4.0F, -3.0F, 0.0F, 0.2618F, 0.0F));
        bone.addOrReplaceChild(FRONT_LEGS,
                CubeListBuilder.create().addBox(FRONT_LEGS, -5.0F, 0.0F, 0.0F, 7, 2, 0, 26, 1),
                PartPose.offset(1.5F, 3.0F, -2.0F));
        bone.addOrReplaceChild(MIDDLE_LEGS,
                CubeListBuilder.create().addBox(MIDDLE_LEGS, -5.0F, 0.0F, 0.0F, 7, 2, 0, 26, 3),
                PartPose.offset(1.5F, 3.0F, 0.0F));
        bone.addOrReplaceChild(BACK_LEGS,
                CubeListBuilder.create().addBox(BACK_LEGS, -5.0F, 0.0F, 0.0F, 7, 2, 0, 26, 5),
                PartPose.offset(1.5F, 3.0F, 2.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void prepareMobModel(T entity, float limbSwing, float limbSwingAmount, float partialTick) {
        super.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        this.rollAmount = entity.getRollAmount(partialTick);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.rightWing.xRot = 0.0F;
        this.leftAntenna.xRot = 0.0F;
        this.rightAntenna.xRot = 0.0F;
        this.bone.xRot = 0.0F;

        boolean resting = entity.onGround() && entity.getDeltaMovement().lengthSqr() < 1.0E-7;
        if (resting) {
            this.rightWing.yRot = -0.2618F;
            this.rightWing.zRot = 0.0F;
            this.leftWing.xRot = 0.0F;
            this.leftWing.yRot = 0.2618F;
            this.leftWing.zRot = 0.0F;
            this.frontLeg.xRot = 0.0F;
            this.midLeg.xRot = 0.0F;
            this.backLeg.xRot = 0.0F;
        } else {
            float flap = ageInTicks * FLAP_DEGREES_PER_TICK * (float) (Math.PI / 180.0);
            this.rightWing.yRot = 0.0F;
            this.rightWing.zRot = Mth.cos(flap) * (float) Math.PI * 0.15F;
            this.leftWing.xRot = this.rightWing.xRot;
            this.leftWing.yRot = this.rightWing.yRot;
            this.leftWing.zRot = -this.rightWing.zRot;
            this.frontLeg.xRot = (float) (Math.PI / 4);
            this.midLeg.xRot = (float) (Math.PI / 4);
            this.backLeg.xRot = (float) (Math.PI / 4);
            this.bone.xRot = 0.0F;
            this.bone.yRot = 0.0F;
            this.bone.zRot = 0.0F;
        }

        if (!entity.isAngry()) {
            this.bone.xRot = 0.0F;
            this.bone.yRot = 0.0F;
            this.bone.zRot = 0.0F;
            if (!resting) {
                float bob = Mth.cos(ageInTicks * 0.18F);
                this.bone.xRot = 0.1F + bob * (float) Math.PI * 0.025F;
                this.leftAntenna.xRot = bob * (float) Math.PI * 0.03F;
                this.rightAntenna.xRot = bob * (float) Math.PI * 0.03F;
                this.frontLeg.xRot = -bob * (float) Math.PI * 0.1F + (float) (Math.PI / 8);
                this.backLeg.xRot = -bob * (float) Math.PI * 0.05F + (float) (Math.PI / 4);
                this.bone.y = BEE_Y_BASE - Mth.cos(ageInTicks * 0.18F) * 0.9F;
            }
        }

        if (this.rollAmount > 0.0F) {
            this.bone.xRot = ModelUtils.rotlerpRad(this.bone.xRot, 3.0915928F, this.rollAmount);
        }
    }

    @Override
    protected Iterable<ModelPart> headParts() {
        return ImmutableList.of();
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(this.bone);
    }
}