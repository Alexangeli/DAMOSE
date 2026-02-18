package Model.Map;

import org.jxmapviewer.viewer.GeoPosition;

import java.util.ArrayList;
import java.util.List;

/**
 * Model della mappa.
 *
 * Contiene tutto lo stato logico necessario alla visualizzazione:
 * - centro attuale
 * - livello di zoom
 * - marker presenti
 *
 * Non contiene logica grafica: la View si limita a leggere
 * questi dati e a disegnarli.
 */
public class MapModel {

    /**
     * Livello minimo e massimo di zoom consentito.
     */
    int MIN_ZOOM = 2;
    int MAX_ZOOM = 8;

    /**
     * Zoom iniziale della mappa.
     */
    final int DEFAULT_ZOOM = 3;

    /**
     * Posizione centrale attuale della mappa.
     */
    private GeoPosition center;

    /**
     * Livello di zoom corrente.
     * È double per permettere variazioni più fluide.
     */
    private double zoom;

    /**
     * Lista dei marker da visualizzare.
     */
    private final List<GeoPosition> markers = new ArrayList<>();

    /**
     * Limiti geografici entro cui è consentita la navigazione.
     * In questo caso l’area di Roma.
     */
    private final double minLat = 41.75;
    private final double maxLat = 42.05;
    private final double minLon = 12.30;
    private final double maxLon = 12.70;

    /**
     * Inizializza la mappa centrata su Roma
     * con zoom di default.
     */
    public MapModel() {
        this.center = new GeoPosition(41.919565, 12.546213);
        this.zoom = DEFAULT_ZOOM;
    }

    /**
     * Limita una posizione ai confini geografici consentiti.
     *
     * Serve per evitare che l’utente possa spostarsi
     * fuori dall’area di interesse.
     */
    public GeoPosition clampPosition(GeoPosition pos) {
        double lat = Math.max(minLat, Math.min(maxLat, pos.getLatitude()));
        double lon = Math.max(minLon, Math.min(maxLon, pos.getLongitude()));
        return new GeoPosition(lat, lon);
    }

    /**
     * Limita il valore di zoom tra minimo e massimo.
     */
    public double clampZoom(double z) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
    }

    public GeoPosition getCenter() {
        return center;
    }

    /**
     * Imposta il centro della mappa,
     * applicando automaticamente il vincolo geografico.
     */
    public void setCenter(GeoPosition center) {
        this.center = clampPosition(center);
    }

    public double getZoom() {
        return zoom;
    }

    /**
     * Imposta il livello di zoom,
     * rispettando i limiti definiti.
     */
    public void setZoom(double zoom) {
        this.zoom = clampZoom(zoom);
    }

    /**
     * Restituisce lo zoom arrotondato a intero.
     *
     * Metodo di supporto per JXMapViewer,
     * che richiede un valore int.
     */
    public int getZoomInt() {
        return (int) Math.round(zoom);
    }

    public List<GeoPosition> getMarkers() {
        return markers;
    }

    /**
     * Aggiunge un marker alla mappa.
     */
    public void addMarker(GeoPosition pos) {
        markers.add(pos);
    }
}
