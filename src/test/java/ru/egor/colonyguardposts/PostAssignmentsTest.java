package ru.egor.colonyguardposts;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PostAssignmentsTest {
    private static final BlockPos A = new BlockPos(1, 64, 1), B = new BlockPos(20, 64, 1), C = new BlockPos(40, 64, 1);
    private static PostAssignments three() {
        PostAssignments state = new PostAssignments(); state.add(A); state.add(B); state.add(C); return state;
    }
    @Test void balancesFiveGuardsAndDoesNotShuffleOnRepeatedQueries() {
        var state = three(); var roster = List.of(5, 4, 3, 2, 1);
        assertTrue(state.reconcile(roster, state.posts()));
        assertEquals(List.of(1L, 2L, 2L), state.posts().stream().map(p -> state.assignments().values().stream().filter(p::equals).count()).sorted().toList());
        var before = state.assignments();
        assertFalse(state.reconcile(List.of(1, 3, 5, 4, 2), state.posts())); assertEquals(before, state.assignments());
    }
    @Test void hiresAndDeathsRebalanceWithoutLosingRemainingCitizens() {
        var state = three(); state.reconcile(List.of(1,2,3,4,5), state.posts());
        state.reconcile(List.of(1,2,3,4), state.posts()); assertFalse(state.assignments().containsKey(5));
        state.reconcile(List.of(1,2,3,4,6), state.posts()); assertEquals(Set.of(1,2,3,4,6), state.assignments().keySet());
        assertEquals(3, new HashSet<>(state.assignments().values()).size());
    }
    @Test void addingPostMovesOnlyOneOfThreeGuards() {
        var state = new PostAssignments(); state.add(A); state.add(B); state.reconcile(List.of(1,2,3), state.posts());
        var before = state.assignments(); state.add(C); state.reconcile(List.of(1,2,3), state.posts());
        assertEquals(1, before.entrySet().stream().filter(e -> !e.getValue().equals(state.assigned(e.getKey()))).count());
    }
    @Test void blockedAndRemovedPostsReleaseAssignments() {
        var state = three(); state.reconcile(List.of(1,2,3), state.posts());
        state.reconcile(List.of(1,2,3), List.of(B,C)); assertFalse(state.assignments().containsValue(A));
        state.remove(B); state.reconcile(List.of(1,2,3), List.of(C)); assertEquals(Set.of(C), new HashSet<>(state.assignments().values()));
        state.reconcile(List.of(1,2,3), List.of()); assertTrue(state.assignments().isEmpty());
    }
    @Test void saveLoadPreservesAssignmentsRevisionAndAllowsOldBuildings() {
        var state = three(); state.reconcile(List.of(1,2,3,4,5), state.posts());
        var restored = new PostAssignments(); restored.load(state.save());
        assertEquals(state.posts(), restored.posts()); assertEquals(state.assignments(), restored.assignments()); assertEquals(state.revision(), restored.revision());
        assertFalse(restored.reconcile(List.of(1,2,3,4,5), restored.posts()));
        restored.load(new CompoundTag()); assertTrue(restored.posts().isEmpty()); assertTrue(restored.assignments().isEmpty());
    }
    @Test void morePostsThanGuardsLeavesEmptyPostsAndBoundsInput() {
        var state = three(); state.reconcile(List.of(1,2), state.posts());
        assertEquals(2, state.assignments().size()); assertEquals(2, new HashSet<>(state.assignments().values()).size());
        assertFalse(state.add(A));
        for (int i=0;i<40;i++) state.add(new BlockPos(100+i,64,0));
        assertEquals(PostAssignments.MAX_POSTS, state.posts().size());
        state.clear(); assertTrue(state.assignments().isEmpty());
    }
}
