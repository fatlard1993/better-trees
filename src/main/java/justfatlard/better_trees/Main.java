package justfatlard.better_trees;

import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.LinkedHashMap;
import java.util.Map;

public class Main implements ModInitializer {

    public static final String MOD_ID = "better-trees-justfatlard";

    /** Vanilla leaf block → its corresponding LeafStairsBlock. Populated during onInitialize. */
    public static final Map<Block, LeafStairsBlock> LEAF_STAIRS_MAP = new LinkedHashMap<>();

    // Each entry: [vanilla leaves block, block ID suffix]
    private static final Object[][] LEAF_TYPES = {
        { Blocks.OAK_LEAVES,      "oak"      },
        { Blocks.SPRUCE_LEAVES,   "spruce"   },
        { Blocks.BIRCH_LEAVES,    "birch"    },
        { Blocks.JUNGLE_LEAVES,   "jungle"   },
        { Blocks.ACACIA_LEAVES,   "acacia"   },
        { Blocks.DARK_OAK_LEAVES, "dark_oak" },
        { Blocks.CHERRY_LEAVES,    "cherry"    },
        { Blocks.MANGROVE_LEAVES,  "mangrove"  },
        { Blocks.PALE_OAK_LEAVES,  "pale_oak"  },
    };

    @Override
    public void onInitialize() {
        boolean pandorical = PandoricalApi.isAvailable();

        for (Object[] type : LEAF_TYPES) {
            Block vanillaLeaves = (Block) type[0];
            String name        = (String) type[1];
            String blockId     = name + "_leaf_stairs";

            ResourceKey<Block> key = ResourceKey.create(
                Registries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, blockId));

            LeafStairsBlock stairsBlock = new LeafStairsBlock(
                BlockBehaviour.Properties.ofFullCopy(vanillaLeaves)
                    .setId(key)
                    .overrideLootTable(vanillaLeaves.getLootTable()));

            LEAF_STAIRS_MAP.put(vanillaLeaves, stairsBlock);
            Registry.register(BuiltInRegistries.BLOCK, Identifier.fromNamespaceAndPath(MOD_ID, blockId), stairsBlock);

            if (pandorical) {
                PandoricalApi.content().registerBlock(
                    MOD_ID + ":" + blockId,
                    new BlockRegistration().baseBlock(
                        BuiltInRegistries.BLOCK.getKey(vanillaLeaves).toString()));
            }
        }

        if (pandorical) {
            PandoricalApi.content().registerModAssets(MOD_ID);
        }

        System.out.println("[" + MOD_ID + "] Loaded better-trees");
    }
}
