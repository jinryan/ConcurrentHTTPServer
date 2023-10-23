package ConfigParser;

import java.util.HashMap;

public class ServerConfigObject {

    private final HashMap<Integer, HashMap<String, String>> mapping;

    public ServerConfigObject() {
        this.mapping = new HashMap<Integer, HashMap<String, String>>();
    }

    public void addMapping(String serverName, String docRoot, int port) {
        if (!this.mapping.containsKey(port)) {
            HashMap<String, String> newPort = new HashMap<String, String>();
            this.mapping.put(port, newPort);
        }

        (this.mapping.get(port)).put(serverName, docRoot);
        System.out.println(serverName + ":" + port + " = " + docRoot);
    }

    public String getRootFrom(String serverName, int port) {
        HashMap<String, String> nameToRoot = mapping.get(port);
        if (nameToRoot == null) {
            return null;
        }
        return nameToRoot.get(serverName);

    }
}
