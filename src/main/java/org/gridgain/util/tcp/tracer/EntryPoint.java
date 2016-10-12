package org.gridgain.util.tcp.tracer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.spi.IgniteSpiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

public class EntryPoint {
    private static final Logger LOG = LoggerFactory.getLogger("result");

    private static final String HELP = "?";

    private static final String NODE_COUNT = "nodes";
    private static final String MULTICAST_GROUP = "group";
    private static final String MULTICAST_PORT = "port";
    private static final String TTL = "ttl";
    private static final String SOCK_ITF = "sockItf";
    private static final String LOCAL_ADDR = "localAddr";
    private static final String TEST_TIME = "testTime";

    public static final String DFLT_MCAST_GROUP = "228.1.2.4";
    public static final int DFLT_MCAST_PORT = 47400;
    public static final int DFLT_TIMEOUT = 5;

    public static void main(String[] args) {
        Opts opts = parseOptions(args);

        String startupParams = opts.toString();

        LOG.info("Startup parameters: {}", startupParams);

        System.out.println("Press Ctrl+C to exit");

        final List<MulticastClient> clients = startClients(opts);

        CountDownLatch nodesCounter = new CountDownLatch(opts.nodes);

        final MulticastInitiator initiator = new MulticastInitiator(opts.ip, opts.sockItf, opts.port, opts.ttl, nodesCounter);
        final Thread initiatorThread = new Thread(initiator);
        initiatorThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("shutting down tracer...");

                initiator.stop();

                stopClients(clients);

                try {
                    initiatorThread.interrupt();
                    initiatorThread.join(10000);
                } catch (InterruptedException e) {
                    System.out.println(e.getMessage());
                }

                System.out.println("finished");
            }
        });

        try {
            boolean success = nodesCounter.await(opts.testTime, TimeUnit.MINUTES);

            if (success) {
                LOG.info("SUCCESS: Packets from all nodes received");
            } else {
                LOG.info("FAIL: Time is over, expected nodes: {}, actual nodes: {}", opts.nodes, nodesCounter.getCount());
            }

            System.exit(0);
        } catch (InterruptedException e) {
            //
        }
    }

    private static List<MulticastClient> startClients(Opts opts) {
        List<MulticastClient> clients = new ArrayList<>(opts.localAddrs.size());

        for (InetAddress locAddr : opts.localAddrs) {
            MulticastClient client = new MulticastClient(opts.ip, locAddr, opts.port, opts.ttl);

            client.start();

            clients.add(client);
        }

        return clients;
    }

    private static void stopClients(List<MulticastClient> clients) {
        for (MulticastClient client : clients) {
            client.interrupt();
        }

        for (MulticastClient client : clients) {
            try {
                client.join(10000);
            } catch (InterruptedException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private static InetAddress toInetAddress(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private static Opts parseOptions(String[] args) {
        final OptionParser parser = new OptionParser();
        final OptionSpec<Void> help = parser.accepts(HELP, "show help").forHelp();
        OptionSpec<Integer> nodes = parser.accepts(NODE_COUNT, "count of expected nodes").withRequiredArg()
                .ofType(Integer.class).required();
        final OptionSpec<String> multicastGroup = parser
                .accepts(MULTICAST_GROUP, "set up multicast group, optional, default value 228.1.2.4").withRequiredArg()
                .ofType(String.class);
        final OptionSpec<Integer> port = parser
                .accepts(MULTICAST_PORT, "set up multicast port, optional, default value 47400").withRequiredArg()
                .ofType(Integer.class);
        OptionSpec<Integer> ttl = parser.accepts(TTL, "ttl").withRequiredArg().ofType(Integer.class);
        OptionSpec<String> sockItf = parser.accepts(SOCK_ITF, "socket interface").withRequiredArg().ofType(String.class);
        OptionSpec<String> localAddr = parser.accepts(LOCAL_ADDR, "local address").withRequiredArg().ofType(String.class);
        OptionSpec<Integer> testTime = parser.accepts(TEST_TIME, "time of the test in minutes").withRequiredArg().ofType(Integer.class);

        OptionSet options;

        try {
            options = parser.parse(args);
        } catch (final Exception e) {
            System.out.println(e.getMessage());
            System.out.println();

            printHelp(parser);
            return null;
        }

        if (options.has(help)) {
            printHelp(parser);
        }

        Opts opts = new Opts();

        opts.nodes = nodes.value(options);

        opts.ip = toInetAddress(options.has(multicastGroup) ? multicastGroup.value(options) : DFLT_MCAST_GROUP);
        opts.port = options.has(port) ? port.value(options) : DFLT_MCAST_PORT;

        if (options.has(ttl))
            opts.ttl = ttl.value(options);

        if (options.has(sockItf))
            opts.sockItf = toInetAddress(sockItf.value(options));

        String lAddr = null;

        if (options.has(localAddr))
            lAddr = localAddr.value(options);

        try {
            opts.localAddrs = toNotLoopbackInetAddrs(U.resolveLocalAddresses(U.resolveLocalHost(lAddr)).get1());
        }
        catch (IOException | IgniteCheckedException e) {
            throw new IgniteSpiException("Failed to resolve local addresses [locAddr=" + lAddr + ']', e);
        }

        if (options.has(testTime))
            opts.testTime = testTime.value(options);

        return opts;
    }

    private static Collection<InetAddress> toNotLoopbackInetAddrs(Collection<String> addresses) {
        List<InetAddress> inetAddrs = new ArrayList<>();

        for (String addr : addresses) {
            InetAddress iAddr = toInetAddress(addr);

            if (!iAddr.isLoopbackAddress()) {
                inetAddrs.add(iAddr);
            }
        }

        return inetAddrs;
    }

    private static void printHelp(final OptionParser parser) {
        try {
            System.out.println("Usage:");
            System.out.println("java -jar tracer.jar --nodes=4 [--group=228.1.2.4] [--port=47400] [--timeout=5]");
            System.out.println("Supported options:");
            parser.printHelpOn(System.out);

            System.exit(0);
        } catch (IOException e) {
            //
        }
    }

    private static class Opts {
        private int nodes;
        private InetAddress ip;
        private int port;
        private int ttl = -1;
        private InetAddress sockItf;
        private Collection<InetAddress> localAddrs;
        private int testTime = 10;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("expected nodes: ").append(nodes).append("; ");
            sb.append("multicast group: ").append(ip).append("; ");
            sb.append("multicast port: ").append(port);

            if (localAddrs != null) {
                sb.append("; ").append("local addrs: ");

                boolean first = true;

                for (InetAddress addr : localAddrs) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append(", ");
                    }

                    sb.append(addr);
                }
            }

            if (ttl != -1)
                sb.append("; ").append("ttl: ").append(ttl);

            if (sockItf != null)
                sb.append("; ").append("sockItf: ").append(sockItf);


            return sb.toString();
        }
    }
}
