import ConfigParser.ServerConfigObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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

    public static Map<String, String> parseRequest(String httpRequest) {
        Map<String, String> requestMap = new HashMap<>();
        String[] lines = httpRequest.split("\\r\\n");
        String[] requestLine = lines[0].split(" ");
        requestMap.put("Method", requestLine[0]);
        requestMap.put("Path", requestLine[1]);
        requestMap.put("Version", requestLine[2]);
        int bodyStartIndex = -1;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) {
                bodyStartIndex = i + 1;
                break;
            }
            String[] header = lines[i].split(": ");
            requestMap.put(header[0], header[1]);
        }
        if (bodyStartIndex != -1 && bodyStartIndex < lines.length) {
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i = bodyStartIndex; i < lines.length; i++) {
                bodyBuilder.append(lines[i]);
            }
            requestMap.put("Body", bodyBuilder.toString());
        }
        return requestMap;
    }

    public static String processRequest(String request) {
        String response = "";
        Map<String, String> requestMap = parseRequest(request);

        try {
            // if (!(requestMap.get("Version").endsWith("0.9") || requestMap.get("Version").endsWith("1.0") || requestMap.get("Version").endsWith("1.1"))) {
            //     System.out.println(requestMap.get("Version").endsWith("1.1"));
            //     System.out.println(requestMap.get("Version"));
            //     System.out.println(requestMap.get("Version").replace("\\", "\\\\"));
            //     throw new ResponseException("Invalid HTTP version: " + requestMap.get("Version"), 400);
            // }

            if (!(requestMap.get("Method").equals("GET"))) {
                throw new ResponseException("Invalid method: " + requestMap.get("Method"), 405);
            }

            File responseFile = getFileFromPath(requestMap.get("Path"));
            response = getFileOutput(responseFile);
            
        } catch (ResponseException e) {
            return e.getStatusCode() + " " + e.getMessage();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }

    private static String getFileOutput(File responseFile) throws IOException{
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(responseFile));
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line);
            content.append(System.lineSeparator());
        }
        reader.close();
        return content.toString();
    }

    private static File getFileFromPath(String path) throws ResponseException {
        String baseDirectory = "../www";
        File res;

        // TODO: MIME checking
        // TODO: security attack handling (../)

        if (path.equals("/")) {
            res = new File(baseDirectory + "/index.html");
        } else {
            res = new File(baseDirectory + path);
        }

        if (!res.exists()){
            throw new ResponseException(path + " could not be found", 404);
        }

        return res;
    }

    private void generateResponse(ConnectionControlBlock ccb) {
        StringBuffer request = ccb.getRequest();
        ByteBuffer writeBuffer = ccb.getWriteBuffer();
        String result = processRequest(request.toString());
//        System.out.println(result);

        String CRLF = "\r\n";
        String response =
            "HTTP/1.1 200 OK" + CRLF +
            "Content-Type: text/html; charset=UTF-8" + CRLF +
            "Content-Length: " + result.getBytes().length + CRLF + CRLF +
            result + CRLF ;


        // Generate Response
        for (int i = 0; i < response.length(); i++) {
            char ch = response.charAt(i);

            ch = Character.toUpperCase(ch);

            writeBuffer.put((byte) ch);
        }

        // CGI Test


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
                ccb.lastFour[ccb.i % 4] = ch;
                if (ccb.i >= 3
                        && ccb.lastFour[ccb.i % 4] == '\n'
                        && ccb.lastFour[(ccb.i-1) % 4] == '\r'
                        && ccb.lastFour[(ccb.i-2) % 4] == '\n'
                        && ccb.lastFour[(ccb.i-3) % 4] == '\r') {
                    ccb.setConnectionState(ConnectionState.READ);
                }
                ccb.i++;
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
//            System.out.println(workerID + " received accept. Active: " + numActiveConnections + " Total: " + syncData.getNumConnections() + " # Workers: " + syncData.getNumWorkers() + " Accept: " + (numActiveConnections > averageConnectionPerWorker));

            return (numActiveConnections > averageConnectionPerWorker);
        }
    }

    private boolean serverIsRunning() {
        synchronized (syncData) {
//            System.out.println("Server running: " + syncData.getServerIsRunning());
            return syncData.getServerIsRunning();
        }
    }
    public void run() {
        System.out.println("Worker #" + workerID + " listening");
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
                        System.out.println("\n\n\n\nWorker " + workerID + " accepted connection from " + client.getRemoteAddress());
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

        for (SelectionKey key : selector.keys()) {
            try {
                key.channel().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
