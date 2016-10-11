package org.gridgain.util.tcp.tracer;

import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Sender extends MulticastAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(Sender.class);

    private long timeoutBtwPacketsSec = 5;

    private long sequence = 0;

    public Sender(String mcastGrp, int mcastPort, long timeoutBtwPacketsSec) {
        super(mcastGrp, mcastPort);

        this.timeoutBtwPacketsSec = timeoutBtwPacketsSec;
    }

    @Override
    protected void doWork(MulticastSocket socket) throws Exception {
        String message = getMessage();

        byte[] packetData = message.getBytes();

        DatagramPacket reqPckt = new DatagramPacket(packetData, packetData.length, getMcastAddress(), getMcastPort());

        LOG.info("Send multicats packet: {}", message);

        socket.send(reqPckt);

        TimeUnit.SECONDS.sleep(timeoutBtwPacketsSec);
    }

    private String getMessage() {
        return String.format("%s|%d", df.format(new Date()), sequence++);
    }
}
