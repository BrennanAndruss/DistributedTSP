import java.util.List;

public class SolvedTspJob {
    public TspJob job;
    public List<Integer> tour;
    public int workerId;

    public SolvedTspJob() {}

    public SolvedTspJob(TspJob job, List<Integer> tour, int workerId) {
        this.job = job;
        this.tour = tour;
        this.workerId = workerId;
    }
}
