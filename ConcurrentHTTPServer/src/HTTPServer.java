import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.*;
import java.util.Scanner;

import static java.lang.Thread.sleep;

public class HTTPServer {

    private int port;
    private static int numWorkers;
    private static int idealAveragePerWorker;
    private static int numTotalConnections;
    private ServerSocketChannel serverChannel;
    private Thread workers[];
    Scanner inputReader;
    WorkersSyncData syncData;
    public HTTPServer(int numWorkers, int idealAveragePerWorker) {
        HTTPServer.numWorkers = numWorkers;
        HTTPServer.idealAveragePerWorker = idealAveragePerWorker;
        HTTPServer.numTotalConnections = numWorkers * idealAveragePerWorker;
        this.port = 8080;
        this.workers = new Thread[numWorkers];
        this.inputReader = new Scanner(System.in);
        syncData = new WorkersSyncData(numWorkers, numTotalConnections);
    }

    private void openServerChannel(int port) {
        try {
            serverChannel = ServerSocketChannel.open(); // Creates an unbounded socket
            ServerSocket serversocket = serverChannel.socket();
            InetSocketAddress address = new InetSocketAddress(port);
            serversocket.bind(address);
            serverChannel.configureBlocking(false);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }




    private void startWorkerThreads() {
        for (int i = 0; i < numWorkers; i++) {
            HTTPServerWorkerThread worker = new HTTPServerWorkerThread(syncData, i);
            Selector workerSelector = worker.getSelector();
            try {
                serverChannel.register(workerSelector, SelectionKey.OP_ACCEPT);
                Thread workerThread = new Thread(worker);
                workerThread.start();
                this.workers[i] = workerThread;
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
            String myinput = inputReader.nextLine();
            System.out.println("You entered " + myinput);
            if (myinput.equals("quit") || myinput.equals("stop")) {
                quitServer = true;
            }
        }
        quitServer();
    }

    public void start() {
        openServerChannel(this.port);
        startWorkerThreads();
        monitorKeyboardInput();

    }
}
