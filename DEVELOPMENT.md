# Better Trees - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Source Map

| File | What is in it |
|---|---|
| `LeafStairsBlock.java` | The block: decay, snow, waterlogging, and being stood on |
| `LeafStairsProcessor.java` | Finding a finished tree's edge leaves and swapping them |
| `AncientTrees.java` | The roll for an oversized variant, and keeping its shape honest |
| `SelfSeeding.java` | A dropped sapling's one chance to take root |
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
