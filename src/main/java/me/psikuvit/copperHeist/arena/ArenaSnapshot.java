package me.psikuvit.copperHeist.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A saved copy of every block inside an arena's bounds (a WorldEdit-free "schematic"). Written by
 * "arena snapshot", read back by the snapshot reset method. Stored as a palette of block-data strings
 * plus one palette index per block, gzip-compressed.
 */
public final class ArenaSnapshot {

    private final String worldName;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<String> palette;
    private final int[] indices;

    private ArenaSnapshot(String worldName, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
                          List<String> palette, int[] indices) {
        this.worldName = worldName;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = palette;
        this.indices = indices;
    }

    public int blockCount() {
        return indices.length;
    }

    /** Reads every block in the arena's bounds. Returns null if the bounds aren't set or the world isn't loaded. */
    public static ArenaSnapshot capture(Arena arena) {
        Location a = arena.getBound1();
        Location b = arena.getBound2();
        if (a == null || b == null || a.getWorld() == null) return null;
        World world = a.getWorld();

        int minX = Math.min(a.getBlockX(), b.getBlockX());
        int minY = Math.max(world.getMinHeight(), Math.min(a.getBlockY(), b.getBlockY()));
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        int maxX = Math.max(a.getBlockX(), b.getBlockX());
        int maxY = Math.min(world.getMaxHeight() - 1, Math.max(a.getBlockY(), b.getBlockY()));
        int maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;

        List<String> palette = new ArrayList<>();
        Map<String, Integer> lookup = new HashMap<>();
        int[] indices = new int[sizeX * sizeY * sizeZ];
        int i = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    String data = world.getBlockAt(minX + x, minY + y, minZ + z).getBlockData().getAsString();
                    Integer index = lookup.get(data);
                    if (index == null) {
                        index = palette.size();
                        palette.add(data);
                        lookup.put(data, index);
                    }
                    indices[i++] = index;
                }
            }
        }
        return new ArenaSnapshot(world.getName(), minX, minY, minZ, sizeX, sizeY, sizeZ, palette, indices);
    }

    public void write(File file) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file.toPath())))) {
            out.writeUTF(worldName);
            out.writeInt(minX);
            out.writeInt(minY);
            out.writeInt(minZ);
            out.writeInt(sizeX);
            out.writeInt(sizeY);
            out.writeInt(sizeZ);
            out.writeInt(palette.size());
            for (String entry : palette) out.writeUTF(entry);
            for (int index : indices) out.writeInt(index);
        }
    }

    public static ArenaSnapshot read(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file.toPath())))) {
            String world = in.readUTF();
            int minX = in.readInt();
            int minY = in.readInt();
            int minZ = in.readInt();
            int sizeX = in.readInt();
            int sizeY = in.readInt();
            int sizeZ = in.readInt();
            int paletteSize = in.readInt();
            List<String> palette = new ArrayList<>(paletteSize);
            for (int i = 0; i < paletteSize; i++) palette.add(in.readUTF());
            int[] indices = new int[sizeX * sizeY * sizeZ];
            for (int i = 0; i < indices.length; i++) indices[i] = in.readInt();
            return new ArenaSnapshot(world, minX, minY, minZ, sizeX, sizeY, sizeZ, palette, indices);
        }
    }

    /**
     * Puts back up to {@code limit} blocks starting at {@code from} (only where the world differs) and returns the
     * next position, so a caller can spread a big restore over several ticks.
     */
    public int restore(int from, int limit) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return indices.length;

        BlockData[] parsed = new BlockData[palette.size()];
        int end = Math.min(indices.length, from + limit);
        int layer = sizeX * sizeZ;
        for (int i = from; i < end; i++) {
            int y = i / layer;
            int rest = i % layer;
            int z = rest / sizeX;
            int x = rest % sizeX;
            BlockData wanted = parsed[indices[i]];
            if (wanted == null) {
                wanted = Bukkit.createBlockData(palette.get(indices[i]));
                parsed[indices[i]] = wanted;
            }
            var block = world.getBlockAt(minX + x, minY + y, minZ + z);
            if (!block.getBlockData().equals(wanted)) block.setBlockData(wanted, false);
        }
        return end;
    }
}
