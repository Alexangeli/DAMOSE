package View.Map;

import Model.Points.ClusterModel;
import View.Waypointers.Painter.*;
import View.Waypointers.Waypoint.StopWaypoint;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;
import Model.GTFS_RT.VehicleInfo;

import java.util.List;
import javax.swing.*;
import java.awt.*;
import java.util.Set;

/**
 * Vista della mappa basata su JXMapViewer.
 *
 * Contiene il componente grafico della mappa e fornisce un metodo
 * per aggiornare la visualizzazione con:
 * - centro e zoom
 * - waypoint delle fermate
 * - cluster di fermate
 * - shape di una linea
 * - veicoli in movimento
 * - evidenziazione di una fermata specifica
 *
 * La classe non gestisce logica di business, ma solo la rappresentazione.
 */
public class MapView extends JPanel {

    /**
     * Componente principale della mappa.
     */
    private final JXMapViewer mapViewer;

    /**
     * Costruisce la vista della mappa e inizializza il TileFactory.
     */
    public MapView() {
        setLayout(new BorderLayout());

        mapViewer = new JXMapViewer();
        mapViewer.setTileFactory(CustomTileFactory.create());

        add(mapViewer, BorderLayout.CENTER);
    }

    /**
     * Aggiorna la mappa con nuovi dati.
     *
     * @param center posizione centrale della mappa
     * @param zoom livello di zoom
     * @param stops insieme dei waypoint delle fermate
     * @param clusters cluster di fermate per il rendering aggregato
     * @param shapePainter painter della shape da disegnare
     * @param highlightedPosition posizione della fermata evidenziata
     * @param vehicles lista dei veicoli da visualizzare
     */
    public void updateView(GeoPosition center,
                           int zoom,
                           Set<StopWaypoint> stops,
                           Set<ClusterModel> clusters,
                           ShapePainter shapePainter,
                           GeoPosition highlightedPosition,
                           List<VehicleInfo> vehicles) {

        mapViewer.setAddressLocation(center);
        mapViewer.setCenterPosition(center);
        mapViewer.setZoom(zoom);

        StopPainter stopPainter = new StopPainter(stops, highlightedPosition);
        ClusterPainter clusterPainter = new ClusterPainter(clusters);
        VehiclePainter vehiclePainter = new VehiclePainter(vehicles);

        MapOverlay overlay = new MapOverlay(stopPainter, clusterPainter, shapePainter, vehiclePainter);
        mapViewer.setOverlayPainter(overlay);

        mapViewer.repaint();
    }

    /**
     * Restituisce il componente JXMapViewer sottostante.
     *
     * @return il viewer della mappa
     */
    public JXMapViewer getMapViewer() {
        return mapViewer;
    }
}
