import java.util.List;

public class Producer implements Runnable {

    private final TspBlackboard blackboard;

    public Producer(TspBlackboard blackboard) {
        this.blackboard = blackboard;
    }

    @Override
    public void run() {
        String mapUrl = blackboard.getCurrentMapUrl();
        List<City> cities = blackboard.getCities(mapUrl);
        if (cities == null) {
            System.out.println("No cities found");
            return;
        }

        int numCities = cities.size();
        try {
            // Generate a job for each start index
            for (int i = 0; i < numCities; i++) {
                TspJob job = new TspJob(i, mapUrl, i);
                blackboard.putJob(job);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
