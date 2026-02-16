package Service.Parsing;

import Model.Parsing.Static.StopTimesModel;
import Model.Points.StopModel;
import Service.Parsing.Static.StaticGtfsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TripStopsService {

    private TripStopsService() {}

    public static List<StopModel> getStopsForRouteDirection(String routeId, int directionId, StaticGtfsRepository repo) {
        Objects.requireNonNull(repo, "repo null");
        if (routeId == null || routeId.isBlank()) return List.of();

        String tripId = repo.getRepresentativeTripId(routeId, directionId);
        if (tripId == null) return List.of();

        List<StopTimesModel> stopTimes = repo.getStopTimesForTrip(tripId);
        if (stopTimes == null || stopTimes.isEmpty()) return List.of();

        ArrayList<StopModel> out = new ArrayList<>();
        for (StopTimesModel st : stopTimes) {
            if (st == null) continue;
            String stopId = st.getStop_id();
            if (stopId == null || stopId.isBlank()) continue;

            StopModel stop = repo.getStopById(stopId);
            if (stop != null) out.add(stop);
        }
        return out;
    }
}