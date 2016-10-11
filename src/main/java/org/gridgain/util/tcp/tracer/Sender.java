package org.gridgain.util.tcp.tracer;

import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class Sender extends MulticastAdapter {
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

        System.out.println("Send multicats packet: " + message);
        socket.send(reqPckt);

        TimeUnit.SECONDS.sleep(timeoutBtwPacketsSec);
    }

    private String getMessage() {
        return String.format("%s|%d", df.format(new Date()), sequence++);
    }
}
