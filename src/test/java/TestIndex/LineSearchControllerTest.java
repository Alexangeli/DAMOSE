package TestIndex;

import Controller.SearchMode.LineSearchController;
import Model.Map.RouteDirectionOption;
import View.SearchBar.SearchBarView;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class LineSearchControllerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private LineSearchController controller;
    private SearchBarView mockView;

    private String routesCsvPath;
    private String tripsCsvPath;

    @Before
    public void setUp() throws IOException {
        mockView = mock(SearchBarView.class);

        routesCsvPath = tmp.newFile("routes.csv").getAbsolutePath();
        tripsCsvPath  = tmp.newFile("trips.csv").getAbsolutePath();

        String routesCsv =
                "route_id,agency_id,route_short_name,route_long_name,route_type,route_url,route_color,route_text_color\n" +
                        "R1,,10,Line 10,3,,,,\n" +
                        "R2,,10A,Line 10A,3,,,,\n";

        String tripsCsv =
                "route_id,service_id,trip_id,trip_headsign,block_id,direction_id\n" +
                        "R1,,T1,Centro,,0\n" +
                        "R1,,T2,Periferia,,1\n" +
                        "R2,,T3,Centro,,0\n";

        java.nio.file.Files.write(java.nio.file.Path.of(routesCsvPath), routesCsv.getBytes(StandardCharsets.UTF_8));
        java.nio.file.Files.write(java.nio.file.Path.of(tripsCsvPath),  tripsCsv.getBytes(StandardCharsets.UTF_8));

        controller = new LineSearchController(mockView, null, routesCsvPath, tripsCsvPath);
    }

    @Test
    public void onTextChangedShowsSuggestions() {
        controller.onTextChanged("10");
        verify(mockView, timeout(300)).showLineSuggestions(argThat(list -> list != null && list.size() >= 2));
    }

    @Test
    public void onTextChangedHidesWhenBlank() {
        controller.onTextChanged("");
        verify(mockView, timeout(300)).hideSuggestions();

        controller.onTextChanged("   ");
        verify(mockView, timeout(300).times(2)).hideSuggestions();
    }

    @Test
    public void onRouteDirectionSelectedDelegatesToMapController() {
        Controller.Map.MapController mockMap = mock(Controller.Map.MapController.class);
        controller = new LineSearchController(mockView, mockMap, routesCsvPath, tripsCsvPath);

        RouteDirectionOption opt = new RouteDirectionOption("R1", "10", 0, "Centro", 3);
        controller.onRouteDirectionSelected(opt);

        // Assumendo signature: highlightRoute(String routeId, String directionId)
        verify(mockMap, timeout(300)).highlightRoute("R1", "0");
    }

    @Test
    public void onTextChangedWithNoMatchesShowsEmptyList() {
        controller.onTextChanged("999999");

        // comportamento reale del tuo controller: showLineSuggestions([])
        verify(mockView, timeout(300))
                .showLineSuggestions(argThat(list -> list != null && list.isEmpty()));

        // opzionale: assicura che non abbia chiamato hideSuggestions in questo caso
        verify(mockView, never()).hideSuggestions();
    }
}