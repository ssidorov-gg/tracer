package org.gridgain.util.tcp.tracer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MulticastInitiator extends Thread {
    private static final Logger LOG = LoggerFactory.getLogger(MulticastInitiator.class);

    protected final DateFormat df = new SimpleDateFormat("HH:mm:ss.SSS");

    private InetAddress mcastGrp;
    private int mcastPort;
    private int ttl = -1;
    private InetAddress sockItf;

    private static final String serverUUID = UUID.randomUUID().toString();
    protected static final Set<String> nodes = new HashSet<>();
    private final CountDownLatch nodeCounter;

    private long sequence = 0;
    private static AtomicInteger instance = new AtomicInteger(-1);
    private final int instanceNum;

    public MulticastInitiator(InetAddress mcastGrp, InetAddress sockItf, int mcastPort, int ttl, CountDownLatch nodeCounter) {
        this.mcastGrp = mcastGrp;
        this.sockItf = sockItf;
        this.mcastPort = mcastPort;
        this.ttl = ttl;
        this.nodeCounter = nodeCounter;
        this.instanceNum = instance.incrementAndGet();
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            String message = getMessage();

            byte[] packetData = message.getBytes();

            DatagramPacket reqPckt = new DatagramPacket(packetData, packetData.length, mcastGrp, mcastPort);

            byte[] resData = new byte[200];

            DatagramPacket resPckt = new DatagramPacket(resData, 200);

            MulticastSocket sock = null;

            try {
                sock = new MulticastSocket(0);

                // Use 'false' to enable support for more than one node on the
                // same machine.
                sock.setLoopbackMode(false);

                if (sockItf != null)
                    sock.setInterface(sockItf);

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
                            nodeCounter.countDown();
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

    private String getMessage() {
        return String.format("REQ;%s;#%s;%s;%d", serverUUID, instanceNum, df.format(new Date()), sequence++);
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }
}
