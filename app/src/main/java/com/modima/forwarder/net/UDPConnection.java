package com.modima.forwarder.net;

import android.net.Network;
import android.util.Log;

import com.modima.forwarder.MainActivity;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;

public class UDPConnection extends Connection{

    static final String TAG = UDPConnection.class.getName();

    private DatagramSocket srcSocket;
    private InetAddress srcAddress;
    private int srcPort;

    public UDPConnection(Connection.Type type, Network srcNet, Network dstNet, InetSocketAddress dstAddr) {
        super(type, Protocol.UDP);
        this.srcNet = srcNet;
        this.dstNet = dstNet;
        this.dstAddress = dstAddr;
    }

    @Override
    public int listen(int listenPort) throws IOException {
        this.listenPort = listenPort;
        if (listenPort <= 0) {
            this.srcSocket = new DatagramSocket();
            this.listenPort = this.srcSocket.getLocalPort();
        } else {
            this.srcSocket = new DatagramSocket(this.listenPort);
        }
        srcNet.bindSocket(srcSocket);
        switch (this.type) {
            case STATIC:
                handleStaticConnection(srcSocket);
                break;
            case SOCKS5:
                handleSocks5Connection(srcSocket);
                break;
        }
        return this.listenPort;
    }

    private void handleStaticConnection(DatagramSocket srcSocket) throws IOException {
        DatagramSocket dstSocket = new DatagramSocket();
        dstNet.bindSocket(dstSocket);

        // start src listener
        new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    Log.e("UDP OUTBOUND", "listen on port " + this.listenPort);
                    srcSocket.receive(packet);
                    srcAddress = packet.getAddress();
                    srcPort = packet.getPort();
                    DatagramPacket dstPacket = new DatagramPacket(packet.getData(), packet.getLength(), dstAddress.getAddress(), dstAddress.getPort());
                    Log.e("UDP OUTBOUND", "Forward to " + dstAddress.toString());
                    dstSocket.send(dstPacket);
                    this.bytesSent += packet.getLength();
                    MainActivity.handler.sendMessage(MainActivity.handler.obtainMessage(MainActivity.MSG_UPDATE_UI));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        // start dst listener
        new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    Log.e("UDP INBOUND", "listen on port " + dstSocket.getLocalPort());
                    dstSocket.receive(packet);
                    DatagramPacket dstPacket = new DatagramPacket(packet.getData(), packet.getLength(), srcAddress, srcPort);
                    Log.e("UDP INBOUND", "Forward to " + srcAddress.toString() + ":" + srcPort);
                    srcSocket.send(dstPacket);
                    this.bytesReceived += packet.getLength();
                    MainActivity.handler.sendMessage(MainActivity.handler.obtainMessage(MainActivity.MSG_UPDATE_UI));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void handleSocks5Connection(DatagramSocket srcSocket) throws IOException {
        /*
            The UDP ASSOCIATE request is used to establish an association within
            the UDP relay process to handle UDP datagrams.  The DST.ADDR and
            DST.PORT fields contain the address and port that the client expects
            to use to send UDP datagrams on for the association.  The server MAY
            use this information to limit access to the association.  If the
            client is not in possesion of the information at the time of the UDP
            ASSOCIATE, the client MUST use a port number and address of all
            zeros. (see https://datatracker.ietf.org/doc/html/rfc1928)
         */
        new Thread(() -> {
            byte[] buf = new byte[BUFFER_SIZE];
            while (true) {
                try {
                    //Log.d(TAG, "SOCKS UDP: listen on " + srcSocket.getLocalSocketAddress(), null);
                    DatagramPacket request = new DatagramPacket(buf, buf.length);
                    //Log.e(TAG, "!!! SOCKS RECEIVE");
                    srcSocket.receive(request);
                    //Log.e(TAG, ">>>>> !!! SOCKS RECEIVE DONE");

                    // Parse UDP Packet
                    int idx = 0;
                    buf = request.getData();
                    //Log.e(TAG, "SOCKS DATA:  " + bytes2String(buf));
                    // first two bytes are reserved 0x0
                    idx+=2;
                    // 3rd byte fragment number (if not 0x0 --> discard (no fragment support yet))
                    if (buf[idx++] != 0) continue;

                    // 4th byte is address type
                    InetAddress targetAddress = null;
                    byte[] addressBytes;
                    switch (buf[idx++]) {
                        case 1: // IP v4
                            addressBytes = Arrays.copyOfRange(buf, idx, idx+=4);
                            targetAddress = InetAddress.getByAddress(addressBytes);
                            break;
                        case 3: // domain name
                            int len = buf[idx++];
                            addressBytes = Arrays.copyOfRange(buf, idx, idx+=len);
                            String domainName = new String(addressBytes, 0, len);
                            targetAddress = dstNet.getByName(domainName);
                            break;
                        case 4: // IP v6
                            addressBytes = Arrays.copyOfRange(buf, idx, idx+=16);
                            targetAddress = InetAddress.getByAddress(addressBytes);
                            break;
                    }
                    int port = byte2int(buf[idx++]) * 256 + byte2int(buf[idx++]);
                    int payloadLen = request.getLength()-idx;
                    byte[] payload = Arrays.copyOfRange(buf,idx,idx+payloadLen);
                    this.dstAddress = new InetSocketAddress(targetAddress, port);

                    // start response thread
                    Log.d("UDP", "forward udp packet to " + targetAddress.getHostAddress() + ":" + port);
                    DatagramSocket ds = new DatagramSocket();
                    dstNet.bindSocket(ds);
                    new Thread(() -> {
                        byte[] respBuf = new byte[BUFFER_SIZE];
                        while (true) {
                            try {
                                DatagramPacket serverResponse = new DatagramPacket(respBuf, respBuf.length);
                                Log.d("UDP", "... await response", null);
                                ds.receive(serverResponse);
                                this.dstAddress = new InetSocketAddress(serverResponse.getAddress(), serverResponse.getPort());

                                // send back to client
                                byte[] sAddr = serverResponse.getAddress().getAddress();
                                int sPort = serverResponse.getPort();
                                byte[] responseData = new byte[BUFFER_SIZE];
                                responseData[0] = (byte) 0x00;    // Reserved 0x00
                                responseData[1] = (byte) 0x00;    // Reserved 0x00
                                responseData[2] = (byte) 0x00;    // FRAG '00' - Standalone DataGram
                                responseData[3] = (byte) 0x01;    // Address Type -->'01'-IP v4
                                System.arraycopy(sAddr, 0, responseData, 4, sAddr.length);
                                responseData[4 + sAddr.length] = (byte) ((sPort >> 8) & 0xFF);
                                responseData[5 + sAddr.length] = (byte) ((sPort) & 0xFF);
                                System.arraycopy(serverResponse.getData(), 0, responseData, 6 + sAddr.length, serverResponse.getLength());
                                DatagramPacket response = new DatagramPacket(responseData,responseData.length,request.getAddress(), request.getPort());
                                srcSocket.send(response);
                                this.bytesReceived += serverResponse.getLength();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();

                    ds.send(new DatagramPacket(payload, payloadLen, targetAddress, port));
                    this.bytesSent += payloadLen;
                    //Log.d("UDP", "forward udp packet ok", null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private int byte2int(byte b) {
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
}
