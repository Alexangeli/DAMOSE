package Model.GTFS_RT;

import Model.GTFS_RT.Enums.AlertCause;
import Model.GTFS_RT.Enums.AlertEffect;
import Model.GTFS_RT.Enums.AlertSeverityLevel;

import java.time.Instant;
import java.util.List;

/**
 * Rappresenta un alert proveniente dal feed GTFS Realtime.
 *
 * Un alert descrive una modifica o un disservizio nel sistema
 * di trasporto (es. sciopero, deviazione, ritardi significativi).
 *
 * Questa classe contiene sia le informazioni di classificazione
 * (causa, effetto, gravità), sia il periodo di validità,
 * sia le entità coinvolte (linee, fermate, ecc.).
 *
 * La classe è immutabile: tutte le liste vengono copiate
 * in modo difensivo nel costruttore.
 */
public class AlertInfo {

    /**
     * Identificativo univoco dell’alert.
     */
    public final String id;

    /**
     * Causa dell’alert (es. sciopero, incidente, maltempo).
     */
    public final AlertCause cause;

    /**
     * Effetto dell’alert sul servizio (es. servizio sospeso, ritardi).
     */
    public final AlertEffect effect;

    /**
     * Livello di gravità dell’alert.
     */
    public final AlertSeverityLevel severityLevel;

    /**
     * Inizio del periodo di validità in epoch seconds.
     * Può essere null se non specificato.
     */
    public final Long start;

    /**
     * Fine del periodo di validità in epoch seconds.
     * Può essere null se non specificato.
     */
    public final Long end;

    /**
     * Titoli brevi dell’alert (possono essere più di uno).
     */
    public final List<String> headers;

    /**
     * Descrizioni dettagliate dell’alert.
     */
    public final List<String> descriptions;

    /**
     * Entità coinvolte dall’alert (linee, fermate, ecc.).
     */
    public final List<InformedEntityInfo> informedEntities;

    /**
     * Costruisce un oggetto AlertInfo a partire dai dati
     * estratti dal feed realtime.
     *
     * Le liste vengono copiate per evitare modifiche esterne.
     */
    public AlertInfo(
            String id,
            AlertCause cause,
            AlertEffect effect,
            AlertSeverityLevel severityLevel,
            Long start,
            Long end,
            List<String> headers,
            List<String> descriptions,
            List<InformedEntityInfo> informedEntities
    ) {
        this.id = id;
        this.cause = cause;
        this.effect = effect;
        this.severityLevel = severityLevel;
        this.start = start;
        this.end = end;

        // Copia difensiva per garantire immutabilità
        this.headers = (headers == null) ? List.of() : List.copyOf(headers);
        this.descriptions = (descriptions == null) ? List.of() : List.copyOf(descriptions);
        this.informedEntities = (informedEntities == null) ? List.of() : List.copyOf(informedEntities);
    }

    /**
     * Indica se l’alert è attivo nel momento corrente.
     *
     * @return true se l’alert è attualmente valido
     */
    public boolean isActiveNow() {
        long now = Instant.now().getEpochSecond();
        return isActiveAt(now);
    }

    /**
     * Verifica se l’alert è attivo in un determinato istante.
     *
     * @param epochSeconds istante da verificare (in epoch seconds)
     * @return true se l’alert è attivo in quel momento
     */
    public boolean isActiveAt(long epochSeconds) {

        // Se non sono specificati intervalli temporali,
        // consideriamo l’alert come attivo.
        if (start == null && end == null) return true;

        // Se l’istante è precedente all’inizio
        if (start != null && epochSeconds < start) return false;

        // Se l’istante è successivo alla fine
        if (end != null && epochSeconds > end) return false;

        return true;
    }
}
