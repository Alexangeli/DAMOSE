package Service.GTFS_RT.Index;

/**
 * Rappresenta una stima del ritardo di una corsa insieme alla sua affidabilità.
 * Utile per combinare informazioni di ritardo provenienti da feed diversi,
 * indicando non solo il valore stimato del ritardo ma anche quanto questa stima sia affidabile.
 */
public final class DelayEstimate {

    /** Ritardo stimato in secondi, null se non disponibile */
    public final Integer delaySec;

    /** Confidenza della stima, valore compreso tra 0 (poco affidabile) e 1 (molto affidabile) */
    public final double confidence;

    /**
     * Costruisce un oggetto DelayEstimate.
     * La confidenza viene automaticamente normalizzata tra 0 e 1.
     *
     * @param delaySec ritardo stimato in secondi, null se non disponibile
     * @param confidence affidabilità della stima, valore nominale 0..1
     */
    public DelayEstimate(Integer delaySec, double confidence) {
        this.delaySec = delaySec;
        this.confidence = clamp01(confidence);
    }

    /**
     * Normalizza un valore di confidenza tra 0 e 1.
     *
     * @param v valore originale della confidenza
     * @return valore compreso tra 0 e 1
     */
    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}