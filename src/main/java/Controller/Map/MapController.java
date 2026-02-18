package Controller.Map;

import Model.GTFS_RT.VehicleInfo;
import Model.Map.MapModel;
import Model.Net.ConnectionState;
import Model.Net.ConnectionStatusProvider;
import Model.Parsing.Static.ShapesModel;
import Model.Parsing.Static.TripsModel;
import Model.Points.ClusterModel;
import Model.Points.StopModel;
import Service.GTFS_RT.Fetcher.Vehicle.VehiclePositionsService;
import Service.Parsing.ShapesService;
import Service.Parsing.TripsService;
import Service.Points.ClusterService;
import Service.Points.StopService;
import View.Map.MapView;
import View.Waypointers.Painter.ShapePainter;
import View.Waypointers.Waypoint.StopWaypoint;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static Service.Points.StopService.getAllStops;

/**
 * Controller della mappa basato su JXMapViewer.
 *
 * Responsabilità:
 * - Caricare le fermate (da CSV GTFS statico) e trasformarle in {@link StopWaypoint}.
 * - Gestire interazioni utente: drag, zoom smooth, click su mappa/marker.
 * - Applicare clustering delle fermate in base allo zoom, per evitare sovrapposizioni.
 * - Gestire l’evidenziazione di una linea (shapes) e di una fermata selezionata.
 * - Mostrare (opzionalmente) i veicoli realtime filtrati per route/direction.
 *
 * Note di design:
 * - Le fermate "di base" sono caricate una volta e mantenute in {@link #waypoints}.
 * - In modalità zoom alto (più “lontano”), le fermate vengono raggruppate in cluster tramite {@link ClusterService}.
 * - Per la UX lo zoom è “smooth”: la rotellina cambia {@link #targetZoom} e un timer applica step graduali.
 * - La fermata evidenziata resta visibile anche quando la mappa è in modalità cluster.
 *
 * Aspetti UI:
 * - Questo controller aggiorna direttamente {@link MapView} tramite {@link #refreshView()}.
 * - In caso di OFFLINE, la mappa può passare a tile “offline only” e la layer realtime (veicoli) viene disattivata.
 *
 * Creatori: Simone Bonuso, Andrea Brandolini, Alessandro Angeli
 */
public class MapController {

    private final MapModel model;
    private final MapView view;
    private final String stopsCsvPath;

    /** Waypoint “base” delle fermate caricate dal CSV. */
    private final Set<StopWaypoint> waypoints = new HashSet<>();

    /** Cluster calcolati dinamicamente quando lo zoom è alto. */
    private Set<ClusterModel> clusters = new HashSet<>();

    /** Zoom target usato per lo smooth-zoom (valore continuo). */
    private double targetZoom;

    /** Timer che applica gradualmente lo zoom (effetto smooth). */
    private final Timer zoomTimer;

    /** Punto precedente durante il drag (per calcolare dx/dy). */
    private Point dragPrev = null;

    /** Painter delle shape (linee) evidenziate. */
    private final ShapePainter shapePainter;

    // Percorsi GTFS statico (nel progetto sono hardcoded perché legati alle risorse locali).
    final String shapesPath = "src/main/resources/rome_static_gtfs/shapes.csv";
    final String routesPath = "src/main/resources/rome_static_gtfs/routes.csv";
    final String tripsPath = "src/main/resources/rome_static_gtfs/trips.csv";

    /** Posizione (lat/lon) della fermata evidenziata, usata dalla View per disegnare un marker speciale. */
    private GeoPosition highlightedPosition = null;

    /** Service realtime da cui leggere i vehicle positions (già cacheati internamente). */
    private final VehiclePositionsService vehiclePositionsService;

    /** Veicoli attualmente visibili in mappa (filtrati su route/direction). */
    private List<VehicleInfo> visibleVehicles = List.of();

    /** Route selezionata per la layer veicoli (null = layer spenta). */
    private volatile String selectedRouteId = null;

