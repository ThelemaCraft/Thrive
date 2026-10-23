package com.thelema.thrive.mixin;

import com.thelema.thrive.client.sound.juice.JuiceSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AmbientSoundHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Shadow @Final private List<AmbientSoundHandler> ambientSoundHandlers;

    /** 构造器 RETURN 时原版三个 handler 已经 add 完，这里追加我们的。 */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void thrive$addJuiceHandler(CallbackInfo ci) {
        this.ambientSoundHandlers.add(new JuiceSound(
                (LocalPlayer) (Object) this,
                Minecraft.getInstance().getSoundManager()));
    }
}