import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

public class TspBlackboard {

    public static final String REQUEST_TOPIC = "csc364/work/request";
    public static final String RESPONSE_TOPIC = "csc364/work/response";
    public static final String ASSIGN_TOPIC = "csc364/work/assign";
    public static final String SHUTDOWN_TOPIC = "csc364/work/shutdown";
    public static final String BROKER = "tcp://broker.hivemq.com:1883";

    private final ConcurrentHashMap<String, List<City>> citiesByUrl = new ConcurrentHashMap<>();
    private String currentMapUrl;

    private final LinkedBlockingQueue<TspJob> queue = new LinkedBlockingQueue<>();

    private List<Integer> bestTour;
    private double bestTourLength = Double.MAX_VALUE;

    public void putJob(TspJob job) throws InterruptedException {
        queue.put(job);
    }

    public TspJob pollJob() throws InterruptedException {
        return queue.poll();
    }

    public TspJob takeJob() throws InterruptedException {
        return queue.take();
    }

    public int getCurrentMapSize() {
        return citiesByUrl.get(currentMapUrl).size();
    }

    public String getCurrentMapUrl() {
        return currentMapUrl;
    }

    public void setCurrentMapUrl(String mapUrl) {
        this.currentMapUrl = mapUrl;
    }

    /*
    City URLs:
    "https://raw.githubusercontent.com/BrennanAndruss/DistributedTSP/refs/heads/master/src/main/resources/lu980.tsp"
    "https://raw.githubusercontent.com/BrennanAndruss/DistributedTSP/refs/heads/master/src/main/resources/nu3496.tsp"
    "https://raw.githubusercontent.com/BrennanAndruss/DistributedTSP/refs/heads/master/src/main/resources/ar9152.tsp"
    "https://raw.githubusercontent.com/BrennanAndruss/DistributedTSP/refs/heads/master/src/main/resources/bm33708.tsp"
    "https://raw.githubusercontent.com/BrennanAndruss/DistributedTSP/refs/heads/master/src/main/resources/usa115475.tsp"
    "https://raw.githubusercontent.com/BrennanAndruss/DistributedTSP/refs/heads/master/src/main/resources/world.tsp"
     */
    public List<City> getCities(String urlString) {
        // Load non-cached cities from raw GitHub URL
        if (!citiesByUrl.containsKey(urlString)) {
            try {
                citiesByUrl.put(urlString, TspParser.load(new URL(urlString)));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        return citiesByUrl.get(urlString);
    }

    public List<Integer> getBestTour() {
        return bestTour;
    }

    public double getBestTourLength() {
        return bestTourLength;
    }

    public void submitJobResult(List<Integer> tour) {
        double len = NearestNeighborSolver.length(citiesByUrl.get(currentMapUrl), tour);
        if (len < bestTourLength) {
            System.out.println("New best tour: " + len);
            bestTour = tour;
            bestTourLength = len;
            // potential to-do: invoke mapPanel.setTour(tour)
        }
    }
}
