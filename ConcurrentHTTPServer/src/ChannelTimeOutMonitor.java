import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.TimerTask;

public class ChannelTimeOutMonitor extends TimerTask {
    private ArrayList<Selector> selectors;

    public ChannelTimeOutMonitor(ArrayList<Selector> selectors) {
        this.selectors = selectors;
    }

    @Override
    public void run() {
        for (Selector selector : selectors) {
            for (SelectionKey key : selector.keys()) {
                if (key.attachment() != null) {
                    ConnectionControlBlock ccb = (ConnectionControlBlock) key.attachment();
                    long curr = System.currentTimeMillis();
                    if (ccb.getConnectionState() == ConnectionState.READING && (double) (curr - ccb.getLastReadTime()) / 1000 > 3.0) {
                        try {
                            key.channel().close();
                            key.cancel();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

            }
        }
    }




}
