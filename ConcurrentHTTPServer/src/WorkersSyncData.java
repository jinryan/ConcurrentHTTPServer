public class WorkersSyncData {
    private final int numWorkers;
    private int numConnections;
    private boolean serverIsRunning = true;
    private final int maxAllowedConnections;

    public WorkersSyncData(int numWorkers, int numConnections, int maxAllowedConnections) {
        this.numConnections = numConnections;
        this.numWorkers = numWorkers;
        this.maxAllowedConnections = maxAllowedConnections;
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

    public boolean isServerOverloaded() {
        System.out.println(getNumConnections());
        return getNumConnections() > maxAllowedConnections;
    }

}
