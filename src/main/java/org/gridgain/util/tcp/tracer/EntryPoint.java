package org.gridgain.util.tcp.tracer;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

public class EntryPoint {
    private static final String HELP = "?";

    private static final String NODE_COUNT = "nodes";
    private static final String MULTICAST_GROUP = "group";
    private static final String MULTICAST_PORT = "port";
    private static final String TTL = "ttl";
    private static final String SOCK_ITF = "sockItf";

    public static void main(String[] args) {
        Opts opts = parseOptions(args);

        CountDownLatch nodesCounter = new CountDownLatch(opts.nodes);

        MulticastInitiator initiator = new MulticastInitiator(opts.ip, opts.port, nodesCounter);
        Thread initiatorThread = new Thread(initiator::work);

        MulticastClient client = new MulticastClient(opts.ip, opts.port);
        Thread clientThread = new Thread(client::work);

        System.out.println("Tool starts with next parameters:");
        System.out.println(opts.toString());

        initiatorThread.start();
        clientThread.start();

        while (true) {
            boolean passed = false;

            try {
                passed = nodesCounter.await(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                //
            }

            if (passed) {
                System.out.println("SUCCESS: Packets from all nodes received");
                System.out.println("Waiting for other nodes (10 minutes)...");
                try {
                    TimeUnit.MINUTES.sleep(10);
                } catch (InterruptedException e) {
                    //
                }
                System.exit(0);
                break;
            } else {
                System.out.println(String.format("FAIL: Time is over, expected nodes: %d, actual nodes: %d",
                        opts.nodes, nodesCounter.getCount()));
                System.out.println("Type 'y' to wait another 10 minutes or 'n' to exit");

                if (userChoice()) {
                    continue;
                } else {
                    System.exit(0);
                }
            }
        }

        initiator.stop();

        try {
            clientThread.interrupt();
            clientThread.join();
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }

        try {
            initiatorThread.interrupt();
            initiatorThread.join();
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }

        System.out.println("finished");
    }

    @SuppressWarnings("resource")
    private static boolean userChoice() {
        Scanner scan = new Scanner(System.in);

        String answer = scan.nextLine();

        if (answer.equals("y")) {
            return true;
        } else {
            return false;
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

        opts.ip = options.has(multicastGroup) ? multicastGroup.value(options) : MulticastAdapter.DFLT_MCAST_GROUP;
        opts.port = options.has(port) ? port.value(options) : MulticastAdapter.DFLT_MCAST_PORT;

        if (options.has(ttl))
            opts.ttl = ttl.value(options);

        if (options.has(sockItf))
            opts.sockItf = sockItf.value(options);

        return opts;
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
        private String ip;
        private int port;
        private int ttl = -1;
        private String sockItf;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("expected nodes: ").append(nodes).append(", ");
            sb.append("multicast group: ").append(ip).append(", ");
            sb.append("multicast port: ").append(port);

            if (ttl != -1)
                sb.append(", ").append("ttl: ").append(ttl);

            if (sockItf != null)
                sb.append(", ").append("sockItf: ").append(sockItf);

            return sb.toString();
        }
    }
}
