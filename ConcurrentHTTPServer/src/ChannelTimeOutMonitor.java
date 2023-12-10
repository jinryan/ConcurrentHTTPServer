import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.TimerTask;

public class ChannelTimeOutMonitor extends TimerTask {
    private final ArrayList<Selector> selectors;

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
                    // if the last time this selector read something was more than 3 seconds ago, close the channel
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
