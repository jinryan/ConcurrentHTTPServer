// package ConcurrentHTTPServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerListenerThread extends Thread {

    private int port;
    private String webroot;
    private ServerSocket serverSocket;

    public ServerListenerThread(int port, String webroot) throws IOException {
        this.port = port;
        this.webroot = webroot;
        this.serverSocket = new ServerSocket(this.port);
    }


    @Override
    public void run() {
        try {
            /* Difference between ServerSocket and Socket in Java
             * ServerSocket = welcome socket
             * Socket establishes actual connection
             */
            
            while (serverSocket.isBound() && !serverSocket.isClosed()) {
                System.out.println("Server waiting for incoming connections");
                Socket socket = this.serverSocket.accept();
                HTTPConnectionWorkerThread workerThread = new HTTPConnectionWorkerThread(socket);
                workerThread.start();
            }
            
            // What would happen if we don't close them?
            
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
}
