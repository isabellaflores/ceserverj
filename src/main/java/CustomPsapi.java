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
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.WinDef.HMODULE;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;

public interface CustomPsapi extends Psapi {

    CustomPsapi INSTANCE = Native.load("psapi", CustomPsapi.class, W32APIOptions.DEFAULT_OPTIONS);

    void EnumProcessModulesEx(HANDLE hProcess, HMODULE[] lphModule, int cb,
                              IntByReference lpcbNeeded, int dwFilterFlag);

}