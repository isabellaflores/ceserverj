/*
 * This file is part of ceserverj by Isabella Flores
 *
 * Copyright © 2021 Isabella Flores
 *
 * It is licensed to you under the terms of the
 * Apache License, Version 2.0. Please see the
 * file LICENSE for more information.
 */

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

public class Main {

    public static final int DEFAULT_PORT_NUMBER = 52736;
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    private static int _port;

    public static void main(String[] args) {
        try {
            switch (args.length) {
                case 0: {
                    _port = DEFAULT_PORT_NUMBER;
                    break;
                }
                case 1: {
                    _port = Integer.parseInt(args[0]);
                    break;
                }
                default: {
                    System.err.println("Too many arguments.");
                    System.exit(-1);
                }
            }
            ServerSocketChannel ss = ServerSocketChannel.open();
            ss.bind(new InetSocketAddress(_port));
            String versionString = "UNKNOWN";
            URL jarUrl = Main.class.getProtectionDomain().getCodeSource().getLocation();
            URLConnection urlConnection = jarUrl.openConnection();
            try (InputStream stream = urlConnection.getInputStream()) {
                try (JarInputStream jarInputStream = new JarInputStream(stream)) {
                    Manifest manifest = jarInputStream.getManifest();
                    if (manifest != null) {
                        Attributes attributes = manifest.getMainAttributes();
                        versionString = attributes.getValue("Implementation-Version");
                    }
                }
            }
            log(null, "Running on port " + _port + " (version " + versionString + ")");
            //noinspection InfiniteLoopStatement
            while (true) {
                SocketChannel socketChannel = ss.accept();
                ClientHandler clientHandler = new ClientHandler(socketChannel);
                clientHandler.start();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            System.exit(-1);
        }
    }

    public static void log(ClientHandler clientHandler, String message) {
        synchronized (System.out) {
            System.out.println(
                    "["
                    + SIMPLE_DATE_FORMAT.format(System.currentTimeMillis())
                    + " "
                    + (clientHandler == null ? "---SYSTEM---" : clientHandler)
                    + "] "
                    + message
            );
        }
    }
}
