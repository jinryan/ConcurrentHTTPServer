import ConfigParser.ApacheConfigParser;
import ConfigParser.ServerConfigObject;

public class Main {
    public static void main(String[] args) {

        ServerConfigObject serverConfig = null;
        String configPath;

        if (args.length == 2 && args[0].equals("-config")) {
            configPath = args[1];
        } else {
            configPath = "src/config.conf";
        }
        serverConfig = ApacheConfigParser.getServerConfigFrom(configPath);
        HTTPServer server = new HTTPServer(8, 8, serverConfig);
        server.start();
    }
}