package com.modima.forwarder.tcp;

import android.util.Log;

import com.modima.forwarder.MainActivity;

import java.io.IOException;
import java.net.Socket;

public class Connection implements Runnable {

    static final String TAG = Connection.class.getName();

    private final Socket clientsocket;
    private final String remoteIp;
    private final int remotePort;
    private Socket targetServerSocket = null;

    public Connection(Socket clientsocket, String remoteIp, int remotePort) {
        this.clientsocket = clientsocket;
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
    }

    @Override
    public void run() {
        try {
            targetServerSocket = MainActivity.cellNet.getSocketFactory().createSocket(remoteIp, remotePort);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        Log.i(TAG, "create connection to " + targetServerSocket.getInetAddress().getHostName() + ":" + targetServerSocket.getPort());

        new Thread(new Forwarder(clientsocket, targetServerSocket)).start();
        new Thread(new Forwarder(targetServerSocket, clientsocket)).start();
        new Thread(() -> {
            while (true) {
                if (clientsocket.isClosed()) {
                    Log.i(TAG, "client socket (" + clientsocket.getInetAddress().getHostName() + ":" + clientsocket.getPort() + ") closed", null);
                    closeServerConnection();
                    break;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
        }).start();
    }

    private void closeServerConnection() {
        if (targetServerSocket != null && !targetServerSocket.isClosed()) {
            try {
                Log.i(TAG, "closing remote host connection " + targetServerSocket.getInetAddress().getHostName() + ":" + targetServerSocket.getPort(), null);
                targetServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
