/*
 * This file is part of ceserverj by Isabella Flores
 *
 * Copyright © 2021 Isabella Flores
 *
 * It is licensed to you under the terms of the
 * Apache License, Version 2.0. Please see the
 * file LICENSE for more information.
 */

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

import java.util.Set;

public class SelectedProcess {

    private final String _executableName;
    private final WinNT.HANDLE _processHandle;
    private MemoryMap<String> _memoryMap;

    public SelectedProcess(int pid) throws WinApiException {
        _processHandle = Win32Utils.openProcess(pid);
        String processName;
        try {
            processName = Win32Utils.getProcessName(_processHandle);
        } catch (WinApiException e) {
            processName = e.toString();
        }
        _executableName = processName;
    }

    public byte[] readMemory(long address, int size) {
        if (size == 0) {
            return new byte[0];
        }
        IntByReference lpNumberOfBytesRead = new IntByReference();
        long buf = Native.malloc(size);
        try {
            Pointer lpBuffer = new Pointer(buf);
            Kernel32.INSTANCE.ReadProcessMemory(
                    getProcessHandle(),
                    new Pointer(address),
                    lpBuffer,
                    size,
                    lpNumberOfBytesRead
            );
            int numBytesRead = lpNumberOfBytesRead.getValue();
            byte[] result = new byte[numBytesRead];
            if (numBytesRead > 0) {
                lpBuffer.read(0, result, 0, numBytesRead);
            }
            return result;
        } finally {
            Native.free(buf);
        }
    }

    public void writeMemory(long address, byte[] bytes) {
        if (bytes.length == 0) {
            return;
        }
        long buf = Native.malloc(bytes.length);
        try {
            Pointer lpBuffer = new Pointer(buf);
            lpBuffer.write(0, bytes, 0, bytes.length);
            boolean result = Kernel32.INSTANCE.WriteProcessMemory(
                    getProcessHandle(),
                    new Pointer(address),
                    lpBuffer,
                    bytes.length,
                    null
            );
            if (!result) {
                Win32Utils.throwLastWin32Exception();
            }
        } finally {
            Native.free(buf);
        }
    }

    @Override
    public String toString() {
        return _executableName;
    }

    public WinNT.HANDLE getProcessHandle() {
        return _processHandle;
    }

    public synchronized String getModuleName(Pointer baseAddress, long regionSize) throws WinApiException {
        if (_memoryMap == null) {
            MemoryMap<String> memoryMap = new MemoryMap<>();
            Set<WinDef.HMODULE> modules = Win32Utils.getProcessModules(_processHandle);
            for (WinDef.HMODULE module : modules) {
                Psapi.MODULEINFO lpmodinfo = new Psapi.MODULEINFO();
                boolean ok = Psapi.INSTANCE.GetModuleInformation(
                        _processHandle,
                        module,
                        lpmodinfo,
                        lpmodinfo.size()
                );
                if (ok) {
                    String moduleName = Win32Utils.getModuleName(_processHandle, module);
                    MemoryRegion<String> memoryRegion = new MemoryRegion<>(
                            moduleName,
                            Win32Utils.getAddress(lpmodinfo.lpBaseOfDll),
                            lpmodinfo.SizeOfImage
                    );
                    memoryMap.add(memoryRegion);
                }
            }
            _memoryMap = memoryMap;
        }
        MemoryRegion<String> memoryRegionContaining = _memoryMap.getMemoryRegionContaining(Win32Utils.getAddress(baseAddress), regionSize);
        return memoryRegionContaining == null ? null : memoryRegionContaining.getUserObject();
    }
}
