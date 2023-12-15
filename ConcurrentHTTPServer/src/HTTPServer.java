import ConfigParser.ServerConfigObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

public class HTTPServer {

    private final ArrayList<Integer> ports;
    private int numWorkers;
    private int numTotalConnections;
    private final ArrayList<ServerSocketChannel> serverChannels;
    private final ArrayList<Selector> selectors;
    private final Thread[] workers;
    Scanner inputReader;
    WorkersSyncData syncData;
    ServerConfigObject serverConfig;
    public HTTPServer(int idealAveragePerWorker, ServerConfigObject serverConfig) {
        this.numWorkers = serverConfig.getnSelectLoops();
        this.numTotalConnections = numWorkers * idealAveragePerWorker;
        this.ports = serverConfig.getPorts();
        this.workers = new Thread[numWorkers];
        this.inputReader = new Scanner(System.in);
        this.serverConfig = serverConfig;
        this.serverChannels = new ArrayList<ServerSocketChannel>();
        this.selectors = new ArrayList<>();
        syncData = new WorkersSyncData(this.numWorkers,0, numTotalConnections);
    }

    private void openServerChannel() {
        try {
            for (int port : this.ports) {
                System.out.println("Server running on port " + port);
                ServerSocketChannel serverChannel = ServerSocketChannel.open(); // Creates an unbounded socket
                ServerSocket serversocket = serverChannel.socket();
                InetSocketAddress address = new InetSocketAddress(port);
                serversocket.bind(address);
                serverChannel.configureBlocking(false);
                serverChannels.add(serverChannel);
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void startWorkerThreads() {
        for (int i = 0; i < numWorkers; i++) {
            HTTPServerWorkerThread worker = new HTTPServerWorkerThread(syncData, i, serverConfig);
            Selector workerSelector = worker.getSelector();
            this.selectors.add(workerSelector);
            try {
                for (ServerSocketChannel serverChannel : this.serverChannels) {
                    serverChannel.register(workerSelector, SelectionKey.OP_ACCEPT);
                    Thread workerThread = new Thread(worker);
                    workerThread.start();
                    this.workers[i] = workerThread;
                }
            } catch (ClosedChannelException e) {
                e.printStackTrace();
            }
        }
    }

    // Every 0.5 seconds, check all threads to see if any have been stuck for more than 3 seconds, and close the channel if so
    private void startMonitoringTimeout() {
        TimerTask timeoutMonitor = new ChannelTimeOutMonitor(this.selectors, syncData);
        Timer timer = new Timer();
        timer.schedule(timeoutMonitor, 0, 500);
    }

    private void quitServer() {
        syncData.setServerRun(false);
        for (int i = 0; i < numWorkers; i++) {
            try {
                workers[i].join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        System.exit(0);
    }

    private void monitorKeyboardInput() {
        while (true) {
            String input = inputReader.nextLine();
            System.out.println("You entered " + input);
            if (input.equalsIgnoreCase("shutdown") || input.equalsIgnoreCase("stop") || input.equalsIgnoreCase("quit")) {
                System.out.println("Server is shutting down");
                break;
            }
        }
        quitServer();
    }

    public void start() {
        openServerChannel();
        startWorkerThreads();
        startMonitoringTimeout();
        monitorKeyboardInput();
    }
}
