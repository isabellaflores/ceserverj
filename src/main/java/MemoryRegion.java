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

public class MemoryRegion<T> implements Comparable<MemoryRegion<T>> {

    @NotNull
    private final T _userObject;
    private final long _regionStart;
    private final long _size;

    public MemoryRegion(@NotNull T userObject, long regionStart, long size) {
        _userObject = userObject;
        _regionStart = regionStart;
        _size = size;
    }

    @NotNull
    public T getUserObject() {
        return _userObject;
    }

    public long getRegionStart() {
        return _regionStart;
    }

    @Override
    public int compareTo(@NotNull MemoryRegion o) {
        return Long.compare(_regionStart, o._regionStart);
    }

    public long getRegionEnd() {
        return _regionStart + _size - 1;
    }

    @Override
    public String toString() {
        return "[start=" + _regionStart + ", size=" + _size + "]";
    }
}
