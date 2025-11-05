package Database;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CalendarDatesModel {
    private String service_id;
    private String date;
    private String exception_type;

    public CalendarDatesModel() {}

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getService_id() {
        return service_id;
    }

    public void setService_id(String service_id) {
        this.service_id = service_id;
    }

    public String getException_type() {
        return exception_type;
    }

    public void setException_type(String exception_type) {
        this.exception_type = exception_type;
    }

    public static List<CalendarDatesModel> readFromCSV(String filePath) {
        List<CalendarDatesModel> calendarDatesList = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {

            String[] nextLine;
            reader.readNext(); // salta l'intestazione

            while ((nextLine = reader.readNext()) != null) {
                CalendarDatesModel calendarDate = new CalendarDatesModel();
                calendarDate.setService_id(nextLine[0].trim());
                calendarDate.setDate(nextLine[1].trim());
                calendarDate.setException_type(nextLine[2].trim());


                calendarDatesList.add(calendarDate);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }

        return calendarDatesList;
    }

}
