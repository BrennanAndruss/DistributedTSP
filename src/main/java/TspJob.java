public class TspJob {
    public String mapUrl;
    public int jobId;
    public int startIndex;

    public TspJob() {}

    public TspJob(int jobId, String mapUrl, int startIndex) {
        this.jobId = jobId;
        this.mapUrl = mapUrl;
        this.startIndex = startIndex;
    }

    public static final TspJob STOP = new TspJob(-1, null, -1);
}
