package Service.GTFS_RT.Index;

public final class DelayEstimate {
    public final Integer delaySec;     // può essere null
    public final double confidence;    // 0..1

    public DelayEstimate(Integer delaySec, double confidence) {
        this.delaySec = delaySec;
        this.confidence = clamp01(confidence);
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}