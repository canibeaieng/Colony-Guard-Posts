package ru.egor.colonyguardposts.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import ru.egor.colonyguardposts.*;

@Mixin(value = AbstractBuildingGuards.class, remap = false)
public abstract class GuardBuildingMixin implements GuardPostAccess {
    @Unique private final PostAssignments colonyguardposts$state = new PostAssignments();
    @Override public PostAssignments colonyguardposts$posts() { return colonyguardposts$state; }
    @Inject(method = "getGuardPos", at = @At("HEAD"), cancellable = true)
    private void colonyguardposts$assigned(AbstractEntityCitizen citizen, CallbackInfoReturnable<BlockPos> result) {
        BlockPos pos = GuardPosts.position((AbstractBuildingGuards)(Object)this, citizen);
        if (pos != null) result.setReturnValue(pos);
    }
    @Inject(method = "setGuardPos", at = @At("HEAD"))
    private void colonyguardposts$nativeReset(BlockPos pos, CallbackInfo info) {
        colonyguardposts$state.clear();
        ((AbstractBuildingGuards)(Object)this).markDirty();
    }
    @Inject(method = "serializeNBT", at = @At("RETURN"))
    private void colonyguardposts$save(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> result) {
        result.getReturnValue().put(PostAssignments.NBT_KEY, colonyguardposts$state.save());
    }
    @Inject(method = "deserializeNBT", at = @At("TAIL"))
    private void colonyguardposts$load(HolderLookup.Provider provider, CompoundTag tag, CallbackInfo info) {
        colonyguardposts$state.load(tag.getCompound(PostAssignments.NBT_KEY));
    }
}
