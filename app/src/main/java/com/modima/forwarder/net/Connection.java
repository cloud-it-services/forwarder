package com.modima.forwarder.net;

import android.net.Network;

import java.io.IOException;
import java.net.InetSocketAddress;

public abstract class Connection {

    public static final int BUFFER_SIZE = 4096;
    public static enum Type {
        STATIC,
        SOCKS5
    }

    public static enum Protocol {
        UDP,
        TCP
    }

    public Type type;
    public Protocol proto;
    public int listenPort;
    public InetSocketAddress dstAddress;
    public int bytesSent;
    public int bytesReceived;

    protected Network srcNet;
    protected Network dstNet;

    public Connection(Type type, Protocol proto){
        this.type = type;
        this.proto = proto;
    }

    public abstract int listen(int port) throws IOException;
}
