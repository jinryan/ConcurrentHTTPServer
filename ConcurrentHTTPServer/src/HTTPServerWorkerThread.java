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
    private int workerID;

    final WorkersSyncData syncData;
    private int numActiveConnections = 0;

    ServerConfigObject serverConfig;

    public HTTPServerWorkerThread(WorkersSyncData syncdata, int workerID, ServerConfigObject serverConfig) {
        this.serverConfig = serverConfig;
        this.workerID = workerID;
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

        String CRLF = "\r\n"; // 13, 10 in ASCII
        String result = request.toString();
        String response =
            "HTTP/1.1 200 OK" + CRLF +
            "Content-Type: text/html; charset=UTF-8" + CRLF +
            "Content-Length: " + result.getBytes().length + CRLF + CRLF +
            result + CRLF ;
            
        System.out.println(response);


        // Generate Response
        for (int i = 0; i < response.length(); i++) {
            char ch = response.charAt(i);

            ch = Character.toUpperCase(ch);

            writeBuffer.put((byte) ch);
        }

        // Update state
        writeBuffer.flip();
        ccb.setConnectionState(ConnectionState.WRITE);

        // Keep Connection Alive
        // ccb.setKeepConnectionAlive(true);
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
            while (ccb.getConnectionState() != ConnectionState.READ
                    && readBuffer.hasRemaining()
                    && request.length() < request.capacity()) {
                char ch = (char) readBuffer.get();
                request.append(ch);
                if (ch == '\r' || ch == '\n') {
                    ccb.setConnectionState(ConnectionState.READ);
                }
            }
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
            System.out.println(workerID + " received accept. Active: " + numActiveConnections + " Total: " + syncData.getNumConnections() + " # Workers: " + syncData.getNumWorkers() + " Accept: " + (numActiveConnections > averageConnectionPerWorker));

            return (numActiveConnections > averageConnectionPerWorker);
        }
    }
    public void run() {
        System.out.println("Worker #" + workerID + " listening");
        while (syncData.getServerIsRunning()) {
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
                        System.out.println("Worker " + workerID + " accepted connection from " + client.getRemoteAddress());
                        client.configureBlocking(false);

                        // Register selector
                        SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

                        // Create CCB
                        ConnectionControlBlock ccb = new ConnectionControlBlock();
                        ccb.setConnectionState(ConnectionState.READING);

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
                        SocketChannel client = (SocketChannel) key.channel();
                        int writeBytes = client.write(ccb.getWriteBuffer());
                        updateCCBOnWrite(writeBytes, ccb);

                        // When finish writing, close socket
                        if (ccb.getConnectionState() == ConnectionState.WRITTEN) {
                            // Unless keep connection alive
                            if (ccb.isKeepConnectionAlive()) {
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
    }

}
