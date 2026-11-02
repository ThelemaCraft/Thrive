package com.thelema.thrive.generic.block;

import com.thelema.thrive.generic.block.other.CloudLightHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class Cloud extends HalfTransparentBlock {

    public Cloud() {
        super(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL)
                .noOcclusion()   // 不剔除相邻面、不阻挡视线、不窒息
                .lightLevel(v -> 0)); // 不遮挡光照，Mixin 扫描可穿透
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        CloudLightHelper.invalidate(pos.getX(), pos.getZ());
        // setBlock 已自动 checkBlock(pos)，会重算本列并向外传播
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        CloudLightHelper.invalidate(pos.getX(), pos.getZ());
        super.onRemove(state, level, pos, next, moved);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;   // 告诉光照引擎：天空光可以穿过这一格继续向下
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;   // 不产生环境光遮蔽（AO），相邻面不会被压暗
    }
}
