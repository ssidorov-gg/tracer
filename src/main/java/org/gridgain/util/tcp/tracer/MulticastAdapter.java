package org.gridgain.util.tcp.tracer;

import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;

public abstract class MulticastAdapter {
    /** Default multicast IP address (value is {@code 228.1.2.4}). */
    public static final String DFLT_MCAST_GROUP = "228.1.2.4";

    /** Default multicast port number (value is {@code 47400}). */
    public static final int DFLT_MCAST_PORT = 47400;

    public static final int DFLT_TIMEOUT = 5;

    protected final DateFormat df = new SimpleDateFormat("HH:mm:ss.SSS");

    private String mcastGrp;

    private int mcastPort;

    private volatile boolean started;

    public MulticastAdapter(String mcastGrp, int mcastPort) {
        this.mcastGrp = mcastGrp;
        this.mcastPort = mcastPort;
    }

    public void start() {
        started = true;

        try (MulticastSocket socket = new MulticastSocket(mcastPort)) {
            // Use 'false' to enable support for more than one node on the same
            // machine.
            socket.setLoopbackMode(false);
            socket.joinGroup(getMcastAddress());
            socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(10));

            while (started) {
                doWork(socket);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e);
        }
    }

    public void stop() {
        started = false;
    }

    protected abstract void doWork(MulticastSocket socket) throws Exception;

    protected String getMcastGrp() {
        return mcastGrp;
    }

    protected InetAddress getMcastAddress() throws UnknownHostException {
        return InetAddress.getByName(getMcastGrp());
    }

    protected int getMcastPort() {
        return mcastPort;
    }
}
