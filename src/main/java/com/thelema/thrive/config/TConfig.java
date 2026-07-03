package com.thelema.thrive.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;


public class TConfig {
    public static final ModConfigSpec SPEC;
    public static final TConfig CONFIG;

    static {
        Pair<TConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(TConfig::new);
        SPEC = pair.getRight();
        CONFIG = pair.getLeft();
    }

    public final ModConfigSpec.ConfigValue<List<? extends String>> mossFrameEmptyBlackList;

    TConfig(ModConfigSpec.Builder builder) {

        builder.push("moss_frame_empty");

        mossFrameEmptyBlackList = builder.defineList(
                "black_list",
                List.of(
                        "minecraft:air",
                        "minecraft:bedrock",
                        "minecraft:end_portal_frame",
                        "minecraft:command_block",
                        "minecraft:structure_block",
                        "minecraft:barrier",
                        "minecraft:jigsaw"
                ),
                () -> "minecraft:air",
                o -> o instanceof String && !((String) o).isEmpty()
        );

        builder.pop();

    }
}