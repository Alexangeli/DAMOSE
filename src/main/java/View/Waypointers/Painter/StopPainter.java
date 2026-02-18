package View.Waypointers.Painter;

import View.Waypointers.Waypoint.StopWaypoint;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Set;
import java.lang.reflect.*;

/**
 * Painter che disegna i waypoint delle fermate sulla mappa.
 *
 * Regole di rendering:
 * - fermate normali: puntino (dot) con riempimento e bordo
 * - fermata evidenziata: marker "a goccia" (pin) con foro bianco centrale
 *
 * La colorazione prova ad utilizzare il tema dell'applicazione tramite reflection;
 * in assenza del sistema tema, viene usato un fallback consistente.
 */
public class StopPainter extends WaypointPainter<Waypoint> {

    private static final int DOT_SIZE = 8;

    private final GeoPosition highlightedPos;

    /**
     * Costruisce il painter delle fermate.
     * Viene fatta una copia difensiva del set per evitare modifiche concorrenti durante il rendering.
     *
     * @param stops insieme di waypoint di fermata (possono essere null)
     * @param highlightedPos posizione da evidenziare (può essere null)
     */
    public StopPainter(Set<StopWaypoint> stops, GeoPosition highlightedPos) {
        this.highlightedPos = highlightedPos;

        Set<Waypoint> copy = new HashSet<>();
        if (stops != null) {
            // StopWaypoint è un Waypoint, quindi può essere aggiunto direttamente.
            copy.addAll(stops);
        }
        setWaypoints(copy);
    }

    /**
     * Disegna tutte le fermate visibili nel viewport corrente.
     * Per ciascun waypoint calcola la posizione in pixel e sceglie se renderizzare
     * dot oppure pin in base al confronto con la posizione evidenziata.
     *
     * @param g contesto grafico
     * @param map mappa JXMapViewer
     * @param w larghezza disponibile per il painter
     * @param h altezza disponibile per il painter
     */
    @Override
    protected void doPaint(Graphics2D g, JXMapViewer map, int w, int h) {
        Set<? extends Waypoint> pts = getWaypoints();
        if (pts == null || pts.isEmpty()) return;

        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Rectangle viewport = map.getViewportBounds();

        for (Waypoint wp : pts) {
            if (wp == null || wp.getPosition() == null) continue;

            Point2D pt = map.getTileFactory().geoToPixel(wp.getPosition(), map.getZoom());

            int x = (int) (pt.getX() - viewport.getX());
            int y = (int) (pt.getY() - viewport.getY());

            boolean isHighlighted = highlightedPos != null
                    && samePosition(wp.getPosition(), highlightedPos);

            if (isHighlighted) {
                drawPinMarker(g, x, y);
            } else {
                drawDot(g, x, y);
            }
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }

    /**
     * Disegna un puntino centrato sulla posizione (x,y).
     *
     * @param g contesto grafico
     * @param x coordinata x in pixel
     * @param y coordinata y in pixel
     */
    private void drawDot(Graphics2D g, int x, int y) {
        Color fill = withAlpha(themePrimaryOrFallback(), 200);
        Color stroke = themeBorderStrongOrFallback(fill);

        g.setColor(fill);
        g.fillOval(x - DOT_SIZE / 2, y - DOT_SIZE / 2, DOT_SIZE, DOT_SIZE);

        g.setColor(stroke);
        g.drawOval(x - DOT_SIZE / 2, y - DOT_SIZE / 2, DOT_SIZE, DOT_SIZE);
    }

    /**
     * Disegna un marker "a goccia" con la punta rivolta verso il basso.
     * La punta coincide con (x,y), mentre la parte circolare viene disegnata più in alto.
     *
     * @param g contesto grafico
     * @param x coordinata x in pixel della punta
     * @param y coordinata y in pixel della punta
     */
    private void drawPinMarker(Graphics2D g, int x, int y) {
        int r = 10;
        int circleCenterY = y - r * 2;

        Color fill = withAlpha(themePrimaryOrFallback(), 230);
        Color stroke = themeBorderStrongOrFallback(fill);

        // Corpo (cerchio + triangolo)
        g.setColor(fill);
        g.fillOval(x - r, circleCenterY - r, 2 * r, 2 * r);

        Polygon p = new Polygon();
        p.addPoint(x, y);
        p.addPoint(x - r, circleCenterY);
        p.addPoint(x + r, circleCenterY);
        g.fillPolygon(p);

        // Bordo
        g.setColor(stroke);
        g.drawOval(x - r, circleCenterY - r, 2 * r, 2 * r);
        g.drawPolygon(p);

        // Foro interno per enfatizzare il marker
        int innerR = r / 2;
        g.setColor(Color.WHITE);
        g.fillOval(x - innerR, circleCenterY - innerR, 2 * innerR, 2 * innerR);
    }

    /**
     * Confronto "morbido" tra due posizioni geografiche.
     * Serve a gestire piccoli scostamenti dovuti a conversioni/arrotondamenti.
     *
     * @param a prima posizione
     * @param b seconda posizione
     * @return true se latitudine e longitudine sono sufficientemente vicine
     */
    private boolean samePosition(GeoPosition a, GeoPosition b) {
        double eps = 1e-6;
        return Math.abs(a.getLatitude() - b.getLatitude()) < eps
                && Math.abs(a.getLongitude() - b.getLongitude()) < eps;
    }

    // ===================== THEME (safe via reflection) =====================

    /**
     * @return colore principale del tema, oppure rosso puro come fallback
     */
    private static Color themePrimaryOrFallback() {
        Color c = fromThemeField("primary");
        return (c != null) ? c : new Color(255, 0, 0);
    }

    /**
     * Recupera un colore di bordo dal tema; se non esiste, ne deriva uno più scuro dal colore base.
     *
     * @param base colore di riferimento per il fallback
     * @return colore bordo forte (tema o fallback)
     */
    private static Color themeBorderStrongOrFallback(Color base) {
        Color c = fromThemeField("borderStrong");
        if (c != null) return c;
        return darken(base, 0.55);
    }

    /**
     * Applica un valore alpha (0..255) ad un colore.
     *
     * @param c colore base
     * @param a alpha desiderato (viene clampato tra 0 e 255)
     * @return colore con alpha aggiornato, oppure null se c è null
     */
    private static Color withAlpha(Color c, int a) {
        if (c == null) return null;
        int aa = Math.max(0, Math.min(255, a));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), aa);
    }

    /**
     * Scurisce un colore mantenendo lo stesso alpha.
     *
     * @param c colore base
     * @param factor fattore moltiplicativo sui canali RGB (0..1)
     * @return colore scurito, oppure null se c è null
     */
    private static Color darken(Color c, double factor) {
        if (c == null) return null;
        factor = Math.max(0.0, Math.min(1.0, factor));
        int r = (int) Math.round(c.getRed() * factor);
        int g = (int) Math.round(c.getGreen() * factor);
        int b = (int) Math.round(c.getBlue() * factor);
        return new Color(r, g, b, c.getAlpha());
    }

    /**
     * Legge un campo pubblico di tipo {@link Color} dal tema corrente, se presente.
     * Flusso: View.Theme.ThemeManager.get() -> theme, poi accesso al campo fieldName.
     *
     * @param fieldName nome del campo colore nel tema
     * @return colore del tema, oppure null se il tema non esiste o il campo non è disponibile
     */
    private static Color fromThemeField(String fieldName) {
        try {
            Class<?> tm = Class.forName("View.Theme.ThemeManager");
            Method get = tm.getMethod("get");
            Object theme = get.invoke(null);
            if (theme == null) return null;

            Field f;
            try {
                f = theme.getClass().getField(fieldName);
            } catch (NoSuchFieldException nf) {
                return null;
            }

            Object v = f.get(theme);
            return (v instanceof Color col) ? col : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
