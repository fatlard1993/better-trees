# Better Trees

A Fabric mod that makes vanilla trees look more natural by replacing the leaf blocks on their outer edges with stair-shaped leaf blocks, so canopies taper instead of ending in flat cubes. It also adds a small chance for saplings and worldgen trees to grow into oversized "ancient" variants of their fancy/mega tree forms.

## Features

- **Leaf stairs on generation**: after any tree grows (from a sapling or from worldgen), its outermost edge leaves are replaced with matching leaf stair blocks for a rounder, more natural canopy silhouette
- **All vanilla leaf types supported**: Oak, Spruce, Birch, Jungle, Acacia, Dark Oak, Cherry, Mangrove, and Pale Oak all have a matching leaf stairs variant
- **Leaf stairs behave like leaves**: they decay when disconnected from logs, block snow accumulation appropriately based on orientation, and can't be waterlogged
- **Ancient trees**: a small chance (roughly 1% from saplings, 0.5% during worldgen) for a tree to grow as its larger fancy/mega variant instead of the normal form, for the species that have one
- **Correct leaf tinting**: leaf stairs are colored to match their parent leaf type (biome-dependent foliage color for most species, fixed tints for spruce/birch, untinted for cherry/pale oak) on Pandorical clients

## Pandorical

Better Trees uses Pandorical to register its leaf stairs blocks (visually based on the corresponding vanilla leaves block) and to apply the correct per-species leaf tint so the stairs blend in with their parent tree. Pandorical is declared as a hard dependency (`fabric.mod.json`), so it must be installed alongside this mod, on both server and client, for the mod to load at all; there is no vanilla-client fallback.

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## License

MIT, see [LICENSE](LICENSE).
