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
    private Socket serverConnection = null;

    public Connection(Socket clientsocket, String remoteIp, int remotePort) {
        this.clientsocket = clientsocket;
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
    }

    @Override
    public void run() {
        Log.i(TAG,"new connection " + clientsocket.getInetAddress().getHostName()+":"+clientsocket.getPort());
        try {
            serverConnection = new Socket(remoteIp, remotePort);
            MainActivity.cellNet.getSocketFactory().createSocket(remoteIp,remotePort);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        new Thread(new Forwarder(clientsocket, serverConnection)).start();
        new Thread(new Forwarder(serverConnection, clientsocket)).start();
        new Thread(() -> {
            while (true) {
                if (clientsocket.isClosed()) {
                    Log.i(TAG,"client socket ("+clientsocket.getInetAddress().getHostName()+":"+clientsocket.getPort()+") closed", null);
                    closeServerConnection();
                    break;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    private void closeServerConnection() {
        if (serverConnection != null && !serverConnection.isClosed()) {
            try {
                Log.i(TAG,"closing remote host connection "+serverConnection.getInetAddress().getHostName()+":"+serverConnection.getPort(),null);
                serverConnection.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
