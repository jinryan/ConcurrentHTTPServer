import com.sun.net.httpserver.Request;

import java.nio.ByteBuffer;

public class ConnectionControlBlock {

    final int defaultBufferSize = 1024;
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
    private boolean keepConnectionAlive;

    private RequestHandler requestHandler = null;
    public ConnectionControlBlock() {
        this.readBuffer = ByteBuffer.allocate(defaultBufferSize);
        this.writeBuffer = ByteBuffer.allocate(defaultBufferSize);
        this.connectionState = ConnectionState.ACCEPT;
        this.request = new StringBuffer(defaultBufferSize);
        this.keepConnectionAlive = false;
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

    public void setKeepConnectionAlive(boolean keepConnectionAlive) {
        this.keepConnectionAlive = keepConnectionAlive;
    }

    public boolean isKeepConnectionAlive() {
        return keepConnectionAlive;
    }

    public void updateConnectionState() {

    }

    public RequestHandler getRequestHandler() {
        return requestHandler;
    }

    public void setRequestHandler(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }
}
