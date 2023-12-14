package ConfigParser;

import java.util.ArrayList;
import java.util.HashMap;

public class ServerConfigObject {

    private final HashMap<Integer, HashMap<String, String>> mapping;
    private final ArrayList<Integer> ports;
    private int nSelectLoops = 8; // Default connection each worker manages if config not specified

    public ServerConfigObject() {
        this.mapping = new HashMap<Integer, HashMap<String, String>>();
        this.ports = new ArrayList<Integer>();
    }

    public void setnSelectLoops(int nSelectLoop) {
        this.nSelectLoops = nSelectLoop;
    }

    public int getnSelectLoops() {
        return this.nSelectLoops;
    }

    public void addPort(int port) {
        this.ports.add(port);
    }

    public ArrayList<Integer> getPorts() {
        return this.ports;
    }

    public void addMapping(String serverName, String docRoot, int port) {
        if (!this.mapping.containsKey(port)) {
            HashMap<String, String> newPort = new HashMap<String, String>();
            this.mapping.put(port, newPort);
        }

        (this.mapping.get(port)).put(serverName, docRoot);
    //    System.out.println(serverName + ":" + port + " = " + docRoot);
    }

    public String getRootFrom(String serverName, int port) {
        HashMap<String, String> nameToRoot = mapping.get(port);
        if (nameToRoot == null) {
            return null;
        }
        return nameToRoot.get(serverName);

    }

    public String getFirstRoot(int port) {
        HashMap<String, String> nameToRoot = mapping.get(port);
        if (nameToRoot == null) {
            return null;
        }
        return nameToRoot.get("First");
    }
}
