package org.gridgain.util.tcp.tracer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MulticastClient extends Thread {
    private static final Logger LOGGER = LoggerFactory.getLogger(MulticastClient.class);

    protected final DateFormat df = new SimpleDateFormat("HH:mm:ss.SSS");

    private InetAddress mcastGrp;
    private int mcastPort;
    private int ttl = -1;
    private InetAddress sockItf;

    private static final String clientUUID = UUID.randomUUID().toString();

    public MulticastClient(InetAddress mcastGrp, InetAddress sockItf, int mcastPort, int ttl) {
        this.mcastGrp = mcastGrp;
        this.sockItf = sockItf;
        this.mcastPort = mcastPort;
        this.ttl = ttl;
    }

    @Override
    public void run() {
        byte[] reqData = new byte[200];

        DatagramPacket pckt = new DatagramPacket(reqData, reqData.length);

        MulticastSocket sock;

        try {
            sock = createSocket();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        while (!Thread.currentThread().isInterrupted()) {
            try {
                sock.receive(pckt);

                String reqMsg = new String(pckt.getData(), 0, pckt.getLength());

                // filter
                if (!reqMsg.startsWith("REQ")) {
                    continue;
                }

                LOGGER.info(reqMsg);

                try {
                    String resMsg = createResponse(reqMsg);
                    sock.send(new DatagramPacket(resMsg.getBytes(), resMsg.getBytes().length, pckt.getAddress(), pckt.getPort()));
                } catch (IOException e) {
                    System.err.println("Error " + e.getMessage());
                }
            } catch (IOException e) {
                System.err.println("Error " + e.getMessage());
            }
        }

        sock.close();
    }

    private String createResponse(String request) {
        return String.format("RES;%s;%s;%s", clientUUID, df.format(new Date()), request);
    }

    private MulticastSocket createSocket() throws IOException {
        MulticastSocket sock = new MulticastSocket(mcastPort);

        sock.setLoopbackMode(false); // Use 'false' to enable support for more
                                     // than one node on the same machine.

        if (sockItf != null)
            sock.setInterface(sockItf);

        sock.joinGroup(mcastGrp);

        if (ttl != -1)
            sock.setTimeToLive(ttl);

        return sock;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }
}
