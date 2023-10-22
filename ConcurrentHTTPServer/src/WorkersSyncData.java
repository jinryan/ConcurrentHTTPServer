public class WorkersSyncData {
    private final int numWorkers;
    private int numConnections;
    private boolean serverIsRunning = true;

    public WorkersSyncData(int numWorkers, int numConnections) {
        this.numConnections = numConnections;
        this.numWorkers = numWorkers;
    }

    public boolean getServerIsRunning() {
        return serverIsRunning;
    }

    public void setServerRun(boolean run) {
        this.serverIsRunning = run;
    }

    public void addConnection() {
        numConnections++;
    }

    public void dropConnection() {
        numConnections--;
    }

    public int getNumWorkers() {
        return numWorkers;
    }

    public int getNumConnections() {
        return numConnections;
    }
}
