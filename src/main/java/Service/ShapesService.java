package Service;

import Model.ShapesModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ShapesService {
    public static java.util.List<ShapesModel> readFromCSV(String filePath) {
        List<ShapesModel> shapesList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {

            String[] nextLine;
            reader.readNext(); // salta l'intestazione

            while ((nextLine = reader.readNext()) != null) {
                ShapesModel shape = new ShapesModel();
                shape.setShape_id(nextLine[0].trim());
                shape.setShape_pt_lat(nextLine[1].trim());
                shape.setShape_pt_lon(nextLine[2].trim());
                shape.setShape_pt_sequence(nextLine[3].trim());
                shape.setShape_dist_traveled(nextLine[4].trim());

                shapesList.add(shape);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }

        return shapesList;
    }
}
