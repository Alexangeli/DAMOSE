package Database;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ShapesModel {
    private String shape_id;
    private String shape_pt_lat;
    private String shape_pt_lon;
    private String shape_pt_sequence;
    private String shape_dist_traveled;

    public ShapesModel() {}

    public String getShape_pt_lat() {
        return shape_pt_lat;
    }

    public void setShape_pt_lat(String shape_pt_lat) {
        this.shape_pt_lat = shape_pt_lat;
    }

    public String getShape_id() {
        return shape_id;
    }

    public void setShape_id(String shape_id) {
        this.shape_id = shape_id;
    }

    public String getShape_pt_lon() {
        return shape_pt_lon;
    }

    public void setShape_pt_lon(String shape_pt_lon) {
        this.shape_pt_lon = shape_pt_lon;
    }

    public String getShape_pt_sequence() {
        return shape_pt_sequence;
    }

    public void setShape_pt_sequence(String shape_pt_sequence) {
        this.shape_pt_sequence = shape_pt_sequence;
    }

    public String getShape_dist_traveled() {
        return shape_dist_traveled;
    }

    public void setShape_dist_traveled(String shape_dist_traveled) {
        this.shape_dist_traveled = shape_dist_traveled;
    }

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
