package com.modima.forwarder.net;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import androidx.annotation.Nullable;

import com.modima.forwarder.MainActivity;
import com.modima.forwarder.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class SocksProxy extends Service implements Serializable {

    static final String TAG = SocksProxy.class.getName();
    public static final int BUFFER_SIZE = 4096;
    private ServerSocket srcSocket;
    private boolean running;

    public SocksProxy() {
    }

    protected int byte2int(byte b) {
        return b & 0xff;
    }

    public static String bytes2String(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ ");
        for (byte b : bytes) {
            sb.append(String.format("0x%02X ", b));
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            int port = intent.getIntExtra("port", R.string.socks_port);
            Log.e(TAG, "start socks proxy on port " + port);
            if (this.srcSocket == null || this.srcSocket.isClosed() || this.srcSocket.getLocalPort() != port) {
                this.stop();
                if (this.srcSocket != null) this.srcSocket.close();
                this.srcSocket = new ServerSocket(port);
                this.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Service.START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void start() {
        this.running = true;
        new Thread(() -> {
            try {
                while (running) {
                    Log.e("TAG", "--- start ---" + this.srcSocket.getLocalPort());
                    Socket s = this.srcSocket.accept();
                    Log.e("TAG", "--- handle connection ---");
                    this.handleConnection(s);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    this.srcSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void stop() {
        running = false;
    }

    public void handleConnection(Socket srcSocket) {
        new Thread(() -> {
            try {
                //Log.e(TAG, "handle connection");
                byte[] buff = new byte[BUFFER_SIZE];
                InputStream clientInStream = srcSocket.getInputStream();
                OutputStream clientOutStream = srcSocket.getOutputStream();

                // 1st byte version 0x05
                clientInStream.read(buff, 0, 1); // must be 0x05
                //Log.e(TAG, "v:" + buff[0]);

                // 2nd byte count of supported auth methods
                clientInStream.read(buff, 0, 1); // must be 0x05
                //Log.e(TAG, "n:" + buff[0]);

                // read codes of supported auth methods (one byte per method)
                int n = buff[0];
                while (n > 0) {
                    clientInStream.read(buff, 0, 1);
                    n--;
                }

                // send 0x0 (no authentication required)
                byte[] firstAckMessage = new byte[]{5, 0};
                clientOutStream.write(firstAckMessage);
                clientOutStream.flush();

                // connection request
                // 1st byte version
                clientInStream.read(buff, 0, 1);
                // 2nd byte command
                clientInStream.read(buff, 0, 1);
                byte command = buff[0];
                //Log.e(TAG, "!!!! CMD: " + buff[0]);

                // 3rd byte reserved (0x0)
                clientInStream.read(buff, 0, 1);

                // 4th byte target address type
                String domainName = "";
                String targetIP = "";
                byte[] addressBytes;
                clientInStream.read(buff, 0, 1);
                switch (buff[0]) {
                    case 1: // IP v4
                        addressBytes = new byte[4];
                        clientInStream.read(addressBytes, 0, addressBytes.length);
                        targetIP = InetAddress.getByAddress(addressBytes).getHostAddress();
                        break;
                    case 3: // domain name
                        clientInStream.read(buff, 0, 1); // read length
                        int len = buff[0];
                        clientInStream.read(buff, 0, len); // read domain name
                        domainName = new String(buff, 0, len);
                        targetIP = MainActivity.cellNet.getByName(domainName).getHostAddress();
                        break;
                    case 4: // IP v6
                        addressBytes = new byte[16];
                        clientInStream.read(buff, 0, addressBytes.length);
                        targetIP = InetAddress.getByAddress(addressBytes).getHostAddress();
                        break;
                }

                // read destination port (Big-Endian)^
                clientInStream.read(buff, 0, 2);
                int port = byte2int(buff[0]) * 256 + byte2int(buff[1]);

                // create socket to target server
                byte[] localIPBytes;
                switch (command) {
                    case 1:
                        // create TCP Connection
                        Log.d(TAG, "TCP Connection to " + domainName + " " + targetIP + ":" + port + " requested");
                        TCPConnection tcpCon = new TCPConnection(Connection.Type.SOCKS5, MainActivity.wifiNet, MainActivity.cellNet, new InetSocketAddress(targetIP, port));
                        tcpCon.listenPort = this.srcSocket.getLocalPort();
                        Socket dstSocket = tcpCon.connect(srcSocket);
                        Message mTCP = MainActivity.handler.obtainMessage(MainActivity.MSG_ADD_CONNECTION);
                        mTCP.obj = tcpCon;
                        MainActivity.handler.sendMessage(mTCP);

                        //Log.e(TAG, "Local TCP Address: " + dstSocket.getLocalSocketAddress().toString());
                        localIPBytes = dstSocket.getLocalAddress().getAddress();
                        int tcpPort = dstSocket.getLocalPort();
                        sendResponse(srcSocket, localIPBytes, tcpPort, (byte) 0);
                        break;
                    case 2:
                        Log.e(TAG, "TCP Server requested (not yet supported");
                        sendResponse(srcSocket, new byte[]{0, 0, 0, 0}, 0, (byte) 7);
                        break;
                    case 3:
                        // create UDP Forwarding
                        Log.d(TAG, "UDP Forwarding requested");
                        UDPConnection udpCon = new UDPConnection(Connection.Type.SOCKS5, MainActivity.wifiNet, MainActivity.cellNet, new InetSocketAddress(targetIP, port));
                        int udpPort = udpCon.listen(-1);
                        Message mUDP = MainActivity.handler.obtainMessage(MainActivity.MSG_ADD_CONNECTION);
                        mUDP.obj = udpCon;
                        MainActivity.handler.sendMessage(mUDP);
                        sendResponse(srcSocket, MainActivity.wifiAddress.getAddress(), udpPort, (byte) 0);
                        // remove UDP Forwarding when main TCP socket is closed
                        while (!(srcSocket.isClosed())) {
                            if (clientInStream.read() == -1) {
                                Message m = MainActivity.handler.obtainMessage(MainActivity.MSG_UPDATE_UI);
                                m.obj = udpCon;
                                MainActivity.handler.sendMessage(m);
                                srcSocket.close();
                            }
                            Thread.sleep(100);
                        }
                        break;
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                sendResponse(srcSocket, new byte[]{0, 0, 0, 0}, 0, (byte) 1);
            }
        }).start();
    }

    protected void sendResponse(Socket socket, byte[] localIP, int localPort, byte respCode) {

        //Log.d(TAG, "Server socket " + InetAddress.getByAddress(localIP).getHostAddress() + ":" + localPort);

        int len = localIP.length + 6;
        byte[] response = new byte[len];
        response[0] = 5;        // version socks5
        response[1] = respCode; // response code
        response[2] = 0;        // reserved

        //Log.e("TAG", "IP len: " + localIP.length);

        if (localIP.length == 4) {
            response[3] = 1;        // address type IPv4
            for (int i = 0; i < localIP.length; i++) {
                response[4 + i] = localIP[i];
            }
        } else if (localIP.length == 16) {
            response[3] = 4;        // address type IPv6
            for (int i = 0; i < localIP.length; i++) {
                response[4 + i] = localIP[i];
            }
        } else {
            Log.e("TAG", "Long IP (maybe domain name)");
        }
        response[len - 2] = (byte) (localPort >> 8);   // high byte port
        response[len - 1] = (byte) (localPort & 0xff); // low byte port

        //Log.d(TAG, "socks response: " + bytes2String(response));
        try {
            OutputStream os = socket.getOutputStream();
            os.write(response, 0, len);
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}