import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;

// Press Shift twice to open the Search Everywhere dialog and type `show whitespaces`,
// then press Enter. You can now see whitespace characters in your code.
public class Main {
    public static void main(String[] args) {
        try {
            String path = "/Users/ryanjin/Desktop/Yale/Fall 23/CPSC 434 Topics in Networked Systems/ConcurrentHTTPServer/ConcurrentHTTPServer/../www2/script.cgi";
            String path2 = path.replace(" ", "%20");
            new URI("file://" + path2);
            File f = new File(path);
            System.out.println(f.exists());
        } catch (URISyntaxException e) {
            System.out.println("Unsuccessful");
            e.printStackTrace();
        }
    }
}