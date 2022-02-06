package com.modima.forwarder.forwarding;

import android.util.Log;

import com.modima.forwarder.Forwarder;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.Callable;

/**
 * Created by Niall McShane on 21/02/2016.
 * <p>
 * Credit: https://alexapps.net/single-threaded-port-forwarding-utility-/
 */
public class TcpForwarder extends Forwarder implements Callable<Void> {

    private static final String TAG = "TcpForwarder";
    private static final int BUFFER_SIZE = 100000;

    public TcpForwarder(InetSocketAddress form, InetSocketAddress to, String ruleName) {
        super("TCP", form, to, ruleName);
    }

    public Void call() throws IOException, BindException {

        Log.d(TAG, String.format(super.START_MESSAGE, protocol, from.getPort(), to.getPort()));

        try {
            Selector selector = Selector.open();

            ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);

            ServerSocketChannel listening = ServerSocketChannel.open();
            listening.configureBlocking(false);

            try {
                listening.socket().bind(this.from, 0);
            } catch (java.net.BindException e) {
                Log.e(TAG, String.format(super.BIND_FAILED_MESSAGE, from.getPort(), protocol, ruleName), e);
                throw new BindException(String.format(super.BIND_FAILED_MESSAGE, from.getPort(), protocol, ruleName), e);
            } catch (java.net.SocketException e) {
                Log.e(TAG, String.format(super.BIND_FAILED_MESSAGE, from.getPort(), protocol, ruleName), e);
                throw new BindException(String.format(super.BIND_FAILED_MESSAGE, from.getPort(), protocol, ruleName), e);
            }

            listening.register(selector, SelectionKey.OP_ACCEPT, listening);

            while (true) {

                if (Thread.currentThread().isInterrupted()) {
                    Log.i(TAG, String.format(super.THREAD_INTERRUPT_CLEANUP_MESSAGE, protocol));
                    listening.close();
                    break;
                }

                int count = selector.select();
                if (count > 0) {
                    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                    while (it.hasNext()) {

                        SelectionKey key = it.next();
                        it.remove();

                        if (key.isValid() && key.isAcceptable()) {
                            processAcceptable(key, to);
                        }

                        if (key.isValid() && key.isConnectable()) {
                            processConnectable(key);
                        }

                        if (key.isValid() && key.isReadable()) {
                            processReadable(key, readBuffer);
                        }

                        if (key.isValid() && key.isWritable()) {
                            processWritable(key);
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Problem opening Selector", e);
            throw e;
        }

        return null;
    }

    private static void registerReads(
            Selector selector,
            SocketChannel socket,
            SocketChannel forwardToSocket) throws ClosedChannelException {
        RoutingPair pairFromToPair = new RoutingPair();
        pairFromToPair.from = socket;
        pairFromToPair.to = forwardToSocket;
        pairFromToPair.from.register(selector, SelectionKey.OP_READ, pairFromToPair);

        RoutingPair pairToFromPair = new RoutingPair();
        pairToFromPair.from = forwardToSocket;
        pairToFromPair.to = socket;
        pairToFromPair.from.register(selector, SelectionKey.OP_READ, pairToFromPair);
    }

    private static void processWritable(
            SelectionKey key) throws IOException {

        RoutingPair pair = (RoutingPair) key.attachment();

        pair.writeBuffer.flip();
        pair.to.write(pair.writeBuffer);

        if (pair.writeBuffer.remaining() > 0) {
            pair.writeBuffer.compact();
        } else {
            key.interestOps(SelectionKey.OP_READ);
            pair.writeBuffer.clear();
        }
    }

    private static void processReadable(
            SelectionKey key,
            ByteBuffer readBuffer) throws IOException {

        readBuffer.clear();
        RoutingPair pair = (RoutingPair) key.attachment();

        int r = 0;
        try {
            r = pair.from.read(readBuffer);
        } catch (IOException e) {
            key.cancel();
            System.out.println("Connection closed: " + key.channel());
        }
        if (r <= 0) {
            pair.from.close();
            pair.to.close();
            key.cancel();
            System.out.println("Connection closed: " + key.channel());
        } else {
            readBuffer.flip();
            pair.to.write(readBuffer);

            if (readBuffer.remaining() > 0) {
                pair.writeBuffer.put(readBuffer);
                key.interestOps(SelectionKey.OP_WRITE);
            }
        }
    }

    private static void processConnectable(
            SelectionKey key) throws IOException {
        SocketChannel from = (SocketChannel) key.attachment();
        SocketChannel forwardToSocket = (SocketChannel) key.channel();

        forwardToSocket.finishConnect();
        forwardToSocket.socket().setTcpNoDelay(true);
        registerReads(key.selector(), from, forwardToSocket);
    }

    private static void processAcceptable(
            SelectionKey key,
            InetSocketAddress forwardToAddress) throws IOException {
        SocketChannel from = ((ServerSocketChannel) key.attachment()).accept();
        System.out.println("Accepted " + from.socket());
        from.socket().setTcpNoDelay(true);
        from.configureBlocking(false);

        SocketChannel forwardToSocket = SocketChannel.open();
        forwardToSocket.configureBlocking(false);

        boolean connected = forwardToSocket.connect(forwardToAddress);
        if (connected) {
            forwardToSocket.socket().setTcpNoDelay(true);
            registerReads(key.selector(), from, forwardToSocket);
        } else {
            forwardToSocket.register(key.selector(), SelectionKey.OP_CONNECT, from);
        }
    }

    static class RoutingPair {
        SocketChannel from;
        SocketChannel to;
        ByteBuffer writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    }
}