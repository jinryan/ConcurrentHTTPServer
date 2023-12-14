public interface RequestHandler {

    public boolean requestCompleted();
    public void readCharsToRequest(char c);
    public void parseRequest();
    public void handleRequest(HTTPResponseHandler responseHandler, WorkersSyncData syncData);
    public boolean keepAlive();
}