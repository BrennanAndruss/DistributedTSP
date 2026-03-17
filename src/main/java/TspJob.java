public class TspJob {
    public String mapUrl;
    public int id;
    public int startIndex;

    public TspJob() {}

    public TspJob(int id, String mapUrl, int startIndex) {
        this.id = id;
        this.mapUrl = mapUrl;
        this.startIndex = startIndex;
    }

    public static final TspJob STOP = new TspJob(-1, null, -1);

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TspJob)) return false;

        TspJob other = (TspJob) obj;
        return id == other.id && startIndex == other.startIndex;
    }
}
