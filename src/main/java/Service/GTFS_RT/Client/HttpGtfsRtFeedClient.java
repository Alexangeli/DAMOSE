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
        this.client = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .build();
        this.requestTimeout = requestTimeout;
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
        if (code < 200 || code >= 300) {
            throw new RuntimeException("GTFS-RT HTTP error " + code + " for url=" + url);
        }

        try (ByteArrayInputStream bin = new ByteArrayInputStream(resp.body())) {
            return GtfsRealtime.FeedMessage.parseFrom(bin);
        }
    }
}