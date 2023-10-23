import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import ConfigParser.ServerConfigObject;

public class HTTPRequestHandler implements RequestHandler {
    private String request;
    public Map<String, String> requestMap;
    private final ServerConfigObject serverConfig;
    public String filePath;
    public String lastModifiedDate;
    public String fileType;

    char[] lastFour = {0, 0, 0, 0};
    int i;

    private static final Map<Integer, String> statusCodeMessages;

    static {
        statusCodeMessages = new HashMap<>();
        statusCodeMessages.put(200, "OK");
        statusCodeMessages.put(304, "Not Modified");
        statusCodeMessages.put(400, "Bad Request");
        statusCodeMessages.put(401, "Unauthorized");
        statusCodeMessages.put(404, "Not Found");
        statusCodeMessages.put(405, "Method Not Allowed");
        statusCodeMessages.put(406, "Not Acceptable");
        statusCodeMessages.put(408, "Request Timeout");
        statusCodeMessages.put(500, "Internal Server Error");

    }

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

    private void validateRequest() throws ResponseException {
        if (!(requestMap.get("Version").startsWith("HTTP/") && (requestMap.get("Version").endsWith("0.9") || requestMap.get("Version").endsWith("1.0") || requestMap.get("Version").endsWith("1.1")))) {
            throw new ResponseException("Invalid HTTP version: " + requestMap.get("Version"), 400);
        }

        if (!(requestMap.get("Method").equals("GET"))) {
            throw new ResponseException("Invalid method: " + requestMap.get("Method"), 405);
        }
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

    private File handleFileFromPath(String path) throws ResponseException, IOException {
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

        if (requestMap.get("Method").equals("GET")) {
            checkType(uri);
        } else if (requestMap.get("Method").equals("POST")) {
            System.out.println("POST REQUEST OH NO!");
        } else {
            throw new ResponseException("Invalid method " + requestMap.get("Method"), 405);
        }

        return res;
    }

    private void checkType(String uri) throws ResponseException, IOException {
        String mimeType = Files.probeContentType(Paths.get(uri));
        fileType = mimeType;

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
        String res;

        if ((documentRoot + path).contains("../") || (documentRoot + path).endsWith("/..") || (documentRoot + path).equals(".."))
            throw new ResponseException(path + " is not a valid path", 404);

        Path currentPath = Paths.get("");
        String currentAbsolutePath = currentPath.toAbsolutePath().toString();

        if (path.equals("/")) {
            res = currentAbsolutePath + "/../" + documentRoot + path + "index.html";
        } else {
            res = currentAbsolutePath + "/../" + documentRoot + path;
        }

        if (requestMap.get("User-Agent") != null && path.equals("/") && requestMap.get("User-Agent").contains("iPhone")) {
            Path testPath = Paths.get(currentAbsolutePath + "/../" + documentRoot + path + "index_m.html");
            if (Files.exists(testPath)) {
                res = testPath.toString();
            }
        }

        // Check for malformed path
        try {
            new URI(res);
        } catch (URISyntaxException e) {
            throw new ResponseException(path + " is not a valid path", 404);
        }

        filePath = res;

        return res;
    }

    public String getResponse() {
        //System.out.println(request);
        try {
            validateRequest();
            File responseFile = handleFileFromPath(requestMap.get("Path"));

            long lastModifiedTimestamp = responseFile.lastModified();
            Date lastModifiedDateDate = new Date(lastModifiedTimestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            lastModifiedDate = sdf.format(lastModifiedDateDate);

            // TODO: add if-modified-since caching here
            String responseBody = getFileOutput(responseFile);
            String responseHeaders = generateHeaders(200, responseBody);

            return responseHeaders + responseBody;

        } catch (ResponseException e) {
            return generateHeaders(e.getStatusCode(), e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String generateHeaders(int statusCode, String responseBody) {
        String CRLF = "\r\n";
        String res = "";

        // First response line
        res += requestMap.get("Version") + " " + statusCode + " " + statusCodeMessages.get(statusCode) + CRLF;

        // Date
        ZonedDateTime currentDateTime = ZonedDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz");
        res += "Date: " + currentDateTime.format(formatter) + CRLF;

        // Server
        res += "Server: Addison-Ryan Server Java/1.21" + CRLF;

        // Last-Modified
        res += "Last-Modified: " + lastModifiedDate + CRLF;

        // Content-Type
        res += "Content-Type: " + (fileType == null ? "text/plain" : fileType) + CRLF;

        // Content-Length
        res += "Content-Length: " + responseBody.getBytes().length + CRLF + CRLF;

        return res;
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
