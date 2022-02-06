package com.modima.forwarder.forwarding;

import android.util.Log;

import com.modima.forwarder.Forwarder;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.Callable;

/**
 * Skeleton taken from: http://cs.ecs.baylor.edu/~donahoo/practical/JavaSockets2/code/UDPEchoServerSelector.java
 * <p>
 * Created by Niall McShane on 21/02/2016.
 */
public class UdpForwarder extends Forwarder implements Callable<Void> {

    private static final String TAG = "UdpForwarder";
    private static final int BUFFER_SIZE = 100000;

    private static final int TIMEOUT = 3000; // Wait timeout (milliseconds)

    public UdpForwarder(InetSocketAddress form, InetSocketAddress to, String ruleName) {
        super("UDP", form, to, ruleName);
    }

    public Void call() throws IOException, BindException {

        Log.d(TAG, String.format(super.START_MESSAGE, protocol, from.getPort(), to.getPort()));

        try {
            ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);

            DatagramChannel inChannel = DatagramChannel.open();
            inChannel.configureBlocking(false);

            try {
                inChannel.socket().bind(this.from);
            } catch (SocketException e) {
                Log.e(TAG, String.format(super.BIND_FAILED_MESSAGE, from.getPort(), protocol, ruleName), e);
                throw new BindException(String.format(super.BIND_FAILED_MESSAGE, from.getPort(), protocol, ruleName), e);
            }

            Selector selector = Selector.open();
            inChannel.register(selector, SelectionKey.OP_READ, new ClientRecord(to));

            while (true) { // Run forever, receiving and echoing datagrams

                if (Thread.currentThread().isInterrupted()) {
                    Log.i(TAG, String.format(super.THREAD_INTERRUPT_CLEANUP_MESSAGE, protocol));
                    inChannel.socket().close();
                    break;
                }

                int count = selector.select();
                if (count > 0) {


                    // Get iterator on set of keys with I/O to process
                    Iterator<SelectionKey> keyIter = selector.selectedKeys().iterator();
                    while (keyIter.hasNext()) {
                        SelectionKey key = keyIter.next(); // Key is bit mask

                        // Client socket channel has pending data?
                        if (key.isReadable()) {
                            // Log.i(TAG, "Have Something to READ");
                            handleRead(key, readBuffer);
                        }

                        // Client socket channel is available for writing and
                        // key is valid (i.e., channel not closed).
                        if (key.isValid() && key.isWritable()) {
                            // Log.i(TAG, "Have Something to WRITE");
                            handleWrite(key);
                        }

                        keyIter.remove();
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Problem opening Selector", e);
            throw e;
        }

        return null;
    }

    public static void handleRead(SelectionKey key, ByteBuffer readBuffer) throws IOException {

        // Log.i("UdpForwarder", "Handling Read");
        DatagramChannel channel = (DatagramChannel) key.channel();
        ClientRecord clientRecord = (ClientRecord) key.attachment();

        // Ensure the buffer is empty
        readBuffer.clear();

        // Receive the data
        channel.receive(readBuffer);

        // Get read to wrte, then send
        readBuffer.flip();
        channel.send(readBuffer, clientRecord.toAddress);

        // If there is anything remaining in the buffer
        if (readBuffer.remaining() > 0) {
            clientRecord.writeBuffer.put(readBuffer);
            key.interestOps(SelectionKey.OP_WRITE);
        }

//        ClientRecord clientRecord = (ClientRecord) key.attachment();
//        clientRecord.buffer.clear();    // Prepare buffer for receiving
//        clientRecord.clientAddress = channel.receive(clientRecord.buffer);
//
//        if (clientRecord.clientAddress != null) {  // Did we receive something?
//            // Register write with the selector
//            key.interestOps(SelectionKey.OP_WRITE);
//        }
    }

    public static void handleWrite(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();
        ClientRecord clientRecord = (ClientRecord) key.attachment();
        clientRecord.writeBuffer.flip(); // Prepare buffer for sending
        int bytesSent = channel.send(clientRecord.writeBuffer, clientRecord.toAddress);


        if (clientRecord.writeBuffer.remaining() > 0) {
            clientRecord.writeBuffer.compact();
        } else {
            key.interestOps(SelectionKey.OP_READ);
            clientRecord.writeBuffer.clear();
        }

//        if (bytesSent != 0) { // Buffer completely written?
//            // No longer interested in writes
//            key.interestOps(SelectionKey.OP_READ);
//        }
    }

    static class ClientRecord {
        public SocketAddress toAddress;
        public ByteBuffer writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        public ClientRecord(SocketAddress toAddress) {
            this.toAddress = toAddress;
        }
    }

}
