package com.thelema.thrive.client.sound.juice;

import com.thelema.thrive.register.FluidRegister;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.AmbientSoundHandler;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 复刻 UnderwaterAmbientSoundHandler：循环底噪 + 概率气泡。 */
@OnlyIn(Dist.CLIENT)
public class JuiceSound implements AmbientSoundHandler {

    private final LocalPlayer player;
    private final SoundManager soundManager;
    private boolean wasInJuice;

    public JuiceSound(LocalPlayer player, SoundManager soundManager) {
        this.player = player;
        this.soundManager = soundManager;
    }

    @Override
    public void tick() {
        boolean inJuice = this.player.getFluidTypeHeight(
                FluidRegister.LANTERN_BERRY_JUICE_TYPE.get()) > 0.0;

        float rand = this.player.getRandom().nextFloat();
        // 进入瞬间起循环底噪，实例自己淡入淡出
        if (inJuice && !this.wasInJuice) {
            this.soundManager.play(new JuiceAmbientSoundInstance(this.player));
            this.player.level().playLocalSound(this.player.getX(), this.player.getY(), this.player.getZ(),
                    SoundEvents.GENERIC_SPLASH, SoundSource.AMBIENT, 0.35F, 1.0F + rand * 0.3F, false);
        } else if (!inJuice && this.wasInJuice) {
            this.player.level().playLocalSound(this.player.getX(), this.player.getY(), this.player.getZ(),
                    SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.AMBIENT, 0.3F, 1.4F + rand * 0.2F, false);
        }
        this.wasInJuice = inJuice;
        if (!inJuice) return;

        // 概率气泡：三层概率合并为一次比较
        float f = this.player.level().random.nextFloat();
        SoundEvent evt = f < 1.0E-4F ? SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE
                       : f < 1.0E-3F ? SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_RARE
                       : f < 0.01F   ? SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS
                       : null;
        if (evt != null) this.soundManager.play(new JuiceSubSound(this.player, evt));
    }

    /** 果汁音效基类：管理玩家引用、位置跟随、离液判据。子类只管 volume/pitch 和 stop 策略。 */
    @OnlyIn(Dist.CLIENT)
    public abstract static class JuiceSoundInstance extends AbstractTickableSoundInstance {

        protected final Player player;

        protected JuiceSoundInstance(Player player, SoundEvent sound, boolean looping) {
            super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = looping;
            this.delay = 0;
            updatePosition();
        }

        @Override
        public final void tick() {
            if (this.player.isRemoved()) { stop(); return; }
            updatePosition();
            onTick(inJuice());
        }

        /** 子类：本 tick 音量/音调更新 + stop 判断。 */
        protected abstract void onTick(boolean inJuice);

        protected final boolean inJuice() {
            return this.player.getFluidTypeHeight(FluidRegister.LANTERN_BERRY_JUICE_TYPE.get()) > 0.0;
        }

        private void updatePosition() {
            this.x = this.player.getX();
            this.y = this.player.getY();
            this.z = this.player.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    /** 一次性气泡：播完自动出列；离液立即 stop。 */
    @OnlyIn(Dist.CLIENT)
    public static class JuiceSubSound extends JuiceSoundInstance {

        public JuiceSubSound(Player player, SoundEvent sound) {
            super(player, sound, false);
            this.relative = true;
            this.volume = 1.0F;
        }

        @Override
        protected void onTick(boolean inJuice) {
            if (!inJuice) stop();
        }
    }

    /** 循环底噪。淡入 +1/tick、淡出 -2/tick，fade 跌破 0 才 stop（原版水下循环同款）。 */
    @OnlyIn(Dist.CLIENT)
    public static class JuiceAmbientSoundInstance extends JuiceSoundInstance {

        public static final int FADE_DURATION = 40;
        private static final float BASE_VOLUME = 0.6F;

        private int fade;

        public JuiceAmbientSoundInstance(Player player) {
            super(player, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, true);
            this.pitch = 0.8F;
        }

        @Override
        protected void onTick(boolean inJuice) {
            this.fade += inJuice ? 1 : -2;
            this.fade = Math.min(this.fade, FADE_DURATION);
            if (this.fade < 0) { stop(); return; }
            this.volume = BASE_VOLUME * (float) this.fade / FADE_DURATION;
        }
    }
}