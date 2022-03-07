package com.modima.forwarder.net;

import android.net.Network;
import android.os.Message;
import android.util.Log;

import com.modima.forwarder.MainActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class TCPConnection extends Connection {

    static final String TAG = TCPConnection.class.getName();

    private ServerSocket serverSocket;
    private boolean running;

    public TCPConnection(Connection.Type type, Network srcNet, Network dstNet, InetSocketAddress dstAddr) {
        super(type, Protocol.TCP);
        this.srcNet = srcNet;
        this.dstNet = dstNet;
        this.dstAddress = dstAddr;
    }

    public void stop() {
        this.running = false;
    }

    public int listen(int listenPort) throws IOException {
        this.listenPort = listenPort;
        if (listenPort >= 0) {
            this.serverSocket = new ServerSocket(listenPort);
        } else {
            this.serverSocket = new ServerSocket();
            listenPort = this.serverSocket.getLocalPort();
        }
        new Thread(() -> {
            while (running) {
                try {
                    Log.d(TAG, "WIFI TCP: listen on " + serverSocket.getLocalSocketAddress(), null);
                    Socket srcSocket = serverSocket.accept();
                    connect(srcSocket);
                } catch (SocketTimeoutException e) {
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                    //handler.sendMessage(handler.obtainMessage(MainActivity.MSG_ERROR, 0, 0, e.getMessage()));
                }
            }
        });
        return listenPort;
    }

    public Socket connect(Socket srcSocket) throws IOException {
        Socket dstSocket = dstNet.getSocketFactory().createSocket();
        dstSocket.connect(dstAddress);

        InputStream srcInputStream = srcSocket.getInputStream();
        OutputStream srcOutputStream = srcSocket.getOutputStream();
        InputStream dstInputStream = dstSocket.getInputStream();
        OutputStream dstOutputSteam = dstSocket.getOutputStream();

        // start src listener
        new Thread(() -> {
            try {
                int bytesRead;
                byte[] buffer = new byte[BUFFER_SIZE];
                while (-1 != (bytesRead = srcInputStream.read(buffer))) {
                    dstOutputSteam.write(buffer, 0, bytesRead);
                    bytesSent += bytesRead;
                    MainActivity.handler.sendMessage(MainActivity.handler.obtainMessage(MainActivity.MSG_UPDATE_UI));
                }
                dstOutputSteam.flush();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                Message m = MainActivity.handler.obtainMessage(MainActivity.MSG_UPDATE_UI);
                m.obj = this;
                MainActivity.handler.sendMessage(m);
            }
        }).start();

        // start dst listener
        new Thread(() -> {
            try {
                int bytesRead;
                byte[] buffer = new byte[BUFFER_SIZE];
                while (-1 != (bytesRead = dstInputStream.read(buffer))) {
                    srcOutputStream.write(buffer, 0, bytesRead);
                    bytesReceived += bytesRead;
                    MainActivity.handler.sendMessage(MainActivity.handler.obtainMessage(MainActivity.MSG_UPDATE_UI));
                }
                srcOutputStream.flush();
                // remove from view
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                Message m = MainActivity.handler.obtainMessage(MainActivity.MSG_UPDATE_UI);
                m.obj = this;
                MainActivity.handler.sendMessage(m);
            }
        }).start();

        return dstSocket;
    }
}
