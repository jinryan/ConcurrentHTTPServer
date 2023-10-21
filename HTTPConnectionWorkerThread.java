import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class HTTPConnectionWorkerThread extends Thread {

    private Socket socket;
    public HTTPConnectionWorkerThread(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();

            // TODO we would write
            String html = "<html><head><title>Simple HTTP Server</title></head><body><h1>Hello World 1</h1></body></html>";

            final String CRLF = "\n\r"; // 13, 10 in ASCII
            // Need to wrap string in HTTP response
            String response =
                "HTTP/1.1 200 OK" + CRLF +
                "Content-Length: " + html.getBytes().length + CRLF + CRLF +
                html + CRLF + CRLF;
            
            System.out.println("Prepared");
            sleep(5000);
            System.out.println("Returning value");

            outputStream.write(response.getBytes());
            // inputStream.close();
            // outputStream.close();
            // socket.close();
        } catch (IOException e) {
            e.printStackTrace();
            // TODO HANDLE LATER
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
