import ConfigParser.ServerConfigObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class HTTPServerWorkerThread implements Runnable {

    private Selector selector;

    final WorkersSyncData syncData;
    private int numActiveConnections = 0;

    ServerConfigObject serverConfig;

    public HTTPServerWorkerThread(WorkersSyncData syncdata, int workerID, ServerConfigObject serverConfig) {
        this.serverConfig = serverConfig;
        this.syncData = syncdata;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Selector getSelector() {
        return selector;
    }

    private void generateResponse(ConnectionControlBlock ccb) {
        StringBuffer request = ccb.getRequest();
        ByteBuffer writeBuffer = ccb.getWriteBuffer();

        RequestHandler requestHandler = ccb.getRequestHandler();
        requestHandler.parseRequest();
        String response = requestHandler.handleRequest();



        // Generate Response
        for (int i = 0; i < response.length(); i++) {
            char ch = response.charAt(i);
            writeBuffer.put((byte) ch);
        }


        // Update state
        writeBuffer.flip();
        ccb.setConnectionState(ConnectionState.WRITE);
    }

    private void closeSocket(SocketChannel socketChannel) {
        numActiveConnections--;
        try {
            socketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateCCBOnRead(int readBytes, ConnectionControlBlock ccb) {
        ByteBuffer readBuffer = ccb.getReadBuffer();
        StringBuffer request = ccb.getRequest();
        if (readBytes == -1) {
            ccb.setConnectionState(ConnectionState.READ);
        } else {
            readBuffer.flip();
            RequestHandler requestHandler = ccb.getRequestHandler();
            while (ccb.getConnectionState() != ConnectionState.READ
                    && readBuffer.hasRemaining()
                    && request.length() < request.capacity()) {
                char ch = (char) readBuffer.get();
                requestHandler.readCharsToRequest(ch);

                if (requestHandler.requestCompleted()) {
                    ccb.setConnectionState(ConnectionState.READ);
                }
            }
            ccb.setLastReadTime(System.currentTimeMillis());
        }
        readBuffer.clear();
        ccb.updateConnectionState();
    }

    private void updateCCBOnWrite(int writeBytes, ConnectionControlBlock ccb) {
        ByteBuffer writeBuffer = ccb.getWriteBuffer();
        if (writeBytes == -1) {
            ccb.setConnectionState(ConnectionState.WRITTEN);
        } else {
            if (writeBuffer.remaining() == 0) {
                ccb.setConnectionState(ConnectionState.WRITTEN);
            }
        }
        ccb.updateConnectionState();
    }

    private boolean overloaded() {
        synchronized (syncData) {
            double averageConnectionPerWorker = (double) syncData.getNumConnections() / (double) syncData.getNumWorkers();
            return (numActiveConnections > averageConnectionPerWorker);
        }
    }

    private boolean serverIsRunning() {
        synchronized (syncData) {
            return syncData.getServerIsRunning();
        }
    }
    public void run() {
//        System.out.println("Worker #" + workerID + " listening");
        while (serverIsRunning()) {
            try {
                selector.select(); // Blocking operation, returns only after a channel is selected
            } catch (IOException e) {
                e.printStackTrace();
            }
            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = readyKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                try {

                    // ==================== Accept =================
                    if (key.isAcceptable()) {
                        // Basic load balancing
                        if (overloaded()) {
                            continue;
                        }
                        // Get channel
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel client = server.accept();
                        if (client == null) {
                            continue;
                        }

                        numActiveConnections++;
//                        System.out.println("Worker " + workerID + " accepted connection from " + client.getRemoteAddress());
                        client.configureBlocking(false);

                        // Register selector
                        SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

                        // Create CCB
                        ConnectionControlBlock ccb = new ConnectionControlBlock();
                        ccb.setConnectionState(ConnectionState.READING);
                        ccb.setLastReadTime(System.currentTimeMillis());

                        // Set HTTP Request Handler
                        HTTPRequestHandler httpRequestHandler = new HTTPRequestHandler(this.serverConfig, client);
                        ccb.setRequestHandler(httpRequestHandler);


                        // Attach to key
                        clientKey.attach(ccb);
                    }

                    // ==================== Read =================
                    if (key.isReadable()) {
                        // Should not be reading if we're not in reading state
                        ConnectionControlBlock ccb = (ConnectionControlBlock) key.attachment();
                        if (ccb.getConnectionState() != ConnectionState.READING) {
                            continue;
                        }

                        System.out.println(4);

                        // Get channel
                        SocketChannel client = (SocketChannel) key.channel();

                        int readBytes = client.read(ccb.getReadBuffer());
                        updateCCBOnRead(readBytes, ccb);

                        // If done reading, generate response
                        if (ccb.getConnectionState() == ConnectionState.READ) {
                            // Generate Response
                            generateResponse(ccb);
                        }
                    }

                    // ==================== Write =================
                    if (key.isWritable()) {
                        // Should be in write state
                        ConnectionControlBlock ccb = (ConnectionControlBlock) key.attachment();
                        if (ccb.getConnectionState() != ConnectionState.WRITE) {
                            continue;
                        }

                        System.out.println(5);

                        SocketChannel client = (SocketChannel) key.channel();
                        int writeBytes = client.write(ccb.getWriteBuffer());
                        updateCCBOnWrite(writeBytes, ccb);
                        // When finish writing, close socket
                        if (ccb.getConnectionState() == ConnectionState.WRITTEN) {
                            // Unless keep connection alive
                            System.out.println(1);
                            if (ccb.isKeepConnectionAlive()) {
                                System.out.println(2);
                                ccb.resetState();
                                ccb.setLastReadTime(System.currentTimeMillis());
                                ccb.setConnectionState(ConnectionState.READING);

                            } else {
                                closeSocket(client);
                            }

                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        for (SelectionKey key : selector.keys()) {
            try {
                key.channel().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
