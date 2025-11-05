package Service;

import Model.StopTimesModel;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class StopTimesService {
    public static List<StopTimesModel> readFromCSV(String filePath) {
        List<StopTimesModel> stopsTimesList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {

            String[] nextLine;
            reader.readNext(); // salta l'intestazione

            while ((nextLine = reader.readNext()) != null) {
                StopTimesModel stopTimes = new StopTimesModel();
                stopTimes.setTrip_id(nextLine[0].trim());
                stopTimes.setArrival_time(nextLine[1].trim());
                stopTimes.setDeparture_time(nextLine[2].trim());
                stopTimes.setStop_id(nextLine[3].trim());
                stopTimes.setStop_sequence(nextLine[4].trim());
                stopTimes.setStop_headsign(nextLine[5].trim());
                stopTimes.setPickup_type(nextLine[6].trim());
                stopTimes.setDrop_off_type(nextLine[7].trim());
                stopTimes.setShape_dist_traveled(nextLine[8].trim());
                stopTimes.setTimepoint(nextLine[9].trim());

                stopsTimesList.add(stopTimes);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }

        return stopsTimesList;
    }

}
