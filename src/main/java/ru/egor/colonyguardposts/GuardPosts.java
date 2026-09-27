package ru.egor.colonyguardposts;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.items.component.BuildingId;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBarracksTower;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import java.util.List;

public final class GuardPosts {
    public static final String GUARD_TASK = "com.minecolonies.core.guard.setting.guard";
    private static final String TOOL_REVISION = "colonyguardposts:revision";
    private GuardPosts() {}
    public static PostAssignments data(AbstractBuildingGuards tower) { return ((GuardPostAccess)tower).colonyguardposts$posts(); }
    public static boolean active(AbstractBuildingGuards tower) {
        return tower instanceof BuildingBarracksTower && GUARD_TASK.equals(tower.getTask()) && !data(tower).posts().isEmpty();
    }

    public static boolean safeFloor(Level level, BlockPos floor) {
        if (!level.hasChunkAt(floor) || level.isOutsideBuildHeight(floor) || level.isOutsideBuildHeight(floor.above(2))
            || !level.getWorldBorder().isWithinBounds(floor)) return false;
        var state = level.getBlockState(floor);
        if (!state.isFaceSturdy(level, floor, Direction.UP) || !state.getFluidState().isEmpty()
            || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) return false;
        for (int i = 1; i <= 2; i++) {
            BlockPos space = floor.above(i); var block = level.getBlockState(space);
            if (!block.getCollisionShape(level, space).isEmpty() || !block.getFluidState().isEmpty()
                || block.is(Blocks.FIRE) || block.is(Blocks.SOUL_FIRE) || block.is(Blocks.SWEET_BERRY_BUSH)) return false;
        }
        return true;
    }

    public static void refresh(AbstractBuildingGuards tower) {
        PostAssignments state = data(tower); Level level = tower.getColony().getWorld();
        if (level == null || level.isClientSide || !state.shouldRefresh(level.getGameTime())) return;
        // Do not load distant chunks or forget their assignments just because a player walked away.
        List<BlockPos> usable = state.posts().stream().filter(p -> !level.hasChunkAt(p) || safeFloor(level, p)).toList();
        if (state.reconcile(tower.getAllAssignedCitizen().stream().map(ICitizenData::getId).toList(), usable)) tower.markDirty();
    }

    public static BlockPos position(AbstractBuildingGuards tower, AbstractEntityCitizen citizen) {
        if (!active(tower) || citizen.getCitizenData() == null) return null;
        refresh(tower);
        BlockPos pos = data(tower).assigned(citizen.getCitizenData().getId());
        // Vanilla handles travel and combat; never teleport or force-load a post.
        return pos != null && safeFloor(citizen.level(), pos) ? pos : tower.getID();
    }

    /** Null delegates to the original item, including all patrol/follow and ordinary guard towers. */
    public static InteractionResult use(UseOnContext ctx) {
        var player = ctx.getPlayer(); if (player == null) return null;
        ItemStack tool = ctx.getItemInHand();
        if (ctx.getLevel().isClientSide) return null;
        ColonyId colonyId = ColonyId.readFromItemStack(tool);
        if (!colonyId.hasColonyId()) return null;
        if (!colonyId.dimension().equals(ctx.getLevel().dimension()))
            return player instanceof ServerPlayer serverPlayer ? tell(serverPlayer, "dimension") : InteractionResult.FAIL;
        if (!(BuildingId.readBuildingFromItemStack(tool) instanceof BuildingBarracksTower tower) || !GUARD_TASK.equals(tower.getTask())) return null;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.FAIL;
        BlockPos floor = ctx.getClickedPos();
        if (tower.getColony().getWorld() != ctx.getLevel() || !tower.getColony().getPermissions().hasPermission(player, Action.MANAGE_HUTS))
            return tell(serverPlayer, "denied");
        if (!player.canInteractWithBlock(floor, 1.0)) return tell(serverPlayer, "too_far_player");
        PostAssignments state = data(tower);
        CompoundTag custom = tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (custom.contains(TOOL_REVISION) && custom.getLong(TOOL_REVISION) != state.revision()) {
            remember(tool, state.revision()); return tell(serverPlayer, "stale");
        }
        if (player.isShiftKeyDown() && floor.equals(tower.getID())) {
            tower.setGuardPos(tower.getID());
            remember(tool, data(tower).revision()); tower.markDirty();
            return tell(serverPlayer, "cleared");
        }
        if (player.isShiftKeyDown()) {
            if (!state.remove(floor)) return tell(serverPlayer, "missing");
            if (state.posts().isEmpty()) tower.setGuardPos(tower.getID());
            tower.markDirty(); remember(tool, state.revision());
            show(tower, serverPlayer); return tell(serverPlayer, "removed", state.posts().size());
        }
        double dx = (double)floor.getX() - tower.getID().getX(), dz = (double)floor.getZ() - tower.getID().getZ();
        if (dx * dx + dz * dz > (double)tower.getPatrolDistance() * tower.getPatrolDistance()) return tell(serverPlayer, "too_far_tower");
        var colonyAt = IColonyManager.getInstance().getColonyByPosFromWorld(ctx.getLevel(), floor);
        if (colonyAt == null || colonyAt.getID() != tower.getColony().getID()) return tell(serverPlayer, "outside");
        if (!safeFloor(ctx.getLevel(), floor)) return tell(serverPlayer, "unsafe");
        if (state.posts().contains(floor)) { remember(tool, state.revision()); show(tower, serverPlayer); return tell(serverPlayer, "exists"); }
        if (state.posts().size() >= PostAssignments.MAX_POSTS) return tell(serverPlayer, "limit", PostAssignments.MAX_POSTS);
        // Preserve a sensible native fallback if this addon is removed later.
        if (state.posts().isEmpty()) tower.setGuardPos(floor);
        state.add(floor); tower.markDirty(); remember(tool, state.revision());
        show(tower, serverPlayer); return tell(serverPlayer, "added", state.posts().size(), floor.toShortString());
    }

    private static void remember(ItemStack tool, long revision) {
        CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> tag.putLong(TOOL_REVISION, revision));
    }
    private static InteractionResult tell(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.translatable("colonyguardposts." + key, args));
        // Consume even rejected clicks, so they cannot fall through into native single-post editing.
        return InteractionResult.SUCCESS;
    }
    private static void show(AbstractBuildingGuards tower, ServerPlayer player) {
        refresh(tower);
        for (BlockPos p : data(tower).posts()) {
            if (!player.serverLevel().hasChunkAt(p)) continue;
            player.serverLevel().sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, p.getX() + .5, p.getY() + 1.2, p.getZ() + .5, 5, .25, .2, .25, 0);
        }
    }
}
