package ru.egor.colonyguardposts.mixin;

import com.minecolonies.api.colony.buildings.IGuardBuilding;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import ru.egor.colonyguardposts.GuardPosts;

@Mixin(value = AbstractEntityAIGuard.class, remap = false)
public abstract class GuardMovementMixin {
    @Shadow @Final protected IGuardBuilding buildingGuards;
    @ModifyConstant(method = "guardMovement", constant = @Constant(intValue = 5))
    private int colonyguardposts$nearPost(int original) {
        return buildingGuards instanceof AbstractBuildingGuards tower && GuardPosts.active(tower) ? 1 : original;
    }
}
