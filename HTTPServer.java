import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class HTTPServer {
    public static void main(String[] args) {
        System.out.println("Server initiated");
        int port = 8080;
        try {
            /* Difference between ServerSocket and Socket in Java
             * ServerSocket = welcome socket
             * Socket establishes actual connection
             */
            
            ServerSocket serverSocket = new ServerSocket(port);
            Socket socket = serverSocket.accept();

            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();

            // TODO we would write
            String html = "<html><head><title>Simple HTTP Server</title></head><body><h1>Hello World</h1></body></html>";

            final String CRLF = "\n\r"; // 13, 10 in ASCII
            // Need to wrap string in HTTP response
            String response =
                "HTTP/1.1 200 OK" + CRLF +
                "Content-Length: " + html.getBytes().length + CRLF + CRLF +
                html + CRLF + CRLF;

            outputStream.write(response.getBytes());
            // What would happen if we don't close them?
            inputStream.close();
            outputStream.close();
            socket.close();
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}