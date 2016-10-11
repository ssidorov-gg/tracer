package org.gridgain.util.tcp.tracer;

import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.Date;

public class Receiver extends MulticastAdapter {

    public Receiver(String mcastGrp, int mcastPort) {
        super(mcastGrp, mcastPort);
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

        String msg = new String(buf, 0, buf.length);

        System.out.println(String.format("Receive multicast packet at %s: %s from ip: %s", df.format(new Date()), msg, msgPacket.getAddress()));
    }
}
