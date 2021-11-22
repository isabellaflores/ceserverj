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
import org.jetbrains.annotations.NotNull;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class ClientHandler extends Thread {

    private static final Map<Integer, SelectedProcess> _openProcesses = new HashMap<>();
    private static final Map<Integer, WinNT.HANDLE> _handlesById = new HashMap<>();
    private static final ReentrantLock _handleLock = new ReentrantLock();
    private static final Map<Integer, ClientHandler> _clients = new HashMap<>();
    private static int _nextHandleId = 1;
    private static int _nextClientId = 1;
    private final SocketChannel _socketChannel;
    private final int _clientId;
    private final String _clientIdString;

    public ClientHandler(SocketChannel socketChannel) throws IOException {
        _clientId = generateClientId();
        _clientIdString = "Client-" + String.format("%05d", _clientId);
        _socketChannel = socketChannel;
        String remoteAddress = ((InetSocketAddress) socketChannel.getRemoteAddress()).getAddress().getHostAddress();
        setName(this + " [" + remoteAddress + "]");
        log("Connection from " + remoteAddress);
    }

    private int generateClientId() {
        int clientId;
        while (true) {
            clientId = _nextClientId++;
            if (clientId > 99999) {
                clientId = 1;
            }
            synchronized (_clients) {
                if (_clients.putIfAbsent(clientId, this) == null) {
                    return clientId;
                }
            }
        }
    }

    private void log(String message) {
        Main.log(this, message);
    }

    @Override
    @NotNull
    public String toString() {
        return _clientIdString;
    }

    @Override
    public void run() {
        try {
            //noinspection InfiniteLoopStatement
            while (true) {
                ByteBuffer commandbuf = ByteBuffer.allocate(1);
                readFully(commandbuf);
                byte command = commandbuf.get(0);
                handleCommand(command);
            }
        } catch (EOFException ex) {
            // closed connection
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            synchronized (_clients) {
                _clients.remove(_clientId);
            }
            try {
                _socketChannel.close();
            } catch (Throwable t) {
                t.printStackTrace();
            }
            log("Connection closed.");
        }
    }

    private void handleCommand(byte command) throws IOException {
        switch (command) {
            case CommandConstants.CMD_CREATETOOLHELP32SNAPSHOT: {
                int dwFlags = readInt();
                int th32ProcessID = readInt();
                WinNT.HANDLE hSnapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
                        new WinDef.DWORD(dwFlags),
                        new WinDef.DWORD(th32ProcessID)
                );
                ByteBuffer result = ByteBuffer.allocate(4);
                result.order(ByteOrder.LITTLE_ENDIAN);
                result.putInt(0, generateHandleId(hSnapshot));
                writeFully(result);
                break;
            }
            case CommandConstants.CMD_PROCESS32FIRST:
            case CommandConstants.CMD_PROCESS32NEXT: {
                WinNT.HANDLE hSnapshot = getHandle(readInt());
                Tlhelp32.PROCESSENTRY32 lppe = new Tlhelp32.PROCESSENTRY32();
                boolean result = command == CommandConstants.CMD_PROCESS32FIRST
                        ? Kernel32.INSTANCE.Process32First(hSnapshot, lppe)
                        : Kernel32.INSTANCE.Process32Next(hSnapshot, lppe);
                if (result) {
                    writeCeProcessEntry(true, lppe.th32ProcessID.intValue(), nullTerminatedCharsToString(lppe.szExeFile));
                } else {
                    writeCeProcessEntry(false, 0, "");
                }
                break;
            }
            case CommandConstants.CMD_CLOSEHANDLE: {
                WinNT.HANDLE handle;
                _handleLock.lock();
                try {
                    int handleId = readInt();
                    handle = _handlesById.remove(handleId);
                    _openProcesses.remove(handleId);
                } finally {
                    _handleLock.unlock();
                }
                Win32Utils.closeHandle(handle);
                ByteBuffer buf = ByteBuffer.allocate(4);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                buf.putInt(0, 1);
                writeFully(buf);
                break;
            }
            case CommandConstants.CMD_OPENPROCESS: {
                int pid = readInt();
                int handleId = 0;
                try {
                    SelectedProcess selectedProcess = new SelectedProcess(pid);
                    _handleLock.lock();
                    try {
                        handleId = generateHandleId(selectedProcess.getProcessHandle());
                        _openProcesses.put(handleId, selectedProcess);
                    } finally {
                        _handleLock.unlock();
                    }
                } catch (WinApiException ex) {
                    ex.printStackTrace();
                }
                ByteBuffer buf = ByteBuffer.allocate(4);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                buf.putInt(0, handleId);
                writeFully(buf);
                break;
            }
            case CommandConstants.CMD_READPROCESSMEMORY: {
                ByteBuffer buf = ByteBuffer.allocate(17);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                readFully(buf);
                final int handleId = buf.getInt();
                final long address = buf.getLong();
                final int size = buf.getInt();
                final byte compress = buf.get();
                SelectedProcess selectedProcess = getOpenProcess(handleId);
                if (compress != 0) {
                    throw new IllegalArgumentException("Compression not yet supported");
                }
                if (selectedProcess != null && address >= 0L) {
                    byte[] bytes = selectedProcess.readMemory(address, size);
                    ByteBuffer memoryBuf = ByteBuffer.allocate(bytes.length + 4);
                    memoryBuf.order(ByteOrder.LITTLE_ENDIAN);
                    memoryBuf.putInt(bytes.length);
                    memoryBuf.put(bytes);
                    memoryBuf.flip();
                    writeFully(memoryBuf);
                } else {
                    ByteBuffer memoryBuf = ByteBuffer.allocate(4);
                    memoryBuf.order(ByteOrder.LITTLE_ENDIAN);
                    memoryBuf.putInt(0);
                    memoryBuf.flip();
                    writeFully(memoryBuf);
                }
                break;
            }
            case CommandConstants.CMD_WRITEPROCESSMEMORY: {
                ByteBuffer buf = ByteBuffer.allocate(16);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                readFully(buf);
                int handleId = buf.getInt();
                long address = buf.getLong();
                int size = buf.getInt();
                ByteBuffer memoryBuf = ByteBuffer.allocate(size);
                memoryBuf.order(ByteOrder.LITTLE_ENDIAN);
                readFully(memoryBuf);
                SelectedProcess selectedProcess = getOpenProcess(handleId);
                int total = 0;
                if (selectedProcess != null) {
                    selectedProcess.writeMemory(address, memoryBuf.array());
                }
                ByteBuffer responseBuffer = ByteBuffer.allocate(4);
                responseBuffer.order(ByteOrder.LITTLE_ENDIAN);
                responseBuffer.putInt(0, total);
                writeFully(responseBuffer);
                break;
            }
            case CommandConstants.CMD_GETARCHITECTURE: {
                WinBase.SYSTEM_INFO si = new WinBase.SYSTEM_INFO();
                Kernel32.INSTANCE.GetSystemInfo(si);
                byte result;
                int architecture = si.processorArchitecture.pi.wProcessorArchitecture.intValue();
                switch (architecture) {
                    case 0: // x86
                        result = 0;
                        break;
                    case 9: // x64 (AMD or Intel)
                        result = 1;
                        break;
                    case 5: // ARM
                        result = 2;
                        break;
                    case 12: // ARM64
                        result = 3;
                        break;
                    default:
                        throw new RuntimeException("Unsupported architecture: #" + architecture);
                }
                ByteBuffer buf = ByteBuffer.allocate(1);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                buf.put(0, result);
                writeFully(buf);
                break;
            }
            case CommandConstants.CMD_MODULE32FIRST:
            case CommandConstants.CMD_MODULE32NEXT: {
                int handleId = readInt();
                WinNT.HANDLE hModule = getHandle(handleId);
                Tlhelp32.MODULEENTRY32W lpme = new Tlhelp32.MODULEENTRY32W();
                boolean result = command == CommandConstants.CMD_MODULE32FIRST ?
                        Kernel32.INSTANCE.Module32FirstW(
                                hModule,
                                lpme
                        ) :
                        Kernel32.INSTANCE.Module32NextW(
                                hModule,
                                lpme
                        );
                if (result) {
                    writeCeModuleEntry(
                            true,
                            Win32Utils.getAddress(lpme.modBaseAddr),
                            lpme.modBaseSize.longValue(),
                            nullTerminatedCharsToString(lpme.szModule)
                    );
                } else {
                    writeCeModuleEntry(false, 0L, 0L, "");
                }
                break;
            }
            case CommandConstants.CMD_GETSYMBOLLISTFROMFILE: {
                int symbolPathSize = readInt();
                ByteBuffer buf = ByteBuffer.allocate(symbolPathSize);
                readFully(buf);
                ByteBuffer response = ByteBuffer.allocate(4);
                response.order(ByteOrder.LITTLE_ENDIAN);
                writeFully(response);
                break;
            }
            case CommandConstants.CMD_VIRTUALQUERYEX:
            case CommandConstants.CMD_GETREGIONINFO: {
                int handleId = readInt();
                long baseAddress = readLong();
                SelectedProcess selectedProcess = getOpenProcess(handleId);
                WinNT.MEMORY_BASIC_INFORMATION mbi = new WinNT.MEMORY_BASIC_INFORMATION();
                BaseTSD.SIZE_T result = Kernel32.INSTANCE.VirtualQueryEx(
                        selectedProcess.getProcessHandle(),
                        new Pointer(baseAddress),
                        mbi,
                        new BaseTSD.SIZE_T(mbi.size())
                );
                ByteBuffer response = ByteBuffer.allocate(25);
                response.order(ByteOrder.LITTLE_ENDIAN);
                if (result.longValue() != 0) {
                    response.put((byte) 1);
                    response.putInt(mbi.protect.intValue()); // protection
                    response.putInt(mbi.type.intValue()); // type
                    response.putLong(Win32Utils.getAddress(mbi.baseAddress));
                    response.putLong(mbi.regionSize.longValue());
                    response.flip();
                }
                writeFully(response);
                if (command == CommandConstants.CMD_GETREGIONINFO) {
                    String name = null;
                    try {
                        name = selectedProcess.getModuleName(mbi.baseAddress, mbi.regionSize.longValue());
                    } catch (WinApiException ex) {
                        ex.printStackTrace();
                    }
                    if (name == null) {
                        name = "";
                    }
                    byte[] nameBytes = name.getBytes(StandardCharsets.ISO_8859_1);
                    int numBytes = Math.min(name.length(), 127);
                    ByteBuffer buf = ByteBuffer.allocate(1 + numBytes);
                    buf.put((byte) numBytes);
                    buf.put(nameBytes, 0, numBytes);
                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    buf.flip();
                    writeFully(buf);
                }
                break;
            }
            case CommandConstants.CMD_VIRTUALQUERYEXFULL: {
                try {
                    int handleId = readInt();
                    //noinspection unused
                    byte flags = readByte(); // TODO: What is this used for?
                    SelectedProcess selectedProcess = getOpenProcess(handleId);

                    WinBase.SYSTEM_INFO si = new WinBase.SYSTEM_INFO();
                    Kernel32.INSTANCE.GetSystemInfo(si);

                    Pointer lpMem = new Pointer(0);
                    List<WinNT.MEMORY_BASIC_INFORMATION> results = new ArrayList<>();
                    while (Win32Utils.getAddress(lpMem) < Win32Utils.getAddress(si.lpMaximumApplicationAddress)) {
                        WinNT.MEMORY_BASIC_INFORMATION mbi = new WinNT.MEMORY_BASIC_INFORMATION();
                        BaseTSD.SIZE_T result = Kernel32.INSTANCE.VirtualQueryEx(
                                selectedProcess.getProcessHandle(),
                                lpMem,
                                mbi,
                                new BaseTSD.SIZE_T(mbi.size())
                        );
                        if (result.longValue() == 0) {
                            throw new WinApiException("Virtual memory query failed", Kernel32.INSTANCE.GetLastError());
                        }

                        results.add(mbi);

                        lpMem = new Pointer(Win32Utils.getAddress(mbi.baseAddress) + mbi.regionSize.longValue());
                    }

                    ByteBuffer response = ByteBuffer.allocate(4 + (results.size() * 24));
                    response.order(ByteOrder.LITTLE_ENDIAN);
                    response.putInt(results.size());
                    for (WinNT.MEMORY_BASIC_INFORMATION mbi : results) {
                        response.putLong(Win32Utils.getAddress(mbi.baseAddress));
                        response.putLong(mbi.regionSize.longValue());
                        response.putInt(mbi.protect.intValue());
                        response.putInt(mbi.type.intValue());
                    }
                    if (response.hasRemaining()) {
                        throw new IllegalStateException();
                    }
                    response.flip();
                    writeFully(response);
                } catch (WinApiException ex) {
                    ex.printStackTrace();
                }
                break;
            }
            default: {
                throw new RuntimeException("Got unknown command: " + command);
            }
        }
    }

    private SelectedProcess getOpenProcess(int handleId) {
        SelectedProcess selectedProcess;
        _handleLock.lock();
        try {
            selectedProcess = _openProcesses.get(handleId);
        } finally {
            _handleLock.unlock();
        }
        return selectedProcess;
    }

    private WinNT.HANDLE getHandle(int handleId) {
        _handleLock.lock();
        try {
            return _handlesById.get(handleId);
        } finally {
            _handleLock.unlock();
        }
    }

    private int generateHandleId(WinNT.HANDLE handle) {
        _handleLock.lock();
        try {
            int handleId;
            do {
                handleId = _nextHandleId++;
            }
            while (handleId == 0 || _handlesById.putIfAbsent(handleId, handle) != null);
            return handleId;
        } finally {
            _handleLock.unlock();
        }
    }

    @NotNull
    private String nullTerminatedCharsToString(char[] chars) {
        StringBuilder sb = new StringBuilder();
        for (char ch : chars) {
            if (ch == 0) {
                break;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    private byte readByte() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(1);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        readFully(buf);
        return buf.get(0);
    }

    private int readInt() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        readFully(buf);
        return buf.getInt(0);
    }

    private long readLong() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        readFully(buf);
        return buf.getLong(0);
    }

    private void writeCeProcessEntry(boolean hasNext, int pid, String processName) throws IOException {
        if (processName == null) {
            processName = "";
        }
        byte[] processNameBytes = processName.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(12 + processNameBytes.length);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(hasNext ? 1 : 0);
        buf.putInt(pid);
        buf.putInt(processNameBytes.length);
        buf.put(processNameBytes);
        buf.flip();
        writeFully(buf);
    }

    private void writeCeModuleEntry(boolean hasNext, long moduleBase, long moduleSize, String moduleName) throws IOException {
        if (moduleName == null) {
            moduleName = "";
        }
        if (moduleSize > 0xffffffffL) {
            throw new IllegalArgumentException();
        }
        byte[] moduleNameBytes = moduleName.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(20 + moduleNameBytes.length);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(hasNext ? 1 : 0);
        buf.putLong(moduleBase);
        buf.putInt((int) moduleSize);
        buf.putInt(moduleNameBytes.length);
        buf.put(moduleNameBytes);
        buf.flip();
        writeFully(buf);
    }

    private void writeFully(ByteBuffer result) throws IOException {
        if (result.order() != ByteOrder.LITTLE_ENDIAN) {
            throw new IllegalStateException();
        }
        while (result.hasRemaining()) {
            _socketChannel.write(result);
        }
    }

    private void readFully(ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            int count = _socketChannel.read(buf);
            if (count < 0) {
                throw new EOFException();
            }
        }
        buf.flip();
    }

}
