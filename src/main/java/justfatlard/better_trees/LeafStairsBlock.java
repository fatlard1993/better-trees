package justfatlard.better_trees;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LeafStairsBlock extends LeavesBlock {

    public static final EnumProperty<Direction> HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final EnumProperty<StairsShape> STAIRS_SHAPE = BlockStateProperties.STAIRS_SHAPE;

    public LeafStairsBlock(BlockBehaviour.Properties properties) {
        super(AmbientLeavesBlockSoundPlayer.noAmbientSound(), properties);
        this.registerDefaultState(
            this.getStateDefinition().any()
                .setValue(HORIZONTAL_FACING, Direction.NORTH)
                .setValue(HALF, Half.BOTTOM)
                .setValue(STAIRS_SHAPE, StairsShape.STRAIGHT)
                .setValue(LeavesBlock.DISTANCE, 7)
                .setValue(LeavesBlock.PERSISTENT, false)
                .setValue(WATERLOGGED, false)
        );
    }

    // MC 26.1: LeavesBlock.<init> calls setValue on PERSISTENT and WATERLOGGED before our
    // constructor body runs, so both must exist in the state definition or it crashes.
    // We expose them but keep them always-false; getStateForPlacement returns defaultBlockState().
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(LeavesBlock.DISTANCE, LeavesBlock.PERSISTENT, WATERLOGGED, HORIZONTAL_FACING, HALF, STAIRS_SHAPE);
    }

    // ── Liquid / waterlogging ────────────────────────────────────────────────

    /** Leaf stairs are never waterlogged. */
    @Override
    protected FluidState getFluidState(BlockState state) {
        return Fluids.EMPTY.defaultFluidState();
    }

    /** Prevent water (or any liquid) from being placed into this block. */
    @Override
    public boolean canPlaceLiquid(LivingEntity entity, BlockGetter level, BlockPos pos,
                                  BlockState state, Fluid fluid) {
        return false;
    }

    /** No liquid to place; always a no-op. */
    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state,
                               FluidState fluidState) {
        return false;
    }

    /** No liquid stored; bucket pickup always returns empty. */
    @Override
    public ItemStack pickupBlock(LivingEntity entity, LevelAccessor level, BlockPos pos,
                                 BlockState state) {
        return ItemStack.EMPTY;
    }

    // ── Placement ────────────────────────────────────────────────────────────

    /**
     * Player-placed leaf stairs use the default state: non-persistent, no waterlogging.
     * (Vanilla LeavesBlock.getStateForPlacement sets PERSISTENT=true and checks for water,
     * both of which we've removed from the state definition.)
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState();
    }

    // ── Shape ────────────────────────────────────────────────────────────────

    /**
     * The same state, wearing a real stair, so vanilla can be asked for the shape.
     *
     * <p>Facing, half and shape are the very properties {@link BlockStateProperties} hands to
     * {@code StairBlock}, so the two states agree by construction. Vanilla joins eight boxes over
     * those three properties to build a stair's outline; a copy of that arithmetic here would be one
     * quiet divergence away from the model it exists to match.
     */
    private static BlockState asStair(BlockState state) {
        return Blocks.OAK_STAIRS.defaultBlockState()
            .setValue(HORIZONTAL_FACING, state.getValue(HORIZONTAL_FACING))
            .setValue(HALF,              state.getValue(HALF))
            .setValue(STAIRS_SHAPE,      state.getValue(STAIRS_SHAPE));
    }

    /**
     * Extending {@link LeavesBlock} means the stair properties reached the model but never the
     * hitbox: a leaf stair drew as a step and stood as a full cube, so it could not be walked up
     * the way its own shape invited.
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return asStair(state).getShape(level, pos, context);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return asStair(state).getCollisionShape(level, pos, context);
    }

    /**
     * Snow layers survive only on blocks with a sturdy top face, which MC derives
     * from this shape.  half=TOP stairs have a fully solid top → snow allowed.
     * half=BOTTOM stairs have the exterior top corner cut away → no snow.
     */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(HALF) == Half.TOP ? Shapes.block() : Shapes.empty();
    }

    // ── Decay ────────────────────────────────────────────────────────────────

    /**
     * Distance-based decay is handled by scheduled ticks (see updateShape / tick).
     * Returning false keeps the random-tick system from ever driving decay for
     * this block.
     */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return false;
    }

    /**
     * A leaf stair is decaying when it can no longer reach a log within 7 hops.
     * PERSISTENT is absent from our state, so all leaf stairs decay when
     * disconnected from wood.
     */
    @Override
    protected boolean decaying(BlockState state) {
        return state.getValue(LeavesBlock.DISTANCE) == 7;
    }

    /**
     * Replicates LeavesBlock.updateShape without the WATERLOGGED fluid-tick side-effect.
     *
     * <p>LeavesBlock.updateShape (MC 26.1) does the following:
     * <ol>
     *   <li>If WATERLOGGED, schedule a water tick; we skip this entirely.
     *   <li>Compute {@code dist = getDistanceAt(neighbor) + 1}.
     *   <li>If dist ≠ 1 or current DISTANCE ≠ dist, schedule a block tick for distance
     *       recalculation (handled by tick → super.tick → updateDistance).
     *   <li>Return the state <em>unchanged</em>; the distance value itself is only
     *       written inside tick().
     * </ol>
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickView,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        int dist = LeavesBlock.getOptionalDistanceAt(neighborState).orElse(7) + 1;
        if ((dist != 1 || state.getValue(LeavesBlock.DISTANCE) != dist) && tickView != null) {
            tickView.scheduleTick(pos, this, 1);
        }
        return state;
    }

    // Falling-leaf particles are only spawned by FallingParticlesLeavesBlock subclasses;
    // plain LeavesBlock (which this extends) never spawns them, so no override is needed
    // to suppress them here.

    /**
     * When a scheduled tick fires:
     * <ul>
     *   <li>If distance is already 7 (disconnected from wood) → drop loot and remove instantly.
     *   <li>Otherwise → delegate to super.tick() which calls the private updateDistance,
     *       recalculating DISTANCE from all neighbors and writing the new value.
     * </ul>
     * PERSISTENT leaf stairs do not exist here; every leaf stair decays when
     * disconnected from wood.
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState current = level.getBlockState(pos);
        if (!(current.getBlock() instanceof LeafStairsBlock)) return;

        // Recalculate distance from all 6 neighbors inline.
        // Calling super.tick() (LeavesBlock.tick) updates DISTANCE via level.setBlock
        // but never removes the block when the result is 7, leaving leaf stairs floating
        // permanently after a tree is cut.  Doing it here lets us handle decay correctly.
        int dist = 7;
        for (Direction dir : Direction.values()) {
            dist = Math.min(dist,
                LeavesBlock.getOptionalDistanceAt(level.getBlockState(pos.relative(dir))).orElse(7) + 1);
        }

        if (dist >= 7) {
            dropResources(current, level, pos);
            level.removeBlock(pos, false);
        } else if (dist != current.getValue(LeavesBlock.DISTANCE)) {
            level.setBlock(pos, current.setValue(LeavesBlock.DISTANCE, dist), 3);
        }
    }

    /** Decay is driven by tick() via scheduled ticks; randomTick is intentionally suppressed. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    }
}
