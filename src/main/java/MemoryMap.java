/*
 * This file is part of ceserverj by Isabella Flores
 *
 * Copyright © 2021 Isabella Flores
 *
 * It is licensed to you under the terms of the
 * Apache License, Version 2.0. Please see the
 * file LICENSE for more information.
 */

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.TreeSet;

public class MemoryMap<T> implements Iterable<MemoryRegion<T>> {

    private final TreeSet<MemoryRegion<T>> _treeSet = new TreeSet<>();

    public void add(MemoryRegion<T> newEntry) {
        MemoryRegion<T> previousEntry = _treeSet.floor(newEntry);
        MemoryRegion<T> nextEntry = _treeSet.ceiling(newEntry);
        if (regionOverlaps(newEntry, previousEntry)) {
            throw new RuntimeException("Overlapping entries: " + newEntry + "/" + previousEntry);
        }
        if (regionOverlaps(newEntry, nextEntry)) {
            throw new RuntimeException("Overlapping entries: " + nextEntry + "/" + newEntry);
        }
        if (!_treeSet.add(newEntry)) {
            throw new RuntimeException("Duplicate memory range: " + newEntry);
        }
    }

    private boolean regionOverlaps(MemoryRegion<T> entry1, MemoryRegion<T> entry2) {
        return entry1 != null
               && entry2 != null
               && entry1.getRegionStart() <= entry2.getRegionEnd()
               && entry2.getRegionStart() <= entry1.getRegionEnd();
    }

    public MemoryRegion<T> getMemoryRegionContaining(long regionStart, long regionSize) {
        MemoryRegion<T> floorEntry = floor(regionStart);
        if (floorEntry != null && floorEntry.getRegionEnd() >= regionStart + regionSize - 1) {
            return floorEntry;
        }
        return null;
    }

    private MemoryRegion<T> floor(long regionStart) {
        return _treeSet.stream().filter(x -> x.getRegionStart() <= regionStart).reduce((first, second) -> second).orElse(null);
    }

    @NotNull
    @Override
    public Iterator<MemoryRegion<T>> iterator() {
        return _treeSet.iterator();
    }

}
