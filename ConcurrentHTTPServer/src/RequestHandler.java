import java.util.ArrayList;

public interface RequestHandler {

    public boolean requestCompleted();
    public void readCharsToRequest(char c);
    public void parseRequest();
    public String getResponse();
    public void getResponseContent(HTTPResponseHandler responseHandler);
    public boolean keepAlive();
}