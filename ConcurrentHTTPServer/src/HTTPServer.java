import ConfigParser.ServerConfigObject;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;

public class HTTPServer {

    private ArrayList<Integer> ports;
    private static int numWorkers;
    private static int idealAveragePerWorker;
    private static int numTotalConnections;
    private ArrayList<ServerSocketChannel> serverChannels;
    private ArrayList<Selector> selectors;
    private Thread workers[];
    Scanner inputReader;
    WorkersSyncData syncData;
    ServerConfigObject serverConfig;
    public HTTPServer(int numWorkers, int idealAveragePerWorker, ServerConfigObject serverConfig) {
        HTTPServer.numWorkers = serverConfig.getnSelectLoop();
        HTTPServer.idealAveragePerWorker = 8;
        HTTPServer.numTotalConnections = numWorkers * idealAveragePerWorker;
        this.ports = serverConfig.getPorts();
        this.workers = new Thread[numWorkers];
        this.inputReader = new Scanner(System.in);
        this.serverConfig = serverConfig;
        this.serverChannels = new ArrayList<ServerSocketChannel>();
        this.selectors = new ArrayList<>();
        syncData = new WorkersSyncData(numWorkers, numTotalConnections);
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

    private void quitServer() {
        syncData.setServerRun(false);
        for (int i = 0; i < numWorkers; i++) {
            try {
                workers[i].join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void monitorKeyboardInput() {
        boolean quitServer = false;
        while (!quitServer) {
            String userInput = inputReader.nextLine();
            if (userInput.equalsIgnoreCase("quit") || userInput.equalsIgnoreCase("stop")) {
                quitServer = true;
            }
        }
        quitServer();
    }

    private void startMonitoringTimeout() {
        TimerTask timeoutMonitor = new ChannelTimeOutMonitor(this.selectors);
        Timer timer = new Timer();
        timer.schedule(timeoutMonitor, 0, 500);
    }

    public void start() {
        openServerChannel();
        startWorkerThreads();
        startMonitoringTimeout();
        monitorKeyboardInput();

    }
}
