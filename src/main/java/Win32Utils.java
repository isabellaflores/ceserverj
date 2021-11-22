/*
 * This file is part of ceserverj by Isabella Flores
 *
 * Copyright © 2021 Isabella Flores
 *
 * It is licensed to you under the terms of the
 * Apache License, Version 2.0. Please see the
 * file LICENSE for more information.
 */

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Win32Utils {

    private static final Field POINTER_PEER_FIELD;

    static {
        try {
            POINTER_PEER_FIELD = Pointer.class.getDeclaredField("peer");
            POINTER_PEER_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    public static String getProcessName(WinNT.HANDLE hProcess) throws WinApiException {
        char[] buffer = new char[1024];
        int numChars = Psapi.INSTANCE.GetProcessImageFileName(hProcess, buffer, buffer.length);
        if (numChars == 0) {
            throw new WinApiException("Unable to get process name", Kernel32.INSTANCE.GetLastError());
        }
        return new String(buffer, 0, numChars);
    }

    @NotNull
    public static String getModuleName(WinNT.HANDLE hProcess, WinDef.HMODULE hModule) throws WinApiException {
        char[] buffer = new char[1024];
        int numChars = Psapi.INSTANCE.GetModuleFileNameExW(hProcess, hModule, buffer, buffer.length);
        if (numChars == 0) {
            throw new WinApiException("Unable to get module name", Kernel32.INSTANCE.GetLastError());
        }
        return new String(buffer, 0, numChars);
    }

    @NotNull
    public static WinNT.HANDLE openProcess(int pid) throws WinApiException {
        WinNT.HANDLE hProcess = Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_ALL_ACCESS,
                false,
                pid
        );
        if (hProcess == null) {
            throw new WinApiException("Unable to open process " + pid, Kernel32.INSTANCE.GetLastError());
        }
        return hProcess;
    }

    public static Set<WinDef.HMODULE> getProcessModules(WinNT.HANDLE processHandle) {
        int arraySize = 1024;
        while (true) {
            IntByReference lpcbNeeded = new IntByReference();
            WinDef.HMODULE[] array = new WinDef.HMODULE[arraySize];
            CustomPsapi.INSTANCE.EnumProcessModulesEx(
                    processHandle,
                    array,
                    arraySize,
                    lpcbNeeded,
                    0x03 // all modules
            );
            if (lpcbNeeded.getValue() <= arraySize) {
                return new HashSet<>(Arrays.asList(array).subList(0, lpcbNeeded.getValue()));
            }
            arraySize = Math.max(lpcbNeeded.getValue() * 2, arraySize * 2);
        }
    }

    public static void throwLastWin32Exception() {
        throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
    }

    public static void closeHandle(WinNT.HANDLE handle) {
        Kernel32.INSTANCE.CloseHandle(handle);
    }

    public static long getAddress(Pointer ptr) {
        try {
            return ptr == null ? 0L : (long) POINTER_PEER_FIELD.get(ptr);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
