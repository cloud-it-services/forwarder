package com.modima.forwarder.socks;

import android.util.Log;

import com.modima.forwarder.MainActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;

public class SocksProxy extends Thread {

    static final String TAG = SocksProxy.class.getName();

    private final Socket socket;
    private final int BUFF_SIZE = 8192;

    public SocksProxy(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        Socket targetSocket = null;
        try {
            Log.e(TAG, "start socks server");
            InputStream clientInStream = socket.getInputStream();
            OutputStream clientOutStream = socket.getOutputStream();
            byte[] buff = new byte[BUFF_SIZE];
            ByteArrayOutputStream byteArrayOutputStream;

            // 1st byte version 0x05
            clientInStream.read(buff, 0, 1); // must be 0x05
            Log.e(TAG, "v:" + buff[0]);

            // 2nd byte count of supported auth methods
            clientInStream.read(buff, 0, 1); // must be 0x05
            Log.e(TAG, "n:" + buff[0]);

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
            Log.e(TAG, "!!!! CMD: " + buff[0]);

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
                    Log.d(TAG, "TCP Connection to " + domainName + " " + targetIP + ":" + port);
                    targetSocket = MainActivity.cellNet.getSocketFactory().createSocket(targetIP, port);
                    localIPBytes = targetSocket.getLocalAddress().getAddress();
                    Log.e(TAG,"Local Socket IP: " + targetSocket.getLocalAddress().getHostAddress());
                    int tcpPort = targetSocket.getLocalPort();
                    sendResponse(localIPBytes, tcpPort, (byte) 0);
                    createTCPConnection(targetSocket);
                    break;
                case 2:
                    // establish TCP Server (command not yet supported)
                    Log.e(TAG, "TCP Server requested (not yet supported");
                    sendResponse(new byte[]{0, 0, 0, 0}, 0, (byte) 7);
                    break;
                case 3:
                    // create UDP Forwarding
                    Log.d(TAG, "UDP Connection to " + domainName + " " + targetIP + ":" + port);
                    DatagramSocket s = new DatagramSocket();
                    MainActivity.wifiNet.bindSocket(s);
                    //localIPBytes = s.getLocalAddress().getAddress();
                    int udpPort = s.getLocalPort();
                    Log.e(TAG, "UDP host address: " + s.getLocalAddress().getHostAddress() + ":" + udpPort);
                    sendResponse(MainActivity.wifiAddress.getAddress(), udpPort, (byte) 0);
                    createUDPConnection(s);
                    Log.e(TAG, "!!!!!!!!!!!!! UDP forwarding established !!!!!!!!!!!!!");
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(new byte[]{0, 0, 0, 0}, 0, (byte) 1);
        } finally {
            /*
            try {
                Log.e(TAG, "!!! Close socket to " + targetSocket.getInetAddress().getHostAddress());
                if (socket != null) socket.close();
                if (targetSocket != null) targetSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            */
        }

    }

