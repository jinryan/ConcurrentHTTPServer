import java.net.URI;
import java.net.URISyntaxException;
import java.io.*;
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

    private final SocketChannel socketChannel;

    private final char[] lastFour = {0, 0, 0, 0};
    private int i;
    private int expectedContentFromBody;
    private boolean chunkEncodedBody = false;
    private boolean readingBody = false;
    public boolean isRequestBroken = false;

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

    public HTTPRequestHandler(ServerConfigObject serverConfig, SocketChannel socketChannel) {
        this.serverConfig = serverConfig;
        this.requestMap = new HashMap<>();
        this.request = "";
        this.socketChannel = socketChannel;
    }

    // given a request, parse the headers into a hashmap (requestMap), and the body into the "Body" field of the hashmap
    public void parseRequest() {
        String[] lines = this.request.split("\\r\\n");
        if (lines.length == 0) {
            isRequestBroken = true;
            return;
        }

        String[] requestLine = lines[0].split(" ");
        if (requestLine.length != 3) {
            isRequestBroken = true;
            return;
        }

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


    public void handleRequest(HTTPResponseHandler responseHandler, WorkersSyncData syncData) {
        try {
            validateRequest();
            checkIfLoad(requestMap.get("Path"), syncData);
            getPath(requestMap.get("Path"));
            authorizeRequest();
            if (requestMap.containsKey("Transfer-Encoding") && (requestMap.get("Transfer-Encoding")).equals("chunked")) {
                // Chunked encoding
                processChunkedRequest(responseHandler);
            } else {
                // Non-chunk encoding
                byte[] httpResponse = processRequest();
                assert httpResponse != null;

                responseHandler.apply(httpResponse, httpResponse.length, false);
                responseHandler.apply(httpResponse, 0, true);
            }
        } catch (ResponseException e) {
            String message = e.getStatusCode() + " " + e.getMessage();
            byte[] httpResponse = generateFullResponse(e.getStatusCode(), message.getBytes(), e.getHasEmptyAuthentication());
            responseHandler.apply(httpResponse, httpResponse.length, false);
            responseHandler.apply(httpResponse, 0, true);
        }
    }

    private void processChunkedRequest(HTTPResponseHandler responseHandler) throws ResponseException{
        try {
            if (requestMap.get("Method").equals("POST")) {
                processPOSTChunked(responseHandler);
            } else {
                // Cannot use chunked encoding on GET requests
                throw new ResponseException("Invalid method: " + requestMap.get("Method"), 405);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processPOSTChunked(HTTPResponseHandler responseHandler) throws IOException, ResponseException {

        int port = socketChannel.socket().getLocalPort();
        lastModifiedDate = getCurrentDate();

        String documentRoot = getDocumentRoot(port);

        String cgiPath = requestMap.get("Path");
        String uri = getURI(cgiPath, documentRoot);

        File f = new File(uri);
        if (!f.exists() || f.isDirectory()) {
            throw new ResponseException("Host " + requestMap.get("Host") + " could not be resolved", 404);
        }

        String queryString = requestMap.get("Body");
        
        runCGIProgramChunked(uri, queryString, socketChannel.getRemoteAddress().toString(), socketChannel.getLocalAddress().toString(), "POST", responseHandler);
    }

    private byte[] CRLFBytes() {
        String CRLF = "\r\n";
        return CRLF.getBytes();
    }

    public void runCGIProgramChunked(String programPath, String queryString, String remotePort, String serverPort, String method, HTTPResponseHandler responseHandler) throws IOException {
        String perlInterpreter = "perl";

        ProcessBuilder processBuilder = new ProcessBuilder(perlInterpreter, programPath);
        processBuilder.environment().put("QUERY_STRING", queryString);
        processBuilder.environment().put("REMOTE_*", remotePort);
        processBuilder.environment().put("SERVER_*", serverPort);
        processBuilder.environment().put("REQUEST_METHOD", method);

        processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);

        Process process = processBuilder.start();
        process.getOutputStream().write(queryString.getBytes());
        process.getOutputStream().close();

        
        InputStream inputStream = process.getInputStream();

        byte[] buffer = new byte[1024];
        int bytesRead;
        byte[] chunkedHeader = generateChunkedHeaders(200);

        // Write header
        responseHandler.apply(chunkedHeader, chunkedHeader.length, false);
        
        // Write body
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            String chunkSize = Integer.toHexString(bytesRead);

            byte[] chunkComponentHeader = chunkSize.getBytes();
            responseHandler.apply(chunkComponentHeader, chunkComponentHeader.length, false);
            responseHandler.apply(CRLFBytes(), 2, false);

            responseHandler.apply(buffer, bytesRead, false);
            responseHandler.apply(CRLFBytes(), 2, false);

        }

        // End body

        String zero = "0";
        byte[] zeroB = zero.getBytes();
        responseHandler.apply(zeroB, zeroB.length, false);
        // Write finish
        responseHandler.apply(null, 0, true);
         
    }

    private byte[] generateChunkedHeaders(int statusCode) {
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

        // Connection
        if (requestMap.containsKey("Connection") && requestMap.get("Connection").equals("keep-alive")) {
            res += "Connection: keep-alive" + CRLF;
        }

        // Content-Type
        res += "Content-Type: " + (fileType == null ? "text/plain" : fileType) + CRLF;

        // Content-Length
        res += "Transfer-Encoding: chunked" + CRLF + CRLF;
        return res.getBytes();
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
        } else if (chunkEncodedBody) {
            return request.endsWith("0\r\n\r\n");
        } else if (!readingBody
                && i > 3
                && lastFour[(i-1) % 4] == '\n'
                && lastFour[(i-2) % 4] == '\r'
                && lastFour[(i-3) % 4] == '\n'
                && lastFour[(i-4) % 4] == '\r') {
            parseRequest();
            if (requestMap.containsKey("Content-Length")) {
                expectedContentFromBody = Integer.parseInt(requestMap.get("Content-Length"));
                readingBody = true;
            } else if (requestMap.containsKey("Transfer-Encoding") && requestMap.get("Transfer-Encoding").equals("chunked")) {
                chunkEncodedBody = true;
            } else {
                return true;
            }
        }
        return false;
    }

    public boolean keepAlive() {
        return requestMap.get("Connection") != null && requestMap.get("Connection").equals("keep-alive");
    }


    // Make sure request is formatted legitimately
    private void validateRequest() throws ResponseException {
        if (isRequestBroken) {
            throw new ResponseException("Invalid request", 500);
        }
        if (!(requestMap.get("Version").startsWith("HTTP/") && (requestMap.get("Version").endsWith("0.9") || requestMap.get("Version").endsWith("1.0") || requestMap.get("Version").endsWith("1.1")))) {
            throw new ResponseException("Invalid HTTP version: " + requestMap.get("Version"), 400);
        }
        if (!((requestMap.get("Method").equals("GET")) || requestMap.get("Method").equals("POST"))) {
            throw new ResponseException("Invalid method " + requestMap.get("Method"), 405);
        }
    }

    private void checkIfLoad(String path, WorkersSyncData syncData) throws ResponseException{
        if (path.equals("/load")) {
            if (syncData.isServerOverloaded()) {
                throw new ResponseException("Overloaded", 503);
            } else {
                throw new ResponseException("Server can accept connections", 200);
            }
        }
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

    private String getDocumentRoot(int port) throws ResponseException {
        String documentRoot = serverConfig.getRootFrom(requestMap.get("Host"), port);

        if (requestMap.get("Host") == null || requestMap.get("Host").startsWith("localhost")){
            documentRoot = serverConfig.getFirstRoot(port);
        }

        return documentRoot;
    }

    private void authorizeRequest() throws ResponseException{
        // check if htaccess exists
        String htaccessPath = filePath.substring(0, filePath.lastIndexOf("/") + 1) + ".htaccess";
        File htaccessFile = new File(htaccessPath);

        if (htaccessFile.exists()) {
            Map<String, String> htaccessMap = parseHtaccess(htaccessFile);

            // check if auth header exists and is valid
            if (requestMap.get("Authorization") == null) {
                throw new ResponseException("Unauthorized: " + htaccessMap.get("AuthName"), 401, true);
            } else {
                if (!requestMap.get("Authorization").startsWith("Basic ")) {
                    throw new RuntimeException();
                }

                String encodedAuth = requestMap.get("Authorization").substring(requestMap.get("Authorization").indexOf(" ") + 1);
                try {
                    byte[] decodedBytes = Base64.getDecoder().decode(encodedAuth);
                    
                    String decodedString = new String(decodedBytes);

                    String[] parts = decodedString.split(":", 2); // Split into username and password

                    if (parts.length != 2) {
                        throw new RuntimeException();
                    }

                    String username = parts[0];
                    String password = parts[1];

                    String encodedUsername = Base64.getEncoder().encodeToString(username.getBytes());
                    String encodedPassword = Base64.getEncoder().encodeToString(password.getBytes());

                    // check that username and password match
                    if (!encodedUsername.equals(htaccessMap.get("User")) || !encodedPassword.equals(htaccessMap.get("Password"))) {
                        throw new RuntimeException();
                    }

                } catch (Exception e) {
                    throw new ResponseException("Unauthorized: " + htaccessMap.get("AuthName"), 401);
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

    private byte[] processRequest() throws ResponseException{
        byte[] responseBody;

        try {
            if (requestMap.get("Method").equals("GET")) {
                responseBody = processGET();
            } else if (requestMap.get("Method").equals("POST")) {
                responseBody = processPOST();

            } else {
                throw new ResponseException("Invalid method: " + requestMap.get("Method"), 405);
            }
            return generateFullResponse(200, responseBody, false);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private byte[] processGET() throws ResponseException, IOException {
        byte[] response;
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

    private void checkType(String uri) throws ResponseException, IOException {
        String mimeType = Files.probeContentType(Paths.get(uri));
        fileType = mimeType;

        if (mimeType == null) {
            
            throw new ResponseException("Invalid file type: requested file is a folder", 406);
        }

        if (requestMap.get("Accept") != null) {
            String[] types = requestMap.get("Accept").split(",\\s*");

            String mimeStart = mimeType.substring(0, mimeType.indexOf("/"));
            String mimeEnd = mimeType.substring(mimeType.indexOf("/") + 1);

            for (String type : types) {

                String typeStart = type.substring(0, type.indexOf("/"));
                String typeEnd  = type.substring(type.indexOf("/") + 1);
                typeEnd = typeEnd.split(";")[0];
                if ((typeStart.equals(mimeStart) || typeStart.equals("*")) && (typeEnd.equals(mimeEnd) || typeEnd.equals("*"))) {
                    return;
                }
            }

            throw new ResponseException("Invalid file type " + mimeType, 406);
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

    private byte[] getFileOutput(File responseFile) throws IOException{
        byte[] fileContent = Files.readAllBytes(responseFile.toPath());
        return fileContent;
    }

    private byte[] processPOST() throws IOException, ResponseException {
        int port = socketChannel.socket().getLocalPort();
        lastModifiedDate = getCurrentDate();

        String documentRoot = getDocumentRoot(port);

        String cgiPath = requestMap.get("Path");
        String uri = getURI(cgiPath, documentRoot);

        File f = new File(uri);
        if (!f.exists() || f.isDirectory()) {
            throw new ResponseException("Host " + requestMap.get("Host") + " could not be resolved", 404);
        }

        String queryString = requestMap.get("Body");
        String returnContent = runCGIProgram(uri, queryString, socketChannel.getRemoteAddress().toString(), socketChannel.getLocalAddress().toString(), "POST");
        return returnContent.getBytes();
    }

    private String runCGIProgram(String programPath, String queryString, String remotePort, String serverPort, String method) throws IOException {
        String perlInterpreter = "perl";
        ProcessBuilder processBuilder = new ProcessBuilder(perlInterpreter, programPath);
        processBuilder.environment().put("QUERY_STRING", queryString);
        processBuilder.environment().put("REMOTE_*", remotePort);
        processBuilder.environment().put("SERVER_*", serverPort);
        processBuilder.environment().put("REQUEST_METHOD", method);

        processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);

        Process process = processBuilder.start();
        process.getOutputStream().write(queryString.getBytes());
        process.getOutputStream().close();
        
        
        InputStream inputStream = process.getInputStream();

        byte[] buffer = new byte[1024];
        int bytesRead;
        StringBuilder output = new StringBuilder();


        while ((bytesRead = inputStream.read(buffer)) != -1) {
            output.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }
        return output.toString();
    }

    private byte[] generateFullResponse(int statusCode, byte[] responseBody, boolean hasEmptyAuthentication) {
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

        // WWW-Authenticate
        if (hasEmptyAuthentication) {
            String responseBodyStr = new String(responseBody, StandardCharsets.UTF_8);
            res += "WWW-Authenticate: Basic Realm=" + responseBodyStr.substring(1) + CRLF;
        }

        // Connection
        if (requestMap.containsKey("Connection") && requestMap.get("Connection").equals("keep-alive")) {
            res += "Connection: keep-alive" + CRLF;
        }

        // Content-Type
        res += "Content-Type: " + (fileType == null ? "text/plain" : fileType) + CRLF;

        // Content-Length
        res += "Content-Length: " + (responseBody.length + 4) + CRLF + CRLF;

        byte[] responseHeader = res.getBytes();

        byte[] combined = new byte[responseHeader.length + responseBody.length];

        for (int i = 0; i < combined.length; ++i)
        {
            combined[i] = i < responseHeader.length ? responseHeader[i] : responseBody[i - responseHeader.length];
        }

        return combined;
    }
}
