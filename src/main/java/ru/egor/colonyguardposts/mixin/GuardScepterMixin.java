package ru.egor.colonyguardposts.mixin;

import com.minecolonies.core.items.ItemScepterGuard;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.egor.colonyguardposts.GuardPosts;

@Mixin(value = ItemScepterGuard.class, remap = false)
public abstract class GuardScepterMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void colonyguardposts$edit(UseOnContext context, CallbackInfoReturnable<InteractionResult> result) {
        InteractionResult handled = GuardPosts.use(context);
        if (handled != null) result.setReturnValue(handled);
    }
}
