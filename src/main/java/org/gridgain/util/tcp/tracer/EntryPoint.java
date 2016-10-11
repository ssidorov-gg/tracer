package org.gridgain.util.tcp.tracer;

import java.io.IOException;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

public class EntryPoint {
    private static final String HELP = "?";
    private static final String CLIENT = "client";
    private static final String SERVER = "server";

    private static final String MULTICAST_GROUP = "group";
    private static final String MULTICAST_PORT = "port";
    private static final String TIMEOUT = "timeout";

    public static void main(String[] args) {
        Opts opts = parseOptions(args);

        MulticastAdapter worker;

        if (opts.client) {
            worker = new Receiver(opts.ip, opts.port);
        } else {
            worker = new Sender(opts.ip, opts.port, opts.timeout);
        }

        Thread workerThread = new Thread(worker::start);

        System.out.println("Tool starts with next parameters:");
        System.out.println(opts.toString());
        System.out.println("press Ctrl + C to stop");

        workerThread.start();

        worker.stop();

        try {
            workerThread.join();
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }
    }

    private static Opts parseOptions(String[] args) {
        final OptionParser parser = new OptionParser();
        final OptionSpec<Void> help = parser.accepts(HELP, "show help").forHelp();
        final OptionSpec<Void> serverMode = parser.accepts(SERVER, "run in server mode, default");
        final OptionSpec<Void> clientMode = parser.accepts(CLIENT, "run in client mode").availableUnless(SERVER);
        final OptionSpec<String> multicastGroup = parser
                .accepts(MULTICAST_GROUP, "set up multicast group, optional, default value 228.1.2.4").withRequiredArg()
                .ofType(String.class);
        final OptionSpec<Integer> port = parser
                .accepts(MULTICAST_PORT, "set up multicast port, optional, default value 47400").withRequiredArg()
                .ofType(Integer.class);
        OptionSpec<Integer> timeout = parser.accepts(TIMEOUT, "timeout (in seconds) between packets (server mode)")
                .availableUnless(CLIENT).withRequiredArg().ofType(Integer.class);

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

        if (options.has(clientMode)) {
            opts.client = true;
        } else if (options.has(serverMode)) {
            opts.client = false;
        } else {
            // server by default
            opts.client = false;
        }

        if (!opts.client) {
            opts.timeout = options.has(timeout) ? timeout.value(options) : MulticastAdapter.DFLT_TIMEOUT;
        }

        opts.ip = options.has(multicastGroup) ? multicastGroup.value(options) : MulticastAdapter.DFLT_MCAST_GROUP;
        opts.port = options.has(port) ? port.value(options) : MulticastAdapter.DFLT_MCAST_PORT;

        return opts;
    }

    private static void printHelp(final OptionParser parser) {
        try {
            System.out.println("Usage:");
            System.out.println("java -jar tracer.jar [--client|--server] [--group=228.1.2.4] [--port=47400] [--timeout=5]");
            System.out.println("Supported options:");
            parser.printHelpOn(System.out);

            System.exit(0);
        } catch (IOException e) {
            //
        }
    }

    private static class Opts {
        private boolean client;
        private String ip;
        private int port;
        private int timeout;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("mode: ").append((client ? "client" : "server")).append(", ");
            sb.append("multicast group: ").append(ip).append(", ");
            sb.append("multicast port: ").append(port).append(", ");

            if (!client)
                sb.append("timeout: ").append(timeout);

            return sb.toString();
        }
    }
}