    /** Direzione selezionata per la layer veicoli (null/-1 = tutte). */
    private volatile Integer selectedDirectionId = null;

    /** Timer che aggiorna periodicamente la layer veicoli (se attiva). */
    private final Timer vehiclesRefreshTimer;

    /** Waypoint speciale della fermata evidenziata (manteniamo un marker singolo sempre visibile). */
    private StopWaypoint highlightedStopWaypoint = null;

    /**
     * Crea il controller mappa e inizializza:
     * - timer di smooth-zoom,
     * - timer refresh veicoli (se attivo),
     * - caricamento fermate,
     * - binding interazioni,
     * - prima renderizzazione.
     *
     * @param model modello mappa (center/zoom/markers)
     * @param view vista mappa (contiene JXMapViewer e layer)
     * @param stopsCsvPath path del file stops.csv usato per caricare le fermate
     * @param vehiclePositionsService service realtime che fornisce i veicoli (cache)
     */
    public MapController(MapModel model, MapView view, String stopsCsvPath, VehiclePositionsService vehiclePositionsService) {
        this.model = model;
        this.view = view;
        this.stopsCsvPath = stopsCsvPath;
        this.vehiclePositionsService = vehiclePositionsService;

        this.shapePainter = new ShapePainter(routesPath, tripsPath);

        this.targetZoom = model.getZoom();

        // Smooth zoom: timer molto rapido per “inseguire” targetZoom.
        zoomTimer = new Timer(10, e -> smoothZoomStep());
        zoomTimer.start();

        // Layer veicoli: refresh lento (i veicoli vengono già fetchati dal service; qui aggiorniamo solo la vista).
        vehiclesRefreshTimer = new Timer(30_000, e -> refreshVehiclesLayerIfNeeded());
        vehiclesRefreshTimer.start();

        loadStops(stopsCsvPath);
        setupInteractions();
        refreshView();
    }

    // ========================= Caricamento fermate =========================

    /**
     * Legge tutte le fermate dal CSV tramite {@link StopService} e crea i relativi {@link StopWaypoint}.
     * Questo popolamento rappresenta lo stato “base” della mappa prima di eventuali filtri (es. ricerca linea).
     *
     * @param filePath path del file stops.csv
     */
    private void loadStops(String filePath) {
        List<StopModel> stops = getAllStops(filePath);

        model.getMarkers().clear();
        waypoints.clear();

        for (StopModel stop : stops) {
            GeoPosition pos = stop.getGeoPosition();
            if (pos != null) {
                model.addMarker(pos);
                waypoints.add(new StopWaypoint(stop));
            }
        }
    }

    // ========================= Interazioni con la mappa =========================

