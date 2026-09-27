package ru.egor.colonyguardposts;

import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.registry.IBuildingDataManager;
import com.minecolonies.api.items.component.*;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.GuardBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBarracksTower;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.util.ChunkDataHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@EventBusSubscriber(modid = ColonyGuardPosts.ID, bus = EventBusSubscriber.Bus.MOD)
@PrefixGameTestTemplate(false)
public class GuardPostGameTests {
    @SubscribeEvent public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event) { event.register(GuardPostGameTests.class); }
    record Fixture(ServerLevel level, Colony colony, BuildingBarracksTower tower, ServerPlayer owner, BlockPos origin, List<ICitizenData> citizens) {}

    static ServerPlayer player(ServerLevel level) {
        var profile = new GameProfile(UUID.randomUUID(), "guard-post-test");
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()) {
            @Override public void sendSystemMessage(Component c) {}
            @Override public void sendSystemMessage(Component c, boolean b) {}
            @Override public void displayClientMessage(Component c, boolean b) {}
        };
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(), connection, player,
            net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {}
        };
        return player;
    }

    static Fixture fixture(GameTestHelper test, int offset, int guards) throws Exception {
        ServerLevel level = test.getLevel(); BlockPos origin = test.absolutePos(new BlockPos(offset, 10, 1024));
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 1; z++) level.setChunkForced((origin.getX() >> 4)+x, (origin.getZ() >> 4)+z, true);
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-3,0,-3), origin.offset(34,3,6)))
            level.setBlock(p, (p.getY() == origin.getY() ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
        ServerPlayer owner = player(level); owner.setPos(origin.above().getCenter());
        Colony colony = (Colony)IColonyManager.getInstance().createColony(level, origin, owner, "Guard Posts Test", "medievaloak");
        ChunkDataHelper.staticClaimInRange(colony, true, origin, 2, level, true);
        BuildingBarracksTower tower = (BuildingBarracksTower)building(level, colony, origin.above(), "blockhutbarrackstower");
        tower.setBuildingLevel(5); tower.getSetting(AbstractBuildingGuards.GUARD_TASK).set(GuardPosts.GUARD_TASK);
        List<ICitizenData> citizens = new ArrayList<>();
        GuardBuildingModule knights = tower.getModulesByType(GuardBuildingModule.class).stream()
            .filter(m -> m.getJobEntry().getKey().getPath().equals("knight")).findFirst().orElseThrow();
        for (int i = 0; i < guards; i++) {
            ICitizenData citizen = colony.getCitizenManager().createAndRegisterCivilianData();
            citizen.setIsChild(false);
            EntityCitizen entity = (EntityCitizen)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("minecolonies:citizen")).create(level);
            entity.setUUID(citizen.getUUID()); entity.setCitizenId(citizen.getId()); entity.getCitizenColonyHandler().setColonyId(colony.getID());
            entity.setPos(origin.offset(i+1,1,2).getCenter());
            entity.setNoAi(true); level.addFreshEntity(entity); citizens.add(citizen);
            entity.getCitizenColonyHandler().registerWithColony(colony.getID(), citizen.getId());
            if (!knights.assignCitizen(citizen)) throw new IllegalStateException("Could not assign guard " + i);
        }
        return new Fixture(level, colony, tower, owner, origin, citizens);
    }

    static AbstractBuildingGuards building(ServerLevel level, Colony colony, BlockPos pos, String block) throws Exception {
        level.setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("minecolonies:" + block)).defaultBlockState(), 3);
        var tile = (AbstractTileEntityColonyBuilding)level.getBlockEntity(pos);
        var building = (AbstractBuildingGuards)IBuildingDataManager.getInstance().createFrom(colony, tile);
        building.setRotationMirror(com.ldtteam.structurize.api.RotationMirror.NONE);
        var add = colony.getServerBuildingManager().getClass().getDeclaredMethod("addBuilding", IBuilding.class);
        add.setAccessible(true); add.invoke(colony.getServerBuildingManager(), building); tile.setColony(colony); tile.setBuilding(building);
        return building;
    }
    static ItemStack tool(Fixture f) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecolonies:scepterguard")));
        new ColonyId(f.colony.getID(), f.level.dimension()).writeToItemStack(stack); new BuildingId(f.tower.getID()).writeToItemStack(stack); return stack;
    }
    static void click(ServerPlayer player, ItemStack tool, BlockPos floor, boolean sneak) {
        player.setItemInHand(InteractionHand.MAIN_HAND, tool); player.setShiftKeyDown(sneak); player.setPos(floor.above().getCenter());
        tool.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(floor.getCenter(), Direction.UP, floor, false)));
        player.setShiftKeyDown(false);
    }
    static void cleanup(Fixture f) {
        f.citizens.forEach(c -> c.getEntity().ifPresent(e -> e.discard()));
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 1; z++) f.level.setChunkForced((f.origin.getX() >> 4)+x, (f.origin.getZ() >> 4)+z, false);
    }

    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty", timeoutTicks=100)
    public static void realScepterDistributesFiveGuardsAndReloadsNativeTower(GameTestHelper test) throws Exception {
        var f = fixture(test, 0, 5); ItemStack scepter = tool(f);
        for (int x : List.of(6,16,26)) click(f.owner, scepter, f.origin.east(x), false);
        var state = GuardPosts.data(f.tower);
        test.assertTrue(state.posts().size() == 3 && !scepter.isEmpty(), "real scepter keeps all three posts and is not consumed");
        GuardPosts.refresh(f.tower);
        var counts = state.posts().stream().map(p -> state.assignments().values().stream().filter(p::equals).count()).sorted().toList();
        test.assertTrue(counts.equals(List.of(1L,2L,2L)), "five guards must distribute 2/2/1: " + counts);
        for (ICitizenData citizen : f.citizens)
            test.assertTrue(f.tower.getGuardPos(citizen.getEntity().orElseThrow()).equals(state.assigned(citizen.getId())), "native AI query returns individual post");
        CompoundTag saved = f.tower.serializeNBT(f.level.registryAccess());
        var restored = (BuildingBarracksTower)IBuildingDataManager.getInstance().createFrom(f.colony, (AbstractTileEntityColonyBuilding)f.level.getBlockEntity(f.tower.getID()));
        restored.deserializeNBT(f.level.registryAccess(), saved);
        test.assertTrue(GuardPosts.data(restored).posts().equals(state.posts()), "native tower NBT retains posts");
        test.assertTrue(GuardPosts.data(restored).assignments().equals(state.assignments()), "native tower NBT retains assignments");
        // Existing single-position saves remain readable when addon data is absent.
        saved.remove(PostAssignments.NBT_KEY); restored.deserializeNBT(f.level.registryAccess(), saved);
        test.assertTrue(GuardPosts.data(restored).posts().isEmpty(), "legacy towers have no custom posts");
        cleanup(f); test.succeed();
    }

    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty", timeoutTicks=100)
    public static void editRejectsForeignStaleUnsafeAndDistantRequests(GameTestHelper test) throws Exception {
        var f = fixture(test, 512, 0); ItemStack scepter = tool(f); BlockPos a = f.origin.east(6), b = f.origin.east(16);
        click(f.owner, scepter, a, false);
        ItemStack stale = scepter.copy(); click(f.owner, scepter, b, false);
        click(f.owner, stale, a, true); test.assertTrue(GuardPosts.data(f.tower).posts().size()==2, "stale tool cannot delete newer data");
        click(f.owner, stale, a, true); test.assertTrue(GuardPosts.data(f.tower).posts().equals(List.of(b)), "retry with refreshed revision deletes intended post");
        var stranger = player(f.level); click(stranger, tool(f), b, true);
        test.assertTrue(GuardPosts.data(f.tower).posts().equals(List.of(b)), "non-member cannot edit posts");
        BlockPos unsafe = f.origin.east(10); f.level.setBlock(unsafe.above(), Blocks.STONE.defaultBlockState(), 2);
        click(f.owner, stale, unsafe, false); test.assertTrue(GuardPosts.data(f.tower).posts().size()==1, "blocked space cannot become a post");
        ItemStack farTool = tool(f); f.owner.setPos(f.origin.above().getCenter()); f.owner.setItemInHand(InteractionHand.MAIN_HAND, farTool);
        farTool.useOn(new UseOnContext(f.owner, InteractionHand.MAIN_HAND, new BlockHitResult(f.origin.east(26).getCenter(), Direction.UP, f.origin.east(26), false)));
        test.assertTrue(GuardPosts.data(f.tower).posts().size()==1, "forged remote click rejected");
        click(f.owner, tool(f), f.origin.east(250), false); test.assertTrue(GuardPosts.data(f.tower).posts().size()==1, "tower distance enforced");
        BlockPos outside = f.origin.east(100); f.level.setBlock(outside, Blocks.STONE.defaultBlockState(), 2);
        test.assertTrue(IColonyManager.getInstance().getColonyByPosFromWorld(f.level, outside) == null, "claim rejection fixture is outside colony");
        click(f.owner, tool(f), outside, false); test.assertTrue(GuardPosts.data(f.tower).posts().size()==1, "unclaimed post rejected");
        var otherDimension = tool(f); new ColonyId(f.colony.getID(), net.minecraft.world.level.Level.NETHER).writeToItemStack(otherDimension);
        click(f.owner, otherDimension, a, false); test.assertTrue(GuardPosts.data(f.tower).posts().size()==1, "cross-dimension tool cannot edit");
        click(f.owner, tool(f), f.tower.getID(), true); test.assertTrue(GuardPosts.data(f.tower).posts().isEmpty(), "sneak click tower clears posts");
        cleanup(f); test.succeed();
    }

    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty", timeoutTicks=100)
    public static void patrolAndOrdinaryTowerKeepNativeScepterBehavior(GameTestHelper test) throws Exception {
        var f = fixture(test, 1024, 0); ItemStack scepter = tool(f);
        click(f.owner, scepter, f.origin.east(6), false);
        f.tower.getSetting(AbstractBuildingGuards.GUARD_TASK).set("com.minecolonies.core.guard.setting.patrol");
        ItemStack patrol = tool(f); click(f.owner, patrol, f.origin.east(16), false); click(f.owner, patrol, f.origin.east(26), false);
        test.assertTrue(f.tower.serializeNBT(f.level.registryAccess()).getList("patrol targets", 10).size()==2, "native patrol still records its route");
        test.assertTrue(GuardPosts.data(f.tower).posts().size()==1 && !GuardPosts.active(f.tower), "patrol preserves inactive posts");
        AbstractBuildingGuards ordinary = building(f.level, f.colony, f.origin.offset(3,1,4), "blockhutguardtower");
        ordinary.setBuildingLevel(1); ordinary.getSetting(AbstractBuildingGuards.GUARD_TASK).set(GuardPosts.GUARD_TASK);
        ItemStack normal = tool(f); new BuildingId(ordinary.getID()).writeToItemStack(normal);
        click(f.owner, normal, f.origin.east(10), false);
        test.assertTrue(GuardPosts.data(ordinary).posts().isEmpty(), "ordinary tower remains native");
        test.assertTrue(f.owner.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "native single-post tool is still consumed");
        cleanup(f); test.succeed();
    }

    @GameTest(templateNamespace="minecraft", template="bastion/mobs/empty", timeoutTicks=180)
    public static void guardAiUsesAssignedPostForNavigation(GameTestHelper test) throws Exception {
        var f = fixture(test, 1536, 1); BlockPos post = f.origin.east(6);
        click(f.owner, tool(f), post, false);
        var entity = f.citizens.getFirst().getEntity().orElseThrow();
        // Keep unrelated needs AI paused; exercise the native guard movement method and navigator.
        entity.setPos(f.origin.offset(2,1,0).getCenter());
        var ai = new com.minecolonies.core.entity.ai.workers.guard.EntityAIMelee((com.minecolonies.core.colony.jobs.guard.JobKnight)f.citizens.getFirst().getJob());
        test.succeedWhen(() -> {
            for (int i=0;i<20;i++) ai.guardMovement();
            entity.getNavigation().tick();
            var path = entity.getNavigation().getPath();
            test.assertTrue(path != null && path.canReach(), "native guard navigator must find a route to its assigned post");
            test.assertTrue(path.getTarget().closerThan(post, 3), "native navigation targets the marked post, not tower or group position");
            cleanup(f);
        });
    }
}
