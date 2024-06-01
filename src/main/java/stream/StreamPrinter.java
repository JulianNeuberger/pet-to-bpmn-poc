package stream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class StreamPrinter extends Thread {
    private final String prefix;
    private final InputStream stream;

    public StreamPrinter(InputStream stream, String prefix) {
        this.stream = stream;
        this.prefix = prefix;
    }

    public void run() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(this.stream));
            System.out.format("Reading from stream with prefix %s ...\n", this.prefix);
            String line;
            while((line = reader.readLine()) != null) {
                System.out.format("%s: %s\n", this.prefix, line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
