// package ConcurrentHTTPServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class HTTPServer {
    public static volatile boolean stopServer = false;
    public static void main(String[] args) {
        
        int port = 8080;
        
        try {
            ServerListenerThread serverListenerThread = new ServerListenerThread(port, "www");
            serverListenerThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }
}