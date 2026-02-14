package Service.GTFS_RT.Client;

import com.google.transit.realtime.GtfsRealtime;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpGtfsRtFeedClient implements GtfsRtFeedClient {

    private final HttpClient client;
    private final Duration requestTimeout;

    public HttpGtfsRtFeedClient(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;

        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(requestTimeout)   // << usa il timeout passato
                .build();
    }

    @Override
    public GtfsRealtime.FeedMessage fetch(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .GET()
                .build();

        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());

        int code = resp.statusCode();

        // se non 2xx -> errore, ma includo Location se presente
        if (code < 200 || code >= 300) {
            String location = resp.headers().firstValue("location").orElse(null);
            if (location != null) {
                throw new RuntimeException("GTFS-RT HTTP error " + code + " for url=" + url + " (Location=" + location + ")");
            }
            throw new RuntimeException("GTFS-RT HTTP error " + code + " for url=" + url);
        }

        byte[] body = resp.body();
        if (body == null || body.length == 0) {
            throw new RuntimeException("GTFS-RT empty body for url=" + url);
        }

        try (ByteArrayInputStream bin = new ByteArrayInputStream(body)) {
            return GtfsRealtime.FeedMessage.parseFrom(bin);
        } catch (Exception parseEx) {
            throw new RuntimeException("GTFS-RT parse error for url=" + url + " (bytes=" + body.length + ")", parseEx);
        }
    }
}