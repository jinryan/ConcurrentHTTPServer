import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import java.util.HashMap;
import java.util.Map;

class ResponseException extends Exception {
    private final int statusCode; 
    
    public int getStatusCode() {
        return statusCode;
    }

    public ResponseException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}

public class HTTPConnectionWorkerThread extends Thread {

    private Socket socket;
    public HTTPConnectionWorkerThread(Socket socket) {
        this.socket = socket;
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
        Map<String, String> requestMap = parseRequest(request);
        try {
            if (!(requestMap.get("Version").endsWith("0.9") || requestMap.get("Version").endsWith("1.0") || requestMap.get("Version").endsWith("1.1"))) {
                throw new ResponseException("Invalid HTTP version: " + requestMap.get("Version"), 400);
            }

            if (!(requestMap.get("Method").equals("GET"))) {
                throw new ResponseException("Invalid method: " + requestMap.get("Method"), 405);
            }

            
        } catch (ResponseException e) {
            return e.getStatusCode() + " " + e.getMessage();
        }

        String response = "";
        
        for (Map.Entry<String, String> entry : requestMap.entrySet()) {
            response += entry.getKey() + ": " + entry.getValue();
        }
        return response;
    }

    @Override
    public void run() {
        try {
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();

            String getRequest = "GET /api/books HTTP/1.2\r\nHost: www.example.com\r\nUser-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36\r\nAccept: application/json\r\n\r\n";
            // String postRequest = "POST /api/books HTTP/1.1\r\nHost: www.example.com\r\nUser-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36\r\nContent-Type: application/json\r\nContent-Length: 35\r\n\r\n{\r\n  \"title\": \"Java Programming\",\r\n  \"author\": \"John Doe\"\r\n}";
            String result = processRequest(getRequest);


            // TODO we would write
            String html = "<html><head><title>Simple HTTP Server</title></head><body><h1>Hello World 1</h1></body></html>";

            final String CRLF = "\r\n"; // 13, 10 in ASCII
            // Need to wrap string in HTTP response
            String response =
                "HTTP/1.1 200 OK" + CRLF +
                "Content-Length: " + result.getBytes().length + CRLF + CRLF +
                result + CRLF + CRLF;
            
            System.out.println("Prepared");
            // sleep(5000);
            System.out.println("Returning value");

            outputStream.write(response.getBytes());
            // inputStream.close();
            // outputStream.close();
            // socket.close();
        } catch (IOException e) {
            e.printStackTrace();
            // TODO HANDLE LATER
        }
    }
}
