import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class CGIProcessBuilder {
    public static String buildProcess(String programPath, String queryString, int remotePort, int serverPort, String method) throws IOException {
        String perlInterpreter = "perl";
        ProcessBuilder processBuilder = new ProcessBuilder(perlInterpreter, programPath);
        processBuilder.environment().put("QUERY_STRING", queryString);
        processBuilder.environment().put("REMOTE_*", String.valueOf(remotePort));
        processBuilder.environment().put("SERVER_*", String.valueOf(serverPort));
        processBuilder.environment().put("REQUEST_METHOD", method);

        Process process = processBuilder.start();

        InputStream inputStream = process.getInputStream();

        byte[] buffer = new byte[1024];
        int bytesRead;
        StringBuilder output = new StringBuilder();

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            output.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }

        return output.toString();

    }
}
