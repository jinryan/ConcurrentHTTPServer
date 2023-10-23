import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import ConfigParser.ServerConfigObject;

public class HTTPRequestHandler implements RequestHandler {
    private String request;
    public Map<String, String> requestMap;
    private ServerConfigObject serverConfig;
    char[] lastFour;
    int i = 0;

    public HTTPRequestHandler(StringBuffer request, ServerConfigObject serverConfig) {
        this.request = request.toString();
        this.serverConfig = serverConfig;
        requestMap = new HashMap<>();
    }

    public HTTPRequestHandler(ServerConfigObject serverConfig) {
        this.serverConfig = serverConfig;
    }

    public void readCharsToRequest(char c) {
        this.request += c;
        this.lastFour[i % 4] = c;
        i++;
    }

    public void parseRequest() {
        System.out.println("Parsing " + this.request);
        String[] lines = this.request.split("\\r\\n");
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
    }

    private String processRequest() {
        String response = "";

        try {
            if (!(requestMap.get("Version").startsWith("HTTP/") &&
                    (requestMap.get("Version").endsWith("0.9") || requestMap.get("Version").endsWith("1.0") || requestMap.get("Version").endsWith("1.1")))) {
                System.out.println("Response version is |" + requestMap.get("Version") + "|");
                throw new ResponseException("Invalid HTTP version: " + requestMap.get("Version"), 400);
            }

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

    private String getFileOutput(File responseFile) throws IOException{
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

    private File getFileFromPath(String path) throws ResponseException {
        int port = 8080;

        String documentRoot = serverConfig.getRootFrom(requestMap.get("Host"), port);
        if (requestMap.get("Host") == null || requestMap.get("Host").startsWith("localhost"))
            documentRoot = serverConfig.getFirstRoot(port);

        if (documentRoot == null)
            throw new ResponseException("Host " + requestMap.get("Host") + " could not be resolved", 404);

        String uri = getURI(path, documentRoot);
        File res = new File(uri);


        // TODO: MIME checking

        if (!res.exists()){
            throw new ResponseException(path + " could not be found", 404);
        }

        return res;
    }

    private String getURI(String path, String documentRoot) throws ResponseException{
        String res = "";

        if ((documentRoot + path).contains("../") || (documentRoot + path).endsWith("/..") || (documentRoot + path).equals(".."))
            throw new ResponseException(path + " is not a valid path", 404);

        if (path.equals("/")) {
            res = "../../" + documentRoot + "/index.html";
        } else {
            res = "../../" + documentRoot + path;
        }

        // Check for malformed path
        try {
            new URI(res);
        } catch (URISyntaxException e) {
            throw new ResponseException(path + " is not a valid path", 404);
        }

        return res;
    }

    public String getResponse() {
        System.out.println(request);
        String result = processRequest();

        String CRLF = "\r\n";
        String response =
            "HTTP/1.1 200 OK" + CRLF +
            "Content-Type: text/html; charset=UTF-8" + CRLF +
            "Content-Length: " + result.getBytes().length + CRLF + CRLF +
            result;
            
        return response;
    }


    public boolean requestCompleted() {
        return (i > 3
                && lastFour[(i-1) % 4] == '\n'
                && lastFour[(i-2) % 4] == '\r'
                && lastFour[(i-3) % 4] == '\n'
                && lastFour[(i-4) % 4] == '\r');
    }
}
