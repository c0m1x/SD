//nome auto explicativo
import java.io.IOException;
import java.util.*;


public class clientUI {
    private boolean authenticated = false;
    //private TimeSeriesClient client;
    private Scanner scanner;

    public clientUI(String host, int port) throws IOException {
        //this.client = new TimeSeriesClient(host, port);
        this.scanner = new Scanner(System.in);
        //client.connect();
    }
}
