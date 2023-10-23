import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {
    public static void main(String[] args) {
        try {
            // Path to the Perl interpreter and the Perl script
            String perlInterpreter = "perl"; // Change to the path of your Perl interpreter if needed
            String perlScript = "src/code.cgi"; // Change to the correct path of your Perl script
            Map<String, String> environment = new HashMap<>();
            environment.put("company", "Apple");
            // Create the ProcessBuilder
            ProcessBuilder processBuilder = new ProcessBuilder(perlInterpreter, perlScript);

            processBuilder.environment().put("company", "Apple");

            // Start the process
            Process process = processBuilder.start();

            // Read the output of the Perl script
            InputStream inputStream = process.getInputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            StringBuilder output = new StringBuilder();

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                output.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}