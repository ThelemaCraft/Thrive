package com.thelema.thrive.generic.blockentity.modifier;

import com.thelema.thrive.generic.blockentity.BiomeCoreBlockEntity;

@FunctionalInterface
public interface PosFinder {
    /** 遍历坐标推给 apply，返回 true = 本次遍历已到末尾。 */
    boolean find(BiomeCoreBlockEntity be, PosApplier apply);
}