import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
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

    private SocketChannel socketChannel;

    private char[] lastFour = {0, 0, 0, 0};
    private int i;
    private int expectedContentFromBody;
    private boolean readingBody = false;

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

    public HTTPRequestHandler(ServerConfigObject serverConfig, SocketChannel socketChannel) {
        this.serverConfig = serverConfig;
        this.requestMap = new HashMap<>();
        this.request = "";
        this.socketChannel = socketChannel;
    }

    public boolean keepAlive() {
        return requestMap.get("Connection") != null && requestMap.get("Connection").equals("keep-alive");
    }

    public void parseRequest() {
//        System.out.println("Request is\n========\n" + this.request + "========\n");
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
        if (!((requestMap.get("Method").equals("GET")) || requestMap.get("Method").equals("POST"))) {

            throw new ResponseException("Invalid method " + requestMap.get("Method"), 405);
        }
    }

    private String getLastModifiedDate(File responseFile) {
        long lastModifiedTimestamp = responseFile.lastModified();
        Date lastModifiedDateDate = new Date(lastModifiedTimestamp);
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(lastModifiedDateDate);
    }

    private String getCurrentDate() {
        ZonedDateTime currentDateTime = ZonedDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz");
        return currentDateTime.format(formatter);
    }
    


    private boolean compareDates() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
            Date fileDate = sdf.parse(lastModifiedDate);
            Date requestDate = sdf.parse(requestMap.get("If-Modified-Since"));
            return fileDate.compareTo(requestDate) < 0;
        } catch (ParseException e) {
            e.printStackTrace();
            return false;
        }
    }

    private String processGET() throws ResponseException, IOException {
        String response;
        File responseFile = getPath(requestMap.get("Path"));
        checkType(filePath);

        lastModifiedDate = getLastModifiedDate(responseFile);
        if (requestMap.get("If-Modified-Since") != null) {
            if (compareDates()) {
                throw new ResponseException("File is cached", 304);
            }
        }
        response = getFileOutput(responseFile);
        return response;
    }

    private String processPOST() throws IOException, ResponseException {
        int port = socketChannel.socket().getLocalPort();
//        System.out.println("POST from " + socketChannel.socket().getLocalPort());
        lastModifiedDate = getCurrentDate();

        String documentRoot = getDocumentRoot(port);

        String cgiPath = requestMap.get("Path");
        String uri = getURI(cgiPath, documentRoot);

//        String encodedURI = uri.replace(" ", "%20");
        File f = new File(uri);
        if (!f.exists() || f.isDirectory()) {
            throw new ResponseException("Host " + requestMap.get("Host") + " could not be resolved", 404);
        }

        String queryString = requestMap.get("Body");
        System.out.println("Fetching file from " + uri);
        return runCGIProgram(uri, queryString, socketChannel.getRemoteAddress().toString(), socketChannel.getLocalAddress().toString(), "POST");
    }

    private String processRequest() throws ResponseException{
        String responseBody;

        try {
            if (requestMap.get("Method").equals("GET")) {
                responseBody = processGET();
            } else if (requestMap.get("Method").equals("POST")) {
                responseBody =  processPOST();
            } else {
                throw new ResponseException("Invalid method: " + requestMap.get("Method"), 405);
            }
            return generateHeaders(200, responseBody);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
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

    private File getPath(String path) throws ResponseException {
        ArrayList<Integer> ports = serverConfig.getPorts();

        String documentRoot = null;

        for (int port : ports) {
            documentRoot = getDocumentRoot(port);
            if (documentRoot != null) {
                break;
            }
        }

        if (documentRoot == null) {
            throw new ResponseException("Host " + requestMap.get("Host") + " could not be resolved", 404);
        }

        String uri = getURI(path, documentRoot);
        File res = new File(uri);

        if (!res.exists()){
            throw new ResponseException(path + " could not be found", 404);
        }

        return res;
    }

    private String getDocumentRoot(int port) throws ResponseException {
        String documentRoot = serverConfig.getRootFrom(requestMap.get("Host"), port);

        if (requestMap.get("Host") == null || requestMap.get("Host").startsWith("localhost")){
            documentRoot = serverConfig.getFirstRoot(port);
        }

        return documentRoot;
    }

    private void checkType(String uri) throws ResponseException, IOException {
        String mimeType = Files.probeContentType(Paths.get(uri));
        fileType = mimeType;

        if (mimeType == null)
            throw new ResponseException("Invalid file type: requested file is a folder", 406);

        if (requestMap.get("Accept") != null) {
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
    }


    private String getURI(String path, String documentRoot) throws ResponseException {
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
            String encodedRes = res.replace(" ", "%20");
            new URI("file://" + encodedRes);
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
            getPath(requestMap.get("Path"));
            authorizeRequest();
            String responseBody = processRequest();
            assert responseBody != null;
            return generateHeaders(200, responseBody);
        } catch (ResponseException e) {
            return generateHeaders(e.getStatusCode(), e.getMessage());
        }
    }

    private void authorizeRequest() throws ResponseException{
        // check if htaccess exists
        String htaccessPath = filePath.substring(0, filePath.lastIndexOf("/") + 1) + ".htaccess";
        File htaccessFile = new File(htaccessPath);

        if (htaccessFile.exists()) {
            Map<String, String> htaccessMap = parseHtaccess(htaccessFile);

            // check if auth header exists and is valid
            if (!(requestMap.get("Authorization") != null && requestMap.get("Authorization").startsWith("Basic "))) {
                throw new ResponseException("~" + htaccessMap.get("AuthName"), 401);
            } else {
                // decode auth header
                String encodedAuth = requestMap.get("Authorization").substring(requestMap.get("Authorization").indexOf(" ") + 1);
                byte[] decodedBytes = Base64.getDecoder().decode(encodedAuth);
                String decodedString = new String(decodedBytes);
                String[] credentials = decodedString.split(":");
                if (credentials.length != 2)
                    throw new ResponseException(htaccessMap.get("AuthName"), 401);

                String username = credentials[0];
                String password = credentials[1];

                // check that username and password match
                if (!username.equals(htaccessMap.get("User")) || !password.equals(htaccessMap.get("Password"))) {
                    throw new ResponseException(htaccessMap.get("AuthName"), 401);
                }
            }
        }
    }

    private Map<String, String> parseHtaccess(File htaccessFile) {
        HashMap<String, String> res = new HashMap<>();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(htaccessFile));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+", 2);
                if (parts.length == 2) {
                    String key = parts[0];
                    String value = parts[1];
                    res.put(key, value.replaceAll("^\"|\"$", ""));
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }

    private String generateHeaders(int statusCode, String responseBody) {
        boolean missingPassword = false;
        String CRLF = "\r\n";
        String res = "";

        // First response line
        res += requestMap.get("Version") + " " + statusCode + " " + statusCodeMessages.get(statusCode) + CRLF;

        // Date
        res += "Date: " + getCurrentDate() + CRLF;

        // Server
        res += "Server: Addison-Ryan Server Java/1.21" + CRLF;

        // Last-Modified
        res += "Last-Modified: " + lastModifiedDate + CRLF;

        // Optional WWW-Authenticate
        if ((statusCode == 401) && (responseBody.charAt(0) == '~')) {
            res += "WWW-Authenticate: Basic Realm=" + responseBody.substring(1) + CRLF;
            missingPassword = true;
        }

        // Content-Type
        res += "Content-Type: " + (fileType == null ? "text/plain" : fileType) + CRLF;

        // Content-Length
        res += "Content-Length: " + (missingPassword ? 0 : responseBody.getBytes().length) + CRLF + CRLF;

        res += (missingPassword ? "" : responseBody);
        return res;
    }

    public String runCGIProgram(String programPath, String queryString, String remotePort, String serverPort, String method) throws IOException {
        String perlInterpreter = "perl";
        ProcessBuilder processBuilder = new ProcessBuilder(perlInterpreter, programPath);
        processBuilder.environment().put("QUERY_STRING", queryString);
        processBuilder.environment().put("REMOTE_*", remotePort);
        processBuilder.environment().put("SERVER_*", serverPort);
        processBuilder.environment().put("REQUEST_METHOD", method);


        Process process = processBuilder.start();
        InputStream inputStream = process.getInputStream();

        byte[] buffer = new byte[1024];
        int bytesRead;
        StringBuilder output = new StringBuilder();

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            output.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }
        return output.toString();
    }

    public void readCharsToRequest(char c) {
        this.request += c;
        this.lastFour[i % 4] = c;
        i++;
        if (readingBody) {
            expectedContentFromBody--;
        }
    }

    public boolean requestCompleted() {
        if (readingBody && expectedContentFromBody == 0) {
            return true;
        } else if (!readingBody
                && i > 3
                && lastFour[(i-1) % 4] == '\n'
                && lastFour[(i-2) % 4] == '\r'
                && lastFour[(i-3) % 4] == '\n'
                && lastFour[(i-4) % 4] == '\r') {
            parseRequest();
            if (requestMap.containsKey("Content-length")) {
                expectedContentFromBody = Integer.parseInt(requestMap.get("Content-length"));
                readingBody = true;
            } else {
                return true;
            }
        }
        return false;
    }


}
