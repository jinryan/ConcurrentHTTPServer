import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import static java.lang.Thread.sleep;

public class HTTPServer {

    private int port;
    public HTTPServer() {
        this.port = 8080;
    }

    private void generateResponse(ConnectionControlBlock ccb) {
        StringBuffer request = ccb.getRequest();
        ByteBuffer writeBuffer = ccb.getWriteBuffer();

        for (int i = 0; i < request.length(); i++) {
            char ch = request.charAt(i);

            ch = Character.toUpperCase(ch);

            writeBuffer.put((byte) ch);
        }
        writeBuffer.flip();
        ccb.setConnectionState(ConnectionState.WRITE);
    }

    private void closeSocket(SocketChannel socketChannel) {
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

    public void start() {

        // Setup Server Channel
        ServerSocketChannel serverChannel;
        Selector selector;
        try {
            serverChannel = ServerSocketChannel.open(); // Creates an unbounded socket
            ServerSocket serversocket = serverChannel.socket();
            InetSocketAddress address = new InetSocketAddress(port);
            serversocket.bind(address);
            serverChannel.configureBlocking(false);
            selector = Selector.open(); // Singleton
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // Listen for incoming connections

        while (true) {
            try {
                selector.select(); // Blocking operation, returns only after a channel is selected
            } catch (IOException e) {
                e.printStackTrace();
            }
            Set readyKeys = selector.selectedKeys();
            Iterator iterator = readyKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = (SelectionKey) iterator.next();
                iterator.remove();
                try {
                    if (key.isAcceptable()) {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel client = server.accept();
                        System.out.println("Accepted connection from " + client);
                        client.configureBlocking(false);
                        SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                        ConnectionControlBlock connectionControlBlock = new ConnectionControlBlock();
                        connectionControlBlock.setConnectionState(ConnectionState.READING);
                        clientKey.attach(connectionControlBlock);
                    }
                    if (key.isReadable()) {
                        ConnectionControlBlock ccb = (ConnectionControlBlock) key.attachment();
                        if (ccb.getConnectionState() != ConnectionState.READING) {
                            continue;
                        }

                        SocketChannel client = (SocketChannel) key.channel();

                        int readBytes = client.read(ccb.getReadBuffer());
                        updateCCBOnRead(readBytes, ccb);

                        if (ccb.getConnectionState() == ConnectionState.READ) {
                            // Generate Response
                            generateResponse(ccb);
                        }
                    }

                    if (key.isWritable()) {
                        ConnectionControlBlock ccb = (ConnectionControlBlock) key.attachment();
                        if (ccb.getConnectionState() != ConnectionState.WRITE) {
                            continue;
                        }
                        SocketChannel client = (SocketChannel) key.channel();
                        int writeBytes = client.write(ccb.getWriteBuffer());
                        updateCCBOnWrite(writeBytes, ccb);

                        if (ccb.getConnectionState() == ConnectionState.WRITTEN) {
                            closeSocket(client);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
