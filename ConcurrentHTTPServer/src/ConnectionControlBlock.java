import java.nio.ByteBuffer;

public class ConnectionControlBlock {

    final int defaultBufferSize = 8192;
    private ByteBuffer readBuffer;
    private ByteBuffer writeBuffer;
    private StringBuffer request;
    private ConnectionState connectionState;

    public long getLastReadTime() {
        return lastReadTime;
    }

    public void setLastReadTime(long lastReadTime) {
        this.lastReadTime = lastReadTime;
    }

    private long lastReadTime;
    private RequestHandler requestHandler = null;
    
    public ConnectionControlBlock() {
        this.readBuffer = ByteBuffer.allocate(defaultBufferSize);
        this.writeBuffer = ByteBuffer.allocate(defaultBufferSize);
        this.connectionState = ConnectionState.ACCEPT;
        this.request = new StringBuffer(defaultBufferSize);
        this.lastReadTime = System.currentTimeMillis();
    }

    public ConnectionControlBlock(int bufferSize) {
        this.readBuffer = ByteBuffer.allocate(bufferSize);
        this.writeBuffer = ByteBuffer.allocate(bufferSize);
        this.connectionState = ConnectionState.ACCEPT;
    }

    public ByteBuffer getReadBuffer() {
        return readBuffer;
    }

    public ByteBuffer getWriteBuffer() {
        return writeBuffer;
    }

    public void setConnectionState(ConnectionState state) {
        this.connectionState = state;
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public StringBuffer getRequest() {
        return request;
    }

    public boolean isKeepConnectionAlive() {
        return this.requestHandler.keepAlive();
    }

    public void updateConnectionState() {

    }

    public RequestHandler getRequestHandler() {
        return requestHandler;
    }

    public void setRequestHandler(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    public void resetState() {
        this.readBuffer.clear();
        this.writeBuffer.clear();
        this.request = new StringBuffer(defaultBufferSize);
    }
}
