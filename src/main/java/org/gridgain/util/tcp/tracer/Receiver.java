package org.gridgain.util.tcp.tracer;

import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Receiver extends MulticastAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(Receiver.class);

    private CountDownLatch nodesCounter;

    private Set<String> nodes = new HashSet<>();

    public Receiver(String mcastGrp, int mcastPort, CountDownLatch nodesCounter) {
        super(mcastGrp, mcastPort);
        this.nodesCounter = nodesCounter;
    }

    @Override
    protected void doWork(MulticastSocket socket) throws Exception {
        byte[] buf = new byte[20];

        DatagramPacket msgPacket = new DatagramPacket(buf, buf.length);

        try {
            socket.receive(msgPacket);
        } catch (SocketTimeoutException e) {
            // nothing to receive
            return;
        }

        String msg = new String(buf, 0, msgPacket.getLength());

        // filter
        if (!msg.contains("|")) {
            return;
        }

        String address = msgPacket.getAddress().getHostAddress();

        if (nodes.add(address)) {
            nodesCounter.countDown();
        }

        LOG.info("Receive multicast packet at {} from ip {}, message: {}", df.format(new Date()), msgPacket.getAddress(), msg);
    }
}
