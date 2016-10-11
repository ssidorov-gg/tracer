package org.gridgain.util.tcp.tracer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MulticastInitiator {
    private static final Logger LOG = LoggerFactory.getLogger(MulticastInitiator.class);

    protected final DateFormat df = new SimpleDateFormat("HH:mm:ss.SSS");

    private String mcastGrp;
    private int mcastPort;
    private int ttl = -1;
    private String sockItf;

    private final String serverUUID = UUID.randomUUID().toString();
    private final Set<String> nodes = new HashSet<>();
    private final int nodeCount;

    private long sequence = 0;
    private volatile int currentNodeCount = 0;

    private volatile boolean started;

    public MulticastInitiator(String mcastGrp, int mcastPort, int nodeCount) {
        this.mcastGrp = mcastGrp;
        this.mcastPort = mcastPort;
        this.nodeCount = nodeCount;
    }

    public void work() {
        started = true;

        while (started) {
            String message = getMessage();

            byte[] packetData = message.getBytes();

            DatagramPacket reqPckt = new DatagramPacket(packetData, packetData.length, toInetAddress(mcastGrp), mcastPort);

            byte[] resData = new byte[200];

            DatagramPacket resPckt = new DatagramPacket(resData, 200);

            MulticastSocket sock = null;

            try {
                sock = new MulticastSocket(0);

                // Use 'false' to enable support for more than one node on the
                // same machine.
                sock.setLoopbackMode(false);

                if (sockItf != null)
                    sock.setInterface(toInetAddress(sockItf));

                sock.setSoTimeout((int) TimeUnit.SECONDS.toMillis(10));

                if (ttl != -1)
                    sock.setTimeToLive(ttl);

                reqPckt.setData(packetData);

                try {
                    LOG.info("request: {}", message);
                    sock.send(reqPckt);
                } catch (IOException e) {
                    System.err.println("Error " + e.getMessage());

                    return;
                }

                long rcvEnd = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);

                try {
                    while (System.currentTimeMillis() < rcvEnd) { // Try to
                                                                  // receive
                        // multiple
                        // responses.
                        sock.receive(resPckt);

                        byte[] data = resPckt.getData();

                        String msg = new String(data, 0, resPckt.getLength());

                        if (!msg.startsWith("RES")) {
                            // just skip, wrong packet

                            continue;
                        }

                        LOG.info("response from {}: {}", resPckt.getAddress(), msg);

                        String clientUUID = msg.split(";")[1];

                        if (nodes.add(clientUUID)) {
                            currentNodeCount++;
                            if (nodes.size() == nodeCount) {
                                System.out.println("SUCCESS: Packets from all nodes received");
                            }
                        }
                    }
                } catch (SocketTimeoutException ignored) {
                    // ignore
                }
            } catch (IOException e) {
                System.err.println("Error " + e.getMessage());
            } finally {
                sock.close();
            }
        }
    }

    public void stop() {
        started = false;
    }

    private String getMessage() {
        return String.format("REQ;%s;%s;%d", serverUUID, df.format(new Date()), sequence++);
    }

    private InetAddress toInetAddress(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    public void setSockItf(String sockItf) {
        this.sockItf = sockItf;
    }

    public int getCurrentNodes() {
        return currentNodeCount;
    }
}
