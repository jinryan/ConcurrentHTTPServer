import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.TimerTask;

public class ChannelTimeOutMonitor extends TimerTask {
    private final ArrayList<Selector> selectors;
    private WorkersSyncData syncData;
    private int timeoutCheck = 1;

    public ChannelTimeOutMonitor(ArrayList<Selector> selectors, WorkersSyncData syncData) {
        this.syncData = syncData;
        this.selectors = selectors;
    }

    @Override
    public void run() {
        // System.out.println("\nCheck for timeout " + timeoutCheck);
        timeoutCheck++;
        for (Selector selector : selectors) {
            for (SelectionKey key : selector.keys()) {
                if (key.attachment() != null) {
                    ConnectionControlBlock ccb = (ConnectionControlBlock) key.attachment();
                    long curr = System.currentTimeMillis();
                    // if the last time this selector read something was more than 3 seconds ago, close the channel
                    if (ccb.getConnectionState() == ConnectionState.READING && (double) (curr - ccb.getLastReadTime()) / 1000 > 10.0) {
                        // System.out.println("Connection timed out");
                        // syncData.dropConnection();
                        ccb.setConnectionState(ConnectionState.TRANSMITTED);
                        // try {
                        //     syncData.dropConnection();
                        //     ccb.setConnectionState(ConnectionState.WRITTEN);
                        //     // key.channel().close();
                        //     // key.cancel();
                        //     // selector.selectedKeys().remove(key);
                        // } catch (IOException e) {
                        //     throw new RuntimeException(e);
                        // }
                    }
                }

            }
        }
    }
}
