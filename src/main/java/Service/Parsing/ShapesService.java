package Service.Parsing;

import Model.Parsing.Static.ShapesModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service per la lettura delle Shapes GTFS (shapes.csv).
 *
 * Responsabilità:
 * - leggere i punti delle shape dal CSV e convertirli in {@link ShapesModel}
 * - mantenere una cache in memoria per evitare letture ripetute del file
 *
 * Contesto:
 * - usato nella parte mappa per disegnare i percorsi (polilinee) associati alle linee.
 *
 * Note di progetto:
 * - la cache è globale (non per-path): se si cambia dataset o file, chiamare {@link #reloadShapes(String)}.
 * - in caso di errori di lettura/parsing ritorna una lista vuota (fallback verso chiamanti/UI).
 *
 * Creatore: Alessandro Angeli
 */
public class ShapesService {

    /**
     * Cache delle shape caricate.
     * Contiene tutti i punti di tutte le shape presenti nel file.
     */
    private static List<ShapesModel> cachedShapes = null;

    /**
     * Restituisce tutte le shape lette dal CSV (usando cache se disponibile).
     *
     * @param filePath path del file shapes.csv
     * @return lista di punti shape (vuota in caso di errori)
     */
    public static List<ShapesModel> getAllShapes(String filePath) {
        if (cachedShapes == null) {
            cachedShapes = readFromCSV(filePath);
        }
        return cachedShapes;
    }

    /**
     * Forza il ricaricamento della cache dal file specificato.
     *
     * @param filePath path del file shapes.csv
     */
    public static void reloadShapes(String filePath) {
        cachedShapes = readFromCSV(filePath);
    }

    /**
     * Legge il CSV e costruisce la lista di {@link ShapesModel}.
     *
     * Assunzioni:
     * - presenza dell'header in prima riga
     * - layout minimo di 5 colonne: shape_id, lat, lon, sequence, dist_traveled
     *
     * @param filePath path del file shapes.csv
     * @return lista di punti shape (mai null)
     */
    private static List<ShapesModel> readFromCSV(String filePath) {
        List<ShapesModel> shapesList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8)
        )) {
            String[] nextLine;
            reader.readNext(); // header

            while ((nextLine = reader.readNext()) != null) {
                if (nextLine.length < 5) {
                    continue; // riga malformata
                }

                ShapesModel shape = new ShapesModel();
                shape.setShape_id(safe(nextLine[0]));
                shape.setShape_pt_lat(safe(nextLine[1]));
                shape.setShape_pt_lon(safe(nextLine[2]));
                shape.setShape_pt_sequence(safe(nextLine[3]));
                shape.setShape_dist_traveled(safe(nextLine[4]));

                shapesList.add(shape);
            }
        } catch (IOException | CsvValidationException e) {
            System.err.println("Errore nella lettura/CSV shapes: " + e.getMessage());
        }

        return shapesList;
    }

    /**
     * Trim "sicuro": evita null.
     *
     * @param s stringa in input
     * @return stringa trim()mata oppure vuota se null
     */
    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }
}