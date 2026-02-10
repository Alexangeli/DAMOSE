package Service.Parsing;

import Model.Parsing.ShapesModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Creatore: Alessandro Angeli



    /**
     * Service per la gestione delle Shapes GTFS.
     * Legge i punti delle shapes da file CSV, gestisce la cache.
     */
    public class ShapesService {

        // ====== CACHE DEI DATI ======
        private static List<ShapesModel> cachedShapes = null;

        // ====== DATA ACCESS ======

        /**
         * Restituisce tutte le shapes dal CSV (usando cache se disponibile).
         */
        public static List<ShapesModel> getAllShapes(String filePath) {
            if (cachedShapes == null) {
                cachedShapes = readFromCSV(filePath);
            }
            return cachedShapes;
        }

        /**
         * Forza il ricaricamento della cache dal file.
         */
        public static void reloadShapes(String filePath) {
            cachedShapes = readFromCSV(filePath);
        }

        /**
         * Parsing diretto (privato) dal CSV.
         */
        private static List<ShapesModel> readFromCSV(String filePath) {
            List<ShapesModel> shapesList = new ArrayList<>();
            try (CSVReader reader = new CSVReader(
                    new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {

                String[] nextLine;
                reader.readNext(); // Salta intestazione

                while ((nextLine = reader.readNext()) != null) {
                    if (nextLine.length < 5) continue; // skip riga malformata
                    ShapesModel shape = new ShapesModel();
                    shape.setShape_id(nextLine[0].trim());
                    shape.setShape_pt_lat(nextLine[1].trim());
                    shape.setShape_pt_lon(nextLine[2].trim());
                    shape.setShape_pt_sequence(nextLine[3].trim());
                    shape.setShape_dist_traveled(nextLine[4].trim());
                    shapesList.add(shape);
                }
            } catch (IOException | CsvValidationException e) {
                System.err.println("Errore nella lettura/CSV shapes: " + e.getMessage());
            }
            return shapesList;
        }
    }