    protected void sendResponse(byte[] localIP, int localPort, byte respCode) {

        //Log.d(TAG, "Server socket " + InetAddress.getByAddress(localIP).getHostAddress() + ":" + localPort);

        int len = localIP.length + 6;
        byte[] response = new byte[len];
        response[0] = 5;        // version socks5
        response[1] = respCode; // response code
        response[2] = 0;        // reserved

        Log.e("TAG","IP len: " + localIP.length);

        if (localIP.length == 4) {
            response[3] = 1;        // address type IPv4
            for (int i = 0; i < localIP.length; i++) {
                response[4+i] = localIP[i];
            }
        } else if (localIP.length == 16) {
            response[3] = 4;        // address type IPv6
            for (int i = 0; i < localIP.length; i++) {
                response[4+i] = localIP[i];
            }
        } else {
            Log.e("TAG","Long IP (maybe domain name)");
        }
        response[len-2] = (byte) (localPort >> 8);   // high byte port
        response[len-1] = (byte) (localPort & 0xff); // low byte port

        Log.d(TAG, "socks response: " + bytes2String(response));
        try {
            OutputStream os = this.socket.getOutputStream();
            os.write(response, 0, len);
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    protected void createUDPConnection(DatagramSocket source) {

        new Thread(() -> {
            byte[] buf = new byte[4096];
            while (true) {
                try {
                    Log.d(TAG, "SOCKS UDP: listen on " + source.getLocalSocketAddress(), null);
                    source.setSoTimeout(120000); // close after two minutes no packet received
                    DatagramPacket request = new DatagramPacket(buf, buf.length);
                    source.receive(request);
                    Log.d("UDP", bytes2String(request.getData()));

                    // Parse UDP Packet
                    int idx = 0;
                    buf = request.getData();
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
                            targetAddress = MainActivity.cellNet.getByName(domainName);
                            break;
                        case 4: // IP v6
                            addressBytes = Arrays.copyOfRange(buf, idx, idx+=16);
                            targetAddress = InetAddress.getByAddress(addressBytes);
                            break;
                    }
                    int port = byte2int(buf[idx++]) * 256 + byte2int(buf[idx++]);
                    int payloadLen = request.getLength()-idx;
                    byte[] payload = Arrays.copyOfRange(buf,idx,idx+payloadLen);

                    Log.d("UDP", "forward udp packet to " + targetAddress.getHostAddress() + ":" + port + " | payload: " + bytes2String(payload));
                    DatagramSocket ds = new DatagramSocket();
                    MainActivity.cellNet.bindSocket(ds);

                    // start response thread
                    new Thread(() -> {
                        byte[] respBuf = new byte[4096];
                        while (true) {
                            try {
                                DatagramPacket serverResponse = new DatagramPacket(respBuf, respBuf.length);
                                Log.d("UDP", "... await response", null);
                                ds.receive(serverResponse);


                                // send back to client
                                byte[] sAddr = serverResponse.getAddress().getAddress();
                                int sPort = serverResponse.getPort();
                                byte[] responseData = new byte[4096];
                                responseData[0] = (byte) 0x00;    // Reserved 0x00
                                responseData[1] = (byte) 0x00;    // Reserved 0x00
                                responseData[2] = (byte) 0x00;    // FRAG '00' - Standalone DataGram
                                responseData[3] = (byte) 0x01;    // Address Type -->'01'-IP v4
                                System.arraycopy(sAddr, 0, responseData, 4, sAddr.length);
                                responseData[4 + sAddr.length] = (byte) ((sPort >> 8) & 0xFF);
                                responseData[5 + sAddr.length] = (byte) ((sPort) & 0xFF);
                                System.arraycopy(serverResponse.getData(), 0, responseData, 6 + sAddr.length, serverResponse.getLength());

                                DatagramPacket response = new DatagramPacket(responseData,responseData.length,request.getAddress(), request.getPort());
                                source.send(response);
                                Log.d("UDP","!!!!!!!!!!!! UDP RESPONSE" + response.getAddress().getHostAddress() + ":" + response.getPort() + " | payload: " + bytes2String(responseData));
                            } catch (SocketTimeoutException e) {
                                if (source != null) {
                                    //Log.e(TAG, "!!! Close udp socket " + source.getInetAddress().getHostAddress());
                                    source.close();
                                }
                                break;
                            }
                            catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();

                    ds.send(new DatagramPacket(payload, payloadLen, targetAddress, port));
                    Log.d("UDP", "forward udp packet ok", null);

                    //MainActivity.cellSocketUDP.send(new DatagramPacket(data, data.length, targetAddress, port));
                } catch (SocketTimeoutException e) {
                    if (source != null) {
                        //Log.e(TAG, "!!! Close udp socket " + source.getInetAddress().getHostAddress());
                        source.close();
                    }
                    break;
                } catch (IOException e) {
                    Log.e(TAG, "!!! Closed UDP socket to " + source.getInetAddress().getHostAddress());
                    e.printStackTrace();
                } catch (Exception e) {
                    Log.e(TAG,e.getMessage(),e);
                }
            }
        }).start();
    }

    protected void createTCPConnection(Socket targetSocket) throws IOException {
        InputStream serverInStream = targetSocket.getInputStream();
        OutputStream serverOutStream = targetSocket.getOutputStream();

        InputStream clientInStream = this.socket.getInputStream();
        OutputStream clientOutStream = this.socket.getOutputStream();

        // start server response handler
        new Thread(() -> {
            try {
                byte[] response = new byte[BUFF_SIZE];
                int bytesRead;
                while (-1 != (bytesRead = serverInStream.read(response, 0, BUFF_SIZE))) {
                    clientOutStream.write(response, 0, bytesRead);
                    clientOutStream.flush();
                }
            } catch (IOException e) {
                Log.e(TAG, "!!! Closed socket to " + targetSocket.getInetAddress().getHostAddress());
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // CLosing the socket leads to that the request may not be sended to the target destination
                try {
                    Log.e(TAG, "!!! Close socket to " + targetSocket.getInetAddress().getHostAddress());
                    if (socket != null) socket.close();
                    if (targetSocket != null) targetSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        // send request to target server
        byte[] request = new byte[BUFF_SIZE];
        int bytesRead;
        try {
            while (-1 != (bytesRead = clientInStream.read(request))) {
                serverOutStream.write(request, 0, bytesRead);
                serverOutStream.flush();
                //Log.d(TAG, bytesRead + " bytes sent");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected int byte2int(byte b) {
        return b & 0xff;
    }
}