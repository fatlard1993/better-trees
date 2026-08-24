# Better Trees

A Minecraft Fabric mod. Canopies that taper instead of ending in a flat cube, and saplings that behave like seeds.

## What This Mod Does

A vanilla tree stops dead at the edge of its outermost leaf block, which from any distance reads as a green box with a trunk under it. This replaces those edge leaves with **stair-shaped leaves**, so a canopy rounds off the way a real one does. It is a small change made in a lot of places at once, and the result is a skyline rather than a stack of cubes.

Two smaller things came along with it: some trees grow far larger than they should, and a sapling nobody picks up eventually plants itself.

## Leaf Stairs

After any tree finishes generating — from a sapling, from worldgen, or drawn as part of a structure — its outermost edge leaves are swapped for matching leaf stairs. Trees a structure places get the same treatment as ones that grow, so a village orchard does not stand out against the forest behind it.

**They are leaves, not decoration.** They decay when disconnected from logs, on the scheduled tick the way vanilla leaves do rather than a minute later. They block snow according to their orientation, they cannot be waterlogged, and they are walked on like stairs rather than like a full block.

**They are tinted to match their parent.** Biome-dependent foliage colour for most species, fixed tints for spruce and birch, untinted for cherry and pale oak. Poplars get stairs too, in all three of their colours.

## Ancient Trees

A small chance — roughly 1% from a sapling, 0.5% during worldgen — that a tree grows as its larger fancy or mega variant instead of the normal form, for the species that have one. Big enough to be worth walking to, rare enough that finding one is an event.

An ancient conifer's cone has to clear its own trunk, so the shape holds up rather than growing through itself.

## Self-Seeding Saplings

Saplings fall out of trees by the thousand and are picked up by nobody. Left alone they sit as items until they despawn, which is the one thing a seed does not do.

A dropped sapling lying on ground it could grow in gets **one chance** to take root there, at about 60%, rolled once — not once per tick, because a repeated roll of any size is a certainty given long enough. The ones that fail despawn the way they always did, so a forest floor does not carpet over.

It waits a minute first. Five seconds was the original number and it was not long enough to be either thing: saplings rooted under players walking over to collect them, and a stack set down for a moment was gone. A minute is well short of vanilla's five-minute despawn, so a seed that takes has genuinely been left.

## Pandorical

Better Trees registers its leaf stairs through Pandorical — each one standing in for its vanilla leaf block — and applies the per-species tint through it as well. Pandorical is a hard dependency (`fabric.mod.json`) and must be present on **both** server and client; there is no vanilla-client fallback, because a client with no leaf stairs has nothing to draw.

With [block-tip](https://github.com/fatlard1993/block-tip) installed, a leaf stair names itself as the leaf it is.

## Source Map

| File | What is in it |
|---|---|
| `LeafStairsBlock.java` | The block: decay, snow, waterlogging, and being stood on |
| `LeafStairsProcessor.java` | Finding a finished tree's edge leaves and swapping them |
| `AncientTrees.java` | The roll for an oversized variant, and keeping its shape honest |
| `SelfSeeding.java` | A dropped sapling's one chance to take root |
| `mixin/TreeGrowerMixin.java` | Trees grown from a sapling |
| `mixin/TreeFeatureMixin.java` | Trees placed by worldgen |
| `mixin/StructureTemplateMixin.java` | Trees a structure draws |
| `mixin/LeafDecayMixin.java` | Decaying on the scheduled tick |
| `mixin/ItemEntityMixin.java` | The dropped sapling's timer |
| `integration/LeafTipRegistration.java` | block-tip naming |

## Building

Better Trees builds against Pandorical's live source, not a published artifact: `settings.gradle` includes `../pandorical`. It also compiles against block-tip's jar for the optional tip integration, so build that first.

```bash
./gradlew build
```

The built jar will be in `build/libs/`.

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## License

MIT, see [LICENSE](LICENSE).
