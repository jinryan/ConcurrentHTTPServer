import ConfigParser.ApacheConfigParser;
import ConfigParser.ServerConfigObject;

public class Main {
    public static void main(String[] args) {

        ServerConfigObject serverConfig = null;

        //
        if (true || (args.length == 2 && args[0].equals("-config"))) {
            // Expecting
//            String configPath = args[1];
            String configPath = "config.conf";
            serverConfig = ApacheConfigParser.getServerConfigFrom(configPath);
        }
        HTTPServer server = new HTTPServer(8, 8, serverConfig);
        server.start();
    }
}