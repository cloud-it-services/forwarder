package com.modima.forwarder.socks;

import android.util.Log;

import com.modima.forwarder.MainActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class SocksServer extends Thread {

    static final String TAG = SocksServer.class.getName();

    private Socket socket;
    private int BUFF_SIZE = 4096;

    public SocksServer(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            InputStream clientInStream = socket.getInputStream();
            OutputStream clientOutStream = socket.getOutputStream();
            byte[] buff = new byte[BUFF_SIZE];
            int rc;
            ByteArrayOutputStream byteArrayOutputStream;

            // 1st byte version 0x05
            clientInStream.read(buff, 0, 1); // must be 0x05
            Log.e(TAG, "v:"+buff[0]);

            // 2nd byte count of supported auth methods
            clientInStream.read(buff, 0, 1); // must be 0x05
            Log.e(TAG, "n:"+buff[0]);

            // read codes of supported auth methods (one byte per method)
            int n=buff[0];
            while (n>0) {
                clientInStream.read(buff,0,1);
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
            // 3rd byte reserved (0x0)
            clientInStream.read(buff, 0, 1);

            // 4th byte target address type
            String domainName = "";
            String targetIP = "";
            clientInStream.read(buff, 0, 1);
            if (buff[0] == 0x01) { // IPv4
                byte[] ipBytes = new byte[4];
                clientInStream.read(ipBytes, 0, ipBytes.length);
                targetIP = InetAddress.getByAddress(ipBytes).getHostAddress();
            } else if (buff[0] == 0x03) { // domain name
                clientInStream.read(buff, 0, 1); // read length
                int len = buff[0];
                clientInStream.read(buff, 0, len); // read domain name
                domainName = new String(buff,0,len);
                targetIP = MainActivity.cellNet.getByName(domainName).getHostAddress();
            } else { // IPv6
                byte[] ipBytes = new byte[16];
                clientInStream.read(buff, 0, ipBytes.length);
                targetIP = InetAddress.getByAddress(ipBytes).getHostAddress();
            }

            // read destination port (Big-Endian)
            clientInStream.read(buff, 0, 2);
            int port = byte2int(buff[0]) * 256 + byte2int(buff[1]);

            Log.d(TAG, "Connected to " + domainName + " " + targetIP + ":" + port);

            // create socket to target server
            Socket outerSocket = MainActivity.cellNet.getSocketFactory().createSocket(targetIP,port);
            InputStream targetInStream = outerSocket.getInputStream();
            OutputStream targetOutStream = outerSocket.getOutputStream();

            byte[] localIP = outerSocket.getLocalAddress().getAddress();
            int localPort = outerSocket.getLocalPort();

            Log.d(TAG, "Server socket " + localIP + ":" + localPort);

            byte[] secondAckMessage = new byte[10];
            secondAckMessage[0] = 5;        // version socks5
            secondAckMessage[1] = 0;        // response code (succeeded)
            secondAckMessage[2] = 0;        // reserved
            secondAckMessage[3] = 1;        // address type IPv4
            secondAckMessage[4] = localIP[0];   // 1st byte of targetIP
            secondAckMessage[5] = localIP[1];   // 2nd byte of targetIP
            secondAckMessage[6] = localIP[2];   // 3rd byte of targetIP
            secondAckMessage[7] = localIP[3];   // 4th byte of targetIP
            secondAckMessage[8] = (byte) (localPort >> 8);   // high byte port
            secondAckMessage[9] = (byte) (localPort & 0xff); // low byte port

            Log.d(TAG, "send ACK " + secondAckMessage.length);
            clientOutStream.write(secondAckMessage, 0, 10);
            clientOutStream.flush();

            // handle response from target server
            new SocksResponseThread(targetInStream, clientOutStream).start();

            // send request to target server
            byte[] request = new byte[4096];
            int bytesRead;
            while (-1 != (bytesRead = clientInStream.read(request))) {
                targetOutStream.write(request, 0, bytesRead);
                Log.d(TAG, bytesRead + " bytes sent");
            }
            targetOutStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public int byte2int(byte b) {
        return b & 0xff;
    }
}
