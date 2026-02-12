package Service.GTFS_RT.Alerts;

import java.util.Collections;
import java.util.List;

public class AlertInfo {
    public final String id;                // entity id (se presente)
    public final String cause;             // enum name se presente (UNKNOWN_CAUSE, STRIKE, etc.)
    public final String effect;            // enum name se presente (DETOUR, NO_SERVICE, etc.)
    public final Long startEpochSec;       // può essere null
    public final Long endEpochSec;         // può essere null
    public final List<String> headerText;  // testi (multilingua) -> qui lista semplice
    public final List<String> descriptionText;

    public AlertInfo(String id,
                     String cause,
                     String effect,
                     Long startEpochSec,
                     Long endEpochSec,
                     List<String> headerText,
                     List<String> descriptionText) {
        this.id = id;
        this.cause = cause;
        this.effect = effect;
        this.startEpochSec = startEpochSec;
        this.endEpochSec = endEpochSec;
        this.headerText = headerText == null ? Collections.emptyList() : headerText;
        this.descriptionText = descriptionText == null ? Collections.emptyList() : descriptionText;
    }
}
