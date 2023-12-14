import ConfigParser.ServerConfigObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

public class HTTPServerWorkerThread implements Runnable {

    private Selector selector;
    final WorkersSyncData syncData;
    private int numActiveConnections = 0;
    ServerConfigObject serverConfig;




    public HTTPServerWorkerThread(WorkersSyncData syncData, int workerID, ServerConfigObject serverConfig) {
        this.serverConfig = serverConfig;
        this.syncData = syncData;
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
        ByteBuffer writeBuffer = ccb.getWriteBuffer();
        RequestHandler requestHandler = ccb.getRequestHandler();
        requestHandler.parseRequest();


        /*
        String response = requestHandler.handleRequest(syncData);



        // Generate Response
        System.out.print("Response: =====");
        for (int i = 0; i < response.length(); i++) {
            char ch = response.charAt(i);
            writeBuffer.put((byte) ch);
            System.out.print((byte) ch);
        }
        System.out.print("====Response ENDS");


        // Update state
        writeBuffer.flip();
        ccb.setConnectionState(ConnectionState.WRITE);

        return;
        */

        ccb.setConnectionState(ConnectionState.WRITE);
//        System.out.println(ccb.getConnectionState());



        HTTPResponseHandler responseWriter = (byteBuffer, bytesRead, responseIsFinished) -> {
            if (responseIsFinished) {
                // Message is finish. Set selector state to written
                writeBuffer.put((byte) '\r');
                writeBuffer.put((byte) '\n');
                writeBuffer.put((byte) '\r');
                writeBuffer.put((byte) '\n');
                writeBuffer.flip();
                ccb.setConnectionState(ConnectionState.WRITTEN);
            } else {
                // Otherwise, write byte buffer

                for (int i = 0; i < bytesRead; i++) {

                    writeBuffer.put(byteBuffer[i]);
                }
                ccb.setConnectionState(ConnectionState.WRITE);
            }

        };

        // Handle response

        requestHandler.handleRequest(responseWriter, syncData);


    }

    private void closeSocket(SocketChannel socketChannel) {
        numActiveConnections--;
        syncData.dropConnection();
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
//        System.out.println("Still need to write " + writeBuffer.array());
        if ((writeBytes == -1 || writeBuffer.remaining() == 0) && ccb.getConnectionState() == ConnectionState.WRITTEN) {
            ccb.setConnectionState(ConnectionState.TRANSMITTED);
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
                        syncData.addConnection();
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

                        // Get channel
                        SocketChannel client = (SocketChannel) key.channel();

                        int readBytes = client.read(ccb.getReadBuffer());
                        System.out.println("Read " + readBytes + " bytes");
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
                        if (ccb.getConnectionState() != ConnectionState.WRITE && ccb.getConnectionState() != ConnectionState.WRITTEN) {
                            continue;
                        }

                        SocketChannel client = (SocketChannel) key.channel();
                        int writeBytes = client.write(ccb.getWriteBuffer());
//                        System.out.println("Wrote " + writeBytes + " bytes");
                        updateCCBOnWrite(writeBytes, ccb);
                        // When finish writing, close socket
                        if (ccb.getConnectionState() == ConnectionState.TRANSMITTED) {
                            // Unless keep connection alive
                            if (ccb.isKeepConnectionAlive()) {
                                client.socket().setKeepAlive(true);
    //                                System.out.println("Connection alive");

                                ccb.resetState();
                                ccb.setLastReadTime(System.currentTimeMillis());
                                ccb.setConnectionState(ConnectionState.READING);

                                HTTPRequestHandler newHttpRequestHandler = new HTTPRequestHandler(this.serverConfig, client);
                                ccb.setRequestHandler(newHttpRequestHandler);

                            } else {
                                closeSocket(client);
                            }

                        }
                    }
                } catch (IOException e) {
                    System.out.println("Server already shut down");
                    break;
//                    e.printStackTrace();
                } catch (CancelledKeyException e) {
                    System.out.println("Server already shut down");
                    break;
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
