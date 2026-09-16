# Better Trees

A Minecraft Fabric mod. Canopies that taper instead of ending in a flat cube, and saplings that behave like seeds.

## What This Mod Does

A vanilla tree stops dead at the edge of its outermost leaf block, which from any distance reads as a green box with a trunk under it. This replaces those edge leaves with **stair-shaped leaves**, so a canopy rounds off the way a real one does. It is a small change made in a lot of places at once, and the result is a skyline rather than a stack of cubes.

Two smaller things ride along: some trees grow far larger than they should, and a sapling nobody picks up eventually plants itself.

## Leaf Stairs

After any tree finishes generating — from a sapling, from worldgen, or drawn as part of a structure — its outermost edge leaves are swapped for matching leaf stairs. Trees a structure places get the same treatment as ones that grow, so a village orchard does not stand out against the forest behind it.

Every leaf in the game has stairs, azalea and flowering azalea included. So do the caps of the
huge mushrooms and the huge fungi: the rim of a red mushroom, the plate of a brown one, and the
wart of a crimson or warped fungus step down at their edges the same way.

**They are leaves, not decoration.** They decay when disconnected from logs, on the scheduled tick rather than a minute later, and so do vanilla leaves here: a felled crown comes down one ring per tick from where the log was, core and shell together. They block snow according to their orientation, they cannot be waterlogged, and they are walked on like stairs rather than like a full block.

**They are tinted to match their parent.** Biome-dependent foliage colour for most species, fixed tints for spruce and birch, untinted for cherry, pale oak and azalea. Poplars get stairs too, in all three of their colours.

## Leaves Keep To Their Own Wood

Vanilla asks only whether there is *a* log within six blocks. So a birch beam run through a wall
holds a felled oak's crown up in the air, and clearing a tree farm leaves a shell of somebody
else's leaves standing in it.

Here a leaf only counts wood of its own species. Fell an oak and its leaves come down, whatever
else is nearby; build with any log you like and nothing sticks to it. Leaves do not prop each
other up across species either, so an oak canopy touching a birch one keeps its own edges.

**Worked out from names, not from a table.** A mod shipping `walnut_log` and `walnut_leaves` is
handled the day it is installed, with no datapack and no entry to add. The few that do not follow
the rule - azalea, which grows on oak, and bamboo - are named individually.

**Unknown means yes.** A block whose name says nothing gets vanilla's own answer. Support is only
ever taken away when the neighbour is certainly a different tree, because being wrong in that
direction deletes somebody's canopy and being wrong in the other leaves it exactly as it was.

This mod's leaf stairs are covered for free: they are leaves, and their names carry their species
the same way vanilla's do.

## Ancient Trees

A small chance — 0.5%, during worldgen only — that a tree generates as its larger fancy or mega variant instead of the normal form, for the species that have one. Big enough to be worth walking to, rare enough that finding one is an event.

**Worldgen only, deliberately.** Saplings used to roll for it too, at about 1%, which quietly made an ancient a crop: plant enough and you get one, and the rarity that made it worth walking to was gone. They are something you come across now, not something you farm. The roll is gated on the tree being placed into a chunk that is still generating rather than into a world that already exists, because a sapling growing runs the very same feature — deleting the sapling path alone would have left the worldgen roll firing for saplings anyway.

An ancient conifer's cone has to clear its own trunk, so the shape holds up rather than growing through itself.

**Every vanilla tree can be ancient**, each in its own shape: oak's spreading heads, acacia's umbrellas, cherry's cloud, birch's slim column, jungle's emergent crowns, mangrove's paired crowns, and poplar as the Lombardy column it is in life, several times taller than it is wide. Spruce rolls evens between vanilla's two big forms: the mega spruce, bushy to the ground, and the mega pine, a bare trunk with its crown at the top. Where vanilla has a bigger form the ancient grows from it; where it has none, from the tree already standing, which is also how a yellow poplar or an azalea keeps its own leaves.

Huge mushrooms and huge fungi go ancient at the same odds: a red mushroom as a domed cap on a thick stem twice the height, a brown one as a plate the width of a house, a crimson or warped fungus as a stem a dozen blocks taller under a pagoda of wart lit through with shroomlight.

## Self-Seeding Saplings

Saplings fall out of trees by the thousand and are picked up by nobody. Left alone they sit as items until they despawn, which is the one thing a seed does not do.

A dropped sapling lying on ground it could grow in gets **one chance** to take root there, at about 60%, rolled once — not once per tick, because a repeated roll of any size is a certainty given long enough. The ones that fail despawn the way they always did, so a forest floor does not carpet over.

It waits a minute first. Five seconds was the original number and it was not long enough to be either thing: saplings rooted under players walking over to collect them, and a stack set down for a moment was gone. A minute is well short of vanilla's five-minute despawn, so a seed that takes has genuinely been left.

It is not only saplings: any dropped item that places a plant gets the same one chance, and the plant itself decides whether it could live where it lies.

## Pandorical

Better Trees registers its leaf stairs through Pandorical — each one standing in for its vanilla leaf block — and applies the per-species tint through it as well. It also asks Pandorical clients to cull the inside of leaf canopies, since nobody stands in a crown to see those faces. Pandorical is required on **both** server and client; there is no vanilla-client fallback, because a client with no leaf stairs has nothing to draw.

With [block-tip](https://github.com/fatlard1993/block-tip) installed, a leaf stair names itself as the leaf it is.

## Development

Installing, building and the map of the source are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
