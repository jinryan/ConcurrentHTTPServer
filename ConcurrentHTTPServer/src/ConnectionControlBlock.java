import com.sun.net.httpserver.Request;

import java.nio.ByteBuffer;

public class ConnectionControlBlock {

    final int defaultBufferSize = 1024;
    private ByteBuffer readBuffer;
    private ByteBuffer writeBuffer;
    private StringBuffer request;
    private ConnectionState connectionState;
    public ConnectionControlBlock() {
        this.readBuffer = ByteBuffer.allocate(defaultBufferSize);
        this.writeBuffer = ByteBuffer.allocate(defaultBufferSize);
        this.connectionState = ConnectionState.ACCEPT;
        this.request = new StringBuffer(defaultBufferSize);
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

    public void updateConnectionState() {

    }
}
