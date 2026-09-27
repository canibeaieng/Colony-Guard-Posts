package ru.egor.colonyguardposts;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Stable assignments belong to the tower, including citizens whose entities are unloaded. */
public final class PostAssignments {
    public static final int MAX_POSTS = 16;
    public static final String NBT_KEY = "colonyguardposts:posts";
    private final List<BlockPos> posts = new ArrayList<>();
    private final Map<Integer, BlockPos> assignments = new TreeMap<>();
    private long revision;
    private long checkedAt = Long.MIN_VALUE;

    public List<BlockPos> posts() { return List.copyOf(posts); }
    public Map<Integer, BlockPos> assignments() { return Map.copyOf(assignments); }
    public long revision() { return revision; }
    public BlockPos assigned(int citizen) { return assignments.get(citizen); }
    public boolean add(BlockPos pos) {
        if (posts.contains(pos) || posts.size() >= MAX_POSTS) return false;
        posts.add(pos.immutable()); edited(); return true;
    }
    public boolean remove(BlockPos pos) {
        if (!posts.remove(pos)) return false;
        assignments.values().removeIf(pos::equals); edited(); return true;
    }
    public void clear() {
        posts.clear(); assignments.clear(); edited();
    }
    private void edited() { revision++; checkedAt = Long.MIN_VALUE; }
    public boolean shouldRefresh(long tick) {
        if (checkedAt != Long.MIN_VALUE && tick >= checkedAt && tick - checkedAt < 20) return false;
        checkedAt = tick; return true;
    }

    /** Move only as many citizens as necessary to restore a balanced distribution. */
    public boolean reconcile(Collection<Integer> citizens, Collection<BlockPos> available) {
        Map<Integer, BlockPos> before = new TreeMap<>(assignments);
        SortedSet<Integer> roster = new TreeSet<>(citizens);
        List<BlockPos> candidates = posts.stream().filter(available::contains).toList();
        assignments.entrySet().removeIf(e -> !roster.contains(e.getKey()) || !candidates.contains(e.getValue()));
        if (!candidates.isEmpty()) {
            Map<BlockPos, Integer> counts = new LinkedHashMap<>();
            candidates.forEach(p -> counts.put(p, 0));
            assignments.values().forEach(p -> counts.compute(p, (k, n) -> n + 1));
            for (int id : roster) {
                if (assignments.containsKey(id)) continue;
                BlockPos target = candidates.stream().min(Comparator.comparingInt(counts::get)).orElseThrow();
                assignments.put(id, target); counts.compute(target, (k, n) -> n + 1);
            }
            while (true) {
                BlockPos low = candidates.stream().min(Comparator.comparingInt(counts::get)).orElseThrow();
                BlockPos high = candidates.stream().max(Comparator.comparingInt(counts::get)).orElseThrow();
                if (counts.get(high) - counts.get(low) <= 1) break;
                int moved = assignments.entrySet().stream().filter(e -> high.equals(e.getValue()))
                    .mapToInt(Map.Entry::getKey).max().orElseThrow();
                assignments.put(moved, low);
                counts.compute(high, (k, n) -> n - 1); counts.compute(low, (k, n) -> n + 1);
            }
        }
        return !before.equals(assignments);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1); tag.putLong("Revision", revision);
        tag.putLongArray("Positions", posts.stream().mapToLong(BlockPos::asLong).toArray());
        ListTag entries = new ListTag();
        assignments.forEach((id, pos) -> {
            CompoundTag entry = new CompoundTag(); entry.putInt("Citizen", id); entry.putLong("Post", pos.asLong()); entries.add(entry);
        });
        tag.put("Assignments", entries); return tag;
    }
    public void load(CompoundTag tag) {
        posts.clear(); assignments.clear(); checkedAt = Long.MIN_VALUE;
        revision = Math.max(0, tag.getLong("Revision"));
        if (tag.getInt("Version") != 1) return;
        for (long packed : tag.getLongArray("Positions")) {
            BlockPos pos = BlockPos.of(packed);
            if (!posts.contains(pos) && posts.size() < MAX_POSTS) posts.add(pos);
        }
        ListTag entries = tag.getList("Assignments", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(entries.size(), 128); i++) {
            CompoundTag entry = entries.getCompound(i); int id = entry.getInt("Citizen"); BlockPos pos = BlockPos.of(entry.getLong("Post"));
            if (id > 0 && posts.contains(pos)) assignments.put(id, pos);
        }
    }
}
