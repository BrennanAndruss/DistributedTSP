import java.util.List;

public class LocalWorker implements Runnable {

    private final int id;

    private final TspBlackboard blackboard;

    private int jobsSolved = 0;

    public LocalWorker(int id, TspBlackboard blackboard) {
        this.id = id;
        this.blackboard = blackboard;
    }

    @Override
    public void run() {
        try {
            while (true) {
                // Take a job
                TspJob tspJob = blackboard.takeJob();
                if (tspJob.equals(TspJob.STOP)) {
                    System.out.println("[LocalWorker " + id + "] Stopped.");
                    System.out.println("[LocalWorker " + id + "] " + jobsSolved + " jobs solved.");
                    break;
                }

                // Get the list of cities
                List<City> cities = blackboard.getCities(tspJob.mapUrl);

                // Solve the job
                List<Integer> tour = NearestNeighborSolver.solve(cities, tspJob.startIndex);
                System.out.println("[LocalWorker" + id + "] Job " + tspJob.id + " solved.");
                jobsSolved++;

                // Submit job result
                blackboard.submitJobResult(tour);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
