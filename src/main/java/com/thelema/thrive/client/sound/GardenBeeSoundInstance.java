package com.thelema.thrive.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * 花园蜂的循环飞行音效，逻辑照搬原版 BeeSoundInstance。
 * 传入 angry 判定时，会在愤怒状态变化时无缝切换到 aggressive 版本。
 */
@OnlyIn(Dist.CLIENT)
public class GardenBeeSoundInstance extends AbstractTickableSoundInstance {

    private final Mob bee;
    private final float minPitch;
    private final float maxPitch;
    private final boolean aggressive;
    @Nullable private final BooleanSupplier angry;
    private boolean hasSwitched;

    public GardenBeeSoundInstance(Mob bee, boolean aggressive, @Nullable BooleanSupplier angry) {
        super(aggressive ? SoundEvents.BEE_LOOP_AGGRESSIVE : SoundEvents.BEE_LOOP,
                SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.bee = bee;
        this.aggressive = aggressive;
        this.angry = angry;
        this.x = bee.getX();
        this.y = bee.getY();
        this.z = bee.getZ();
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.minPitch = bee.isBaby() ? 1.1F : 0.7F;
        this.maxPitch = bee.isBaby() ? 1.5F : 1.1F;
    }

    @Override
    public void tick() {
        if (this.bee.isRemoved()) {
            this.stop();
            return;
        }

        // 愤怒状态变了：排队播放另一版循环，本实例停止
        if (this.angry != null && !this.hasSwitched) {
            boolean nowAngry = this.angry.getAsBoolean();
            if (nowAngry != this.aggressive) {
                Minecraft.getInstance().getSoundManager().queueTickingSound(
                        new GardenBeeSoundInstance(this.bee, nowAngry, this.angry));
                this.hasSwitched = true;
            }
        }
        if (this.hasSwitched) {
            this.stop();
            return;
        }

        this.x = this.bee.getX();
        this.y = this.bee.getY();
        this.z = this.bee.getZ();

        float speed = (float) this.bee.getDeltaMovement().horizontalDistance();
        if (speed >= 0.01F) {
            this.pitch = Mth.lerp(Mth.clamp(speed, this.minPitch, this.maxPitch), this.minPitch, this.maxPitch);
            this.volume = Mth.lerp(Mth.clamp(speed, 0.0F, 0.5F), 0.0F, 1.2F);
        } else {
            this.pitch = 0.0F;
            this.volume = 0.0F;
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean canPlaySound() {
        return !this.bee.isSilent();
    }
}
