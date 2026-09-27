package ru.egor.colonyguardposts;

import com.minecolonies.core.items.ItemScepterGuard;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@Mod(ColonyGuardPosts.ID)
public final class ColonyGuardPosts {
    public static final String ID = "colonyguardposts";
    public ColonyGuardPosts() { NeoForge.EVENT_BUS.register(this); }
    @SubscribeEvent
    public void tooltip(ItemTooltipEvent event) {
        if (event.getItemStack().getItem() instanceof ItemScepterGuard) {
            event.getToolTip().add(Component.translatable("colonyguardposts.tooltip.mode"));
            event.getToolTip().add(Component.translatable("colonyguardposts.tooltip.edit"));
            event.getToolTip().add(Component.translatable("colonyguardposts.tooltip.clear"));
        }
    }
}
