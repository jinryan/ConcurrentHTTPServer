public interface RequestHandler {

    public boolean requestCompleted();
    public void readCharsToRequest(char c);
    public void parseRequest();
    public String handleRequest();
    public boolean keepAlive();
}