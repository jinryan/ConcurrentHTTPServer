import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import ConfigParser.ConfigNode;
import ConfigParser.ServerConfigObject;

public class HTTPRequestHandler implements RequestHandler {
    private String request;
    public Map<String, String> requestMap;
    private final ServerConfigObject serverConfig;

    char[] lastFour = {0, 0, 0, 0};
    int i;

    public HTTPRequestHandler(StringBuffer request, ServerConfigObject serverConfig) {
        this.request = request.toString();
        this.serverConfig = serverConfig;
        this.requestMap = new HashMap<>();
    }

    public HTTPRequestHandler(ServerConfigObject serverConfig) {
        this.serverConfig = serverConfig;
        this.requestMap = new HashMap<>();
        this.request = "";
    }

    public void parseRequest() {
        String[] lines = this.request.split("\\r\\n");
        String[] requestLine = lines[0].split(" ");
        System.out.println("Request line is " + lines[0]);
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
            if (!(requestMap.get("Version").startsWith("HTTP/") && (requestMap.get("Version").endsWith("0.9") || requestMap.get("Version").endsWith("1.0") || requestMap.get("Version").endsWith("1.1")))) {
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

    private File getFileFromPath(String path) throws ResponseException, IOException {
        int port = 8080;

        String documentRoot = serverConfig.getRootFrom(requestMap.get("Host"), port);
        if (requestMap.get("Host") == null || requestMap.get("Host").startsWith("localhost"))
            documentRoot = serverConfig.getFirstRoot(port);

        if (documentRoot == null)
            throw new ResponseException("Host " + requestMap.get("Host") + " could not be resolved", 404);

        String uri = getURI(path, documentRoot);
        File res = new File(uri);

        if (!res.exists()){
            throw new ResponseException(path + " could not be found", 404);
        }

        checkType(uri);

        return res;
    }

    private void checkType(String uri) throws ResponseException, IOException {
        String mimeType = Files.probeContentType(Paths.get(uri));

        if (mimeType == null)
            throw new ResponseException("Invalid file type: requested file is a folder", 406);

        String[] types = requestMap.get("Accept").split(",\\s*");

        String mimeStart = mimeType.substring(0, mimeType.indexOf("/"));
        String mimeEnd = mimeType.substring(mimeType.indexOf("/") + 1);

        for (String type : types) {
            String typeStart = type.substring(0, type.indexOf("/"));
            String typeEnd  = type.substring(type.indexOf("/") + 1);
            if ((typeStart.equals(mimeStart) || typeStart.equals("*")) && (typeEnd.equals(mimeEnd) || typeEnd.equals("*"))) {
                return;
            }
        }

        throw new ResponseException("Invalid file type " + mimeType, 406);

    }


    private String getURI(String path, String documentRoot) throws ResponseException{
        String res = "";

        if ((documentRoot + path).contains("../") || (documentRoot + path).endsWith("/..") || (documentRoot + path).equals(".."))
            throw new ResponseException(path + " is not a valid path", 404);

        Path currentPath = Paths.get("");
        String currentAbsolutePath = currentPath.toAbsolutePath().toString();

        if (path.equals("/")) {
            res = currentAbsolutePath + "/../" + documentRoot + "/index.html";
        } else {
            res = currentAbsolutePath + "/../" + documentRoot + path;
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
//        System.out.println(request);
        String result = processRequest();

        String CRLF = "\r\n";
        String response =
                "HTTP/1.1 200 OK" + CRLF +
                        "Content-Type: text/html; charset=UTF-8" + CRLF +
                        "Content-Length: " + result.getBytes().length + CRLF + CRLF +
                        result;

        return response;
    }

    public void readCharsToRequest(char c) {
        this.request += c;
        this.lastFour[i % 4] = c;
        i++;
    }

    public boolean requestCompleted() {
        return (i > 3
                && lastFour[(i-1) % 4] == '\n'
                && lastFour[(i-2) % 4] == '\r'
                && lastFour[(i-3) % 4] == '\n'
                && lastFour[(i-4) % 4] == '\r');
    }


}
