package Service.Parsing.Static;

import Model.Parsing.Static.RoutesModel;
import Model.Parsing.Static.StopTimesModel;
import Model.Parsing.Static.TripsModel;
import Model.Points.StopModel;

import java.util.List;

/**
 * DTO con tutte le liste GTFS static già caricate.
 * Serve al Builder per passare i dati alla Repository.
 */
public final class StaticGtfsData {

    public final List<StopModel> stops;
    public final List<RoutesModel> routes;
    public final List<TripsModel> trips;
    public final List<StopTimesModel> stopTimes;

    public StaticGtfsData(
            List<StopModel> stops,
            List<RoutesModel> routes,
            List<TripsModel> trips,
            List<StopTimesModel> stopTimes
    ) {
        this.stops = stops;
        this.routes = routes;
        this.trips = trips;
        this.stopTimes = stopTimes;
    }
}