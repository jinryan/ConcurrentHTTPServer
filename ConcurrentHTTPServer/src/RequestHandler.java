public interface RequestHandler {

    public boolean requestCompleted();
    public void readCharsToRequest(char c);
    public void parseRequest();
    public String handleRequest(WorkersSyncData syncData);
    public void handleRequest(HTTPResponseHandler responseHandler);
    public boolean keepAlive();
}