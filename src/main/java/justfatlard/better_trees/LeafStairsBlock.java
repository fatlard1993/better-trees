package justfatlard.better_trees;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    // Decay is LeavesBlock's, inherited whole: the scheduled tick walks DISTANCE up to 7, and a
    // random tick after that drops the block. PERSISTENT is in the state definition and never
    // set, so a leaf stair decays exactly when the leaf it replaced would have.
    //
    // Removing the block from the scheduled tick instead put the shell and the vanilla leaves it
    // wraps on two clocks: felling a tree stripped the crown within a tick and left the core
    // hanging for the usual minutes. One clock means the shared code, not a copy of it.

    /**
     * Replicates LeavesBlock.updateShape without the WATERLOGGED fluid-tick side-effect: a
     * neighbour change reschedules the distance tick, and the distance itself is written by
     * {@link LeavesBlock#tick}. The state is returned unchanged.
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
}