    /**
     * Registra tutti i listener di input su {@link JXMapViewer}:
     * - drag per spostare il centro,
     * - rotellina per smooth-zoom,
     * - click per debug (stop/cluster più vicino),
     * - click su marker (selezione fermata),
     * - sync del centro quando cambia dal viewer.
     */
    private void setupInteractions() {
        JXMapViewer map = view.getMapViewer();

        // Drag mappa: ricavo delta in pixel, lo converto in nuovo centro geograﬁco.
        MouseAdapter dragAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragPrev = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragPrev != null) {
                    Point current = e.getPoint();
                    int dx = current.x - dragPrev.x;
                    int dy = current.y - dragPrev.y;

                    GeoPosition center = map.getCenterPosition();
                    Point2D centerPx = map.getTileFactory().geoToPixel(center, model.getZoomInt());
                    centerPx.setLocation(centerPx.getX() - dx, centerPx.getY() - dy);
                    GeoPosition newCenter = map.getTileFactory().pixelToGeo(centerPx, model.getZoomInt());

                    model.setCenter(newCenter);
                    refreshView();

                    dragPrev = current;
                }
            }
        };
        map.addMouseListener(dragAdapter);
        map.addMouseMotionListener(dragAdapter);

        // Zoom con rotella (smooth): non tocchiamo subito model.setZoom, aggiorniamo solo targetZoom.
        map.addMouseWheelListener(e -> {
            double delta = -e.getPreciseWheelRotation() * 0.5;
            targetZoom += delta;
            targetZoom = model.clampZoom(targetZoom);
        });

        // Click mappa: logica “debug” per stop/cluster vicino + click diretto su marker.
        map.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                GeoPosition clicked = map.convertPointToGeoPosition(e.getPoint());
                int currentZoom = model.getZoomInt();

                // Raggio di ricerca (km) scelto empiricamente per zoom: serve a evitare selezioni troppo lontane.
                double radiusKm;
                switch (currentZoom) {
                    case 2:
                    case 3:
                        radiusKm = 0.05;
                        break;
                    case 4:
                        radiusKm = 0.08;
                        break;
                    case 5:
                        radiusKm = 0.15;
                        break;
                    case 6:
                        radiusKm = 0.4;
                        break;
                    case 7:
                        radiusKm = 1;
                        break;
                    case 8:
                        radiusKm = 2.1;
                        break;
                    default:
                        radiusKm = 0;
                }

                if (currentZoom > 1 && currentZoom <= 3) {
                    StopModel nearestStop = findNearestStop(clicked, radiusKm);
                    if (nearestStop != null) {
                        System.out.println("--- Fermata più vicina: ID=" + nearestStop.getId()
                                + ", Nome=" + nearestStop.getName());
                    }
                } else if (currentZoom > 3) {
                    ClusterModel nearestCluster = findNearestCluster(clicked, radiusKm);
                    if (nearestCluster != null) {
                        System.out.println("--- Cluster con centro" + nearestCluster.getPosition()
                                + ", con: " + nearestCluster.getSize() + " fermate");
                        centerMapOnCluster(nearestCluster);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // Click diretto sui marker: controllo “pixel hitbox” semplice (6px) per evitare geometrie complesse.
                for (StopWaypoint wp : waypoints) {
                    Point2D p = map.getTileFactory().geoToPixel(wp.getPosition(), model.getZoomInt());
                    if (Math.abs(p.getX() - e.getX()) < 6 && Math.abs(p.getY() - e.getY()) < 6) {
                        onMarkerClick(wp);
                        break;
                    }
                }
            }
        });

        // Se il viewer cambia centro (es. drag interno), sincronizzo il model e ridisegno.
        map.addPropertyChangeListener("centerPosition", evt -> {
            GeoPosition pos = (GeoPosition) evt.getNewValue();
            model.setCenter(pos);
            refreshView();
        });
    }

    /**
     * Applica uno step di zoom verso {@link #targetZoom}.
     * Scelta: interpolazione 0.2 per avere un effetto morbido ma reattivo.
     */
    private void smoothZoomStep() {
        double current = model.getZoom();
        if (Math.abs(current - targetZoom) < 0.01) return;

        double newZoom = current + (targetZoom - current) * 0.2;
        model.setZoom(newZoom);
        refreshView();
    }

    /**
     * Handler click su marker: per ora logga informazioni (utile in fase demo/esame).
     * Se in futuro servirà aprire pannello dettagli, questo è il punto giusto.
     */
    private void onMarkerClick(StopWaypoint wp) {
        StopModel stop = wp.getStop();
        if (stop != null) {
            System.out.println("--- Fermata cliccata: ID=" + stop.getId()
                    + ", Nome=" + stop.getName());
        }
    }

    /**
     * Cerca la fermata più vicina a una posizione, entro un raggio massimo.
     *
     * Nota: qui rilegge dal CSV per semplicità e coerenza con il modello StopService.
     * Se dovesse diventare un collo di bottiglia, si può ottimizzare usando la lista già in memoria.
     *
     * @param pos posizione di riferimento
     * @param radiusKm raggio massimo (km)
     * @return fermata più vicina oppure null se nessuna entro raggio
     */
    private StopModel findNearestStop(GeoPosition pos, double radiusKm) {
        List<StopModel> stops = getAllStops(stopsCsvPath);
        StopModel nearest = null;
        double minDist = radiusKm;

        for (StopModel stop : stops) {
            GeoPosition stopPos = stop.getGeoPosition();
            if (stopPos == null) continue;

            double dist = StopService.calculateDistance(pos, stopPos);
            if (dist <= minDist) {
                minDist = dist;
                nearest = stop;
            }
        }
        return nearest;
    }

    /**
     * Cerca il cluster più vicino tra quelli calcolati per lo zoom corrente.
     *
     * @param pos posizione di riferimento
     * @param radiusKm raggio massimo (km)
     * @return cluster più vicino oppure null se nessuno entro raggio
     */
    private ClusterModel findNearestCluster(GeoPosition pos, double radiusKm) {
        ClusterModel nearest = null;
        double minDist = radiusKm;

        for (ClusterModel clusterX : clusters) {
            GeoPosition clusterPos = clusterX.getPosition();
            if (clusterPos == null) continue;

            double dist = StopService.calculateDistance(pos, clusterPos);
            if (dist <= minDist) {
                minDist = dist;
                nearest = clusterX;
            }
        }
        return nearest;
    }

    // ========================= Metodi usati dalla ricerca =========================

    /**
     * Centra la mappa su una fermata e imposta uno zoom ravvicinato.
     * Inoltre salva la fermata come “evidenziata” così rimane visibile anche in modalità cluster.
     *
     * @param stop fermata da centrare (deve avere GeoPosition valida)
     */
    public void centerMapOnStop(StopModel stop) {
        if (stop == null || stop.getGeoPosition() == null) return;

        GeoPosition pos = stop.getGeoPosition();

        model.setCenter(pos);
        highlightedPosition = pos;

        // Manteniamo un marker dedicato (utile se poi la mappa passa a cluster-mode).
        highlightedStopWaypoint = new StopWaypoint(stop);

        double desiredZoom = 2.0;
        targetZoom = model.clampZoom(desiredZoom);
        model.setZoom(targetZoom);

        refreshView();
    }

    /**
     * Variante “robusta” che costruisce la {@link GeoPosition} da lat/lon.
     * Usata quando i dati stop arrivano da parsing/ricerca e non hanno già una GeoPosition pronta.
     *
     * @param stop fermata GTFS (lat/lon devono essere validi)
     */
    public void centerMapOnGtfsStop(StopModel stop) {
        if (stop == null) return;

        try {
            double lat = stop.getLatitude();
            double lon = stop.getLongitude();
            GeoPosition pos = new GeoPosition(lat, lon);

            model.setCenter(pos);
            highlightedPosition = pos;
            highlightedStopWaypoint = new StopWaypoint(stop);

            double desiredZoom = 2.0;
            targetZoom = model.clampZoom(desiredZoom);
            model.setZoom(targetZoom);

            refreshView();
        } catch (Exception e) {
            System.err.println("[MapController] centerMapOnGtfsStop: coordinate non valide per stop "
                    + stop.getId() + " (" + e.getMessage() + ")");
        }
    }

    /**
     * Centra la mappa su un cluster e riduce lo zoom di 1 livello (per “aprire” il cluster).
     * Qui aggiorniamo subito anche il viewer per rendere stabile il ricalcolo clustering.
     *
     * @param cluster cluster selezionato
     */
    public void centerMapOnCluster(ClusterModel cluster) {
        if (cluster == null || cluster.getPosition() == null) return;

        GeoPosition pos = cluster.getPosition();
        model.setCenter(pos);

        int reducedZoomInt = model.getZoomInt() - 1;
        double newZoom = model.clampZoom(reducedZoomInt);
        targetZoom = newZoom;
        model.setZoom(newZoom);

        // Update immediato del viewer: serve perché ClusterService usa map + zoom corrente.
        JXMapViewer map = view.getMapViewer();
        map.setZoom(model.getZoomInt());
        map.setCenterPosition(pos);

        refreshView();
    }

    /**
     * Ridisegna la mappa applicando:
     * - center e zoom attuali del model,
     * - modalità stops o modalità clusters in base allo zoom,
     * - shapes evidenziate,
     * - fermata evidenziata (se presente),
     * - layer veicoli filtrata (se attiva).
     *
     * Nota: la scelta stop/cluster è volutamente semplice (soglia su zoomInt) perché è facile da spiegare all’orale.
     */
    public void refreshView() {
        int zoomInt = (int) Math.round(model.getZoom());

        Set<StopWaypoint> stopsToDisplay;
        Set<ClusterModel> clustersToDisplay;

        JXMapViewer map = view.getMapViewer();
        map.setZoom(zoomInt);
        map.setCenterPosition(model.getCenter());

        if (zoomInt <= 3) {
            // Modalità "stops": mostro tutti i waypoint, ma garantisco che l’evidenziato resti presente.
            Set<StopWaypoint> tmp = new HashSet<>(waypoints);
            if (highlightedStopWaypoint != null) {
                tmp.add(highlightedStopWaypoint);
            }

            stopsToDisplay = tmp;
            clustersToDisplay = Set.of();
        } else {
            // Modalità "clusters": raggruppo i waypoint base in cluster (griglia dipendente dallo zoom).
            int gridSizePx = getGridSizeForZoom(zoomInt);
            clusters = ClusterService.createClusters(List.copyOf(waypoints), map, gridSizePx);

            // In cluster-mode mostro comunque la fermata evidenziata come marker singolo (se esiste).
            stopsToDisplay = (highlightedStopWaypoint != null) ? Set.of(highlightedStopWaypoint) : Set.of();
            clustersToDisplay = clusters;
        }

        view.updateView(
                model.getCenter(),
                zoomInt,
                stopsToDisplay,
                clustersToDisplay,
                shapePainter,
                highlightedPosition,
                visibleVehicles
        );
    }

    /**
     * Dimensione griglia (px) usata dal clustering in base allo zoom.
     * Valori empirici: più zoom alto (più vicino) → griglia più grande → meno clustering.
     *
     * @param zoom zoom intero attuale
     * @return grid size in pixel
     */
    private int getGridSizeForZoom(int zoom) {
        if (zoom >= 8) return 240;
        if (zoom >= 6) return 160;
        if (zoom >= 4) return 100;
        return 0;
    }

    /**
     * Rimuove l’evidenziazione delle linee (shape) e ridisegna.
     */
    public void clearRouteHighlight() {
        shapePainter.setHighlightedShapes(List.of());
        refreshView();
    }

    /**
     * Mostra solo le fermate passate in input, nascondendo tutte le altre.
     * Usa gli ID GTFS (stop_id) per filtrare le fermate già caricate da CSV.
     *
     * Nota: se la lista è vuota non facciamo nulla per evitare di “svuotare” completamente la mappa.
     *
     * @param stops lista di fermate da mantenere visibili
     */
    public void hideUselessStops(List<StopModel> stops) {
        if (stops == null || stops.isEmpty()) {
            System.out.println("[MapController] hideUselessStops chiamato con lista vuota.");
            return;
        }

        // 1) Insieme degli stop_id da tenere.
        Set<String> allowedIds = new HashSet<>();
        for (StopModel s : stops) {
            if (s != null && s.getId() != null) {
                allowedIds.add(s.getId());
            }
        }

        // 2) Rileggo tutte le fermate dal CSV e filtro.
        List<StopModel> allStops = getAllStops(stopsCsvPath);

        // 3) Reset marker/waypoints correnti.
        model.getMarkers().clear();
        waypoints.clear();

        // 4) Aggiungo solo fermate consentite.
        for (StopModel stop : allStops) {
            if (!allowedIds.contains(stop.getId())) continue;

            GeoPosition pos = stop.getGeoPosition();
            if (pos != null) {
                model.addMarker(pos);
                waypoints.add(new StopWaypoint(stop));
            }
        }

        System.out.println("[MapController] hideUselessStops → fermate visibili: " + waypoints.size());
        refreshView();
    }

    /**
     * Ripristina tutte le fermate dopo un filtro (es. line-search) e ridisegna.
     */
    public void showAllStops() {
        loadStops(stopsCsvPath);
        refreshView();
    }

    // ========================= Evidenziazione route (shapes) =========================

    /**
     * Evidenzia tutte le shape associate a una route, includendo entrambe le direzioni.
     *
     * @param routeId id della route GTFS
     */
    public void highlightRouteAllDirections(String routeId) {
        if (routeId == null || routeId.isBlank()) return;

        List<String> shapeIds = TripsService.getAllTrips(tripsPath).stream()
                .filter(trip -> routeId.equals(trip.getRoute_id()))
                .map(TripsModel::getShape_id)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .toList();

        List<ShapesModel> shapesToDraw = ShapesService.getAllShapes(shapesPath).stream()
                .filter(shape -> shapeIds.contains(shape.getShape_id()))
                .toList();

        shapePainter.setHighlightedShapes(shapesToDraw);
        refreshView();
    }

    /**
     * Evidenzia una route e adatta (fit) centro/zoom per includere tutta la linea (utile per line-search).
     *
     * @param routeId id route
     * @param directionId id direzione (come stringa per match con i model)
     */
    public void highlightRouteFitLine(String routeId, String directionId) {
        if (routeId == null || routeId.isBlank() || directionId == null) return;

        List<String> shapeIds = TripsService.getAllTrips(tripsPath).stream()
                .filter(trip -> routeId.equals(trip.getRoute_id())
                        && directionId.equals(trip.getDirection_id()))
                .map(TripsModel::getShape_id)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .toList();

        List<ShapesModel> shapesToDraw = ShapesService.getAllShapes(shapesPath).stream()
                .filter(shape -> shapeIds.contains(shape.getShape_id()))
                .toList();

        shapePainter.setHighlightedShapes(shapesToDraw);

        // Fit dinamico: porta in vista l’intera linea con padding.
        fitMapToShapes(shapesToDraw, /*paddingPx*/ 60);

        refreshView();
    }

    /**
     * Evidenzia una route mantenendo centro/zoom invariati (utile per stop-search: non vogliamo “spostare” l’utente).
     *
     * @param routeId id route
     * @param directionId id direzione
     */
    public void highlightRouteKeepStopView(String routeId, String directionId) {
        if (routeId == null || routeId.isBlank() || directionId == null) return;

        List<String> shapeIds = TripsService.getAllTrips(tripsPath).stream()
                .filter(trip -> routeId.equals(trip.getRoute_id())
                        && directionId.equals(trip.getDirection_id()))
                .map(TripsModel::getShape_id)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .toList();

        List<ShapesModel> shapesToDraw = ShapesService.getAllShapes(shapesPath).stream()
                .filter(shape -> shapeIds.contains(shape.getShape_id()))
                .toList();

        shapePainter.setHighlightedShapes(shapesToDraw);
        refreshView();
    }

    /**
     * Fit della mappa su una lista di shape:
     * - calcola bounds lat/lon,
     * - setta il centro,
     * - trova lo zoom più alto che fa stare tutto nella viewport + padding.
     *
     * @param shapes punti della shape (lista non vuota)
     * @param paddingPx padding in pixel per non attaccare la linea ai bordi
     */
    private void fitMapToShapes(List<ShapesModel> shapes, int paddingPx) {
        if (shapes == null || shapes.isEmpty()) return;

        JXMapViewer map = view.getMapViewer();

        double minLat = Double.POSITIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY, maxLon = Double.NEGATIVE_INFINITY;

        for (ShapesModel s : shapes) {
            double lat = Double.parseDouble(s.getShape_pt_lat());
            double lon = Double.parseDouble(s.getShape_pt_lon());
            minLat = Math.min(minLat, lat);
            maxLat = Math.max(maxLat, lat);
            minLon = Math.min(minLon, lon);
            maxLon = Math.max(maxLon, lon);
        }

        GeoPosition center = new GeoPosition((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0);

        // Metto il centro prima: il calcolo zoom è più stabile.
        map.setCenterPosition(center);
        model.setCenter(center);

        int bestZoom = findBestZoomForBounds(map, minLat, maxLat, minLon, maxLon, paddingPx);

        // Clamp finale: evitiamo zoom troppo “lontani” per non perdere il contesto.
        int minAllowedZoom = 10;
        int finalZoom = Math.max(minAllowedZoom, bestZoom);

        map.setZoom(finalZoom);
        model.setZoom(finalZoom);
        targetZoom = finalZoom;

        System.out.println("[fitMapToShapes] map size=" + map.getWidth() + "x" + map.getHeight()
                + " viewport=" + map.getViewportBounds()
                + " bestZoom=" + bestZoom);
    }

    /**
     * Trova lo zoom più alto (più vicino) che permette ai bounds di rientrare nella viewport.
     * Applica un “boost” per avvicinare leggermente rispetto al fit conservativo.
     *
     * @param map viewer usato per convertire geo→pixel ai vari livelli di zoom
     * @param minLat latitudine minima bounds
     * @param maxLat latitudine massima bounds
     * @param minLon longitudine minima bounds
     * @param maxLon longitudine massima bounds
     * @param paddingPx padding da rispettare (px)
     * @return zoom consigliato
     */
    private int findBestZoomForBounds(JXMapViewer map,
                                      double minLat, double maxLat,
                                      double minLon, double maxLon,
                                      int paddingPx) {

        final int MIN_ZOOM = 1;
        final int MAX_ZOOM = 15;

        // Non andare mai troppo lontano: scelta UX (linea sempre ben visibile).
        final int MIN_ALLOWED_ZOOM = 11;

        // Avvicina rispetto al fit “conservativo”: 1-3 rende l’inquadratura più piacevole.
        final int ZOOM_BOOST = 2;

        int viewW = map.getWidth();
        int viewH = map.getHeight();

        // Fallback su viewportBounds se width/height non sono ancora pronti.
        if (viewW <= 1 || viewH <= 1) {
            Rectangle vb = map.getViewportBounds();
            if (vb != null) {
                viewW = vb.width;
                viewH = vb.height;
            }
        }

        // Se ancora non abbiamo dimensioni affidabili, ritorniamo uno zoom “vicino” e stabile.
        if (viewW <= 1 || viewH <= 1) {
            return MIN_ALLOWED_ZOOM;
        }

        int usableW = Math.max(1, viewW - 2 * paddingPx);
        int usableH = Math.max(1, viewH - 2 * paddingPx);

        // Cerco lo zoom più alto che fa entrare i bounds.
        int best = MIN_ZOOM;
        for (int z = MAX_ZOOM; z >= MIN_ZOOM; z--) {
            Point2D p1 = map.getTileFactory().geoToPixel(new GeoPosition(maxLat, minLon), z);
            Point2D p2 = map.getTileFactory().geoToPixel(new GeoPosition(minLat, maxLon), z);

            double widthPx = Math.abs(p2.getX() - p1.getX());
            double heightPx = Math.abs(p2.getY() - p1.getY());

            if (widthPx <= usableW && heightPx <= usableH) {
                best = z;
                break;
            }
        }

        int finalZoom = best + ZOOM_BOOST;
        finalZoom = Math.min(MAX_ZOOM, finalZoom);
        finalZoom = Math.max(MIN_ALLOWED_ZOOM, finalZoom);

        return finalZoom;
    }

    /**
     * Evidenzia tutte le direzioni di una route mantenendo centro/zoom invariati (stop-mode).
     *
     * @param routeId id route
     */
    public void highlightRouteAllDirectionsKeepStopView(String routeId) {
        if (routeId == null || routeId.isBlank()) return;

        List<String> shapeIds = TripsService.getAllTrips(tripsPath).stream()
                .filter(trip -> routeId.equals(trip.getRoute_id()))
                .map(TripsModel::getShape_id)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .toList();

        List<ShapesModel> shapesToDraw = ShapesService.getAllShapes(shapesPath).stream()
                .filter(shape -> shapeIds.contains(shape.getShape_id()))
                .toList();

        shapePainter.setHighlightedShapes(shapesToDraw);
        refreshView();
    }

    // ========================= Layer veicoli realtime =========================

    /**
     * Aggiorna {@link #visibleVehicles} in base a route e direction selezionate.
     * Filtra anche le coordinate nulle per evitare marker invalidi.
     */
    private void updateVisibleVehiclesForSelectedRoute() {
        String routeId = selectedRouteId;
        Integer dir = selectedDirectionId;

        if (routeId == null || routeId.isBlank()) {
            visibleVehicles = List.of();
            return;
        }

        visibleVehicles = vehiclePositionsService.getVehicles().stream()
                .filter(v -> v.routeId != null && routeId.equals(v.routeId))
                .filter(v -> v.lat != null && v.lon != null)
                .filter(v -> dir == null || dir == -1 || (v.directionId != null && v.directionId.equals(dir)))
                .toList();
    }

    /**
     * Attiva (o aggiorna) la visualizzazione dei veicoli per una route/direction.
     *
     * @param routeId route da filtrare
     * @param directionId direction da filtrare (-1 = tutte)
     */
    public void showVehiclesForRoute(String routeId, int directionId) {
        this.selectedRouteId = routeId;
        this.selectedDirectionId = directionId;
        refreshVehiclesLayerIfNeeded();
    }

    /**
     * Disattiva la layer veicoli e ridisegna.
     */
    public void clearVehicles() {
        selectedRouteId = null;
        selectedDirectionId = null;
        visibleVehicles = List.of();
        refreshView();
    }

    /**
     * Aggiorna la layer veicoli solo se è attiva (route selezionata).
     * Nota: qui non fetchiamo la rete; leggiamo la cache del service.
     */
    public void refreshVehiclesLayerIfNeeded() {
        if (selectedRouteId == null) return;
        updateVisibleVehiclesForSelectedRoute();
        refreshView();
    }

    /**
     * Rimuove l’evidenziazione della fermata (marker speciale) e ridisegna.
     */
    public void clearHighlightedStop() {
        highlightedPosition = null;
        highlightedStopWaypoint = null;
        refreshView();
    }

    // ========================= Online/Offline binding =========================

    /**
     * Collega il controller a un {@link ConnectionStatusProvider} per reagire a ONLINE/OFFLINE.
     *
     * Comportamento:
     * - OFFLINE: tile solo offline, layer veicoli disattivata e stop del timer refresh veicoli.
     * - ONLINE: tile normali, riavvio timer refresh e aggiornamento layer se attiva.
     *
     * @param provider provider stato connessione
     */
    public void bindConnectionStatus(ConnectionStatusProvider provider) {
        if (provider == null) return;

        provider.addListener(state -> SwingUtilities.invokeLater(() -> {
            if (state == ConnectionState.OFFLINE) {
                View.Map.CustomTileFactory.setOfflineOnly(true);
                clearVehicles();
                vehiclesRefreshTimer.stop();
            } else {
                View.Map.CustomTileFactory.setOfflineOnly(false);
                if (!vehiclesRefreshTimer.isRunning()) vehiclesRefreshTimer.start();
                refreshVehiclesLayerIfNeeded();
            }
        }));
    }
}