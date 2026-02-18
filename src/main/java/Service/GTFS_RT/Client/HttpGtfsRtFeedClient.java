package Service.GTFS_RT.Client;

import com.google.transit.realtime.GtfsRealtime;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client HTTP per scaricare feed GTFS-Realtime.
 *
 * Implementa GtfsRtFeedClient utilizzando Java HttpClient.
 * Permette di:
 * - impostare un timeout personalizzato
 * - gestire redirect HTTP
 * - verificare codici di stato HTTP non 2xx
 * - rilevare body vuoti o errori di parsing del feed
 *
 * L'output è un oggetto GtfsRealtime.FeedMessage pronto per
 * essere processato da servizi come ArrivalPredictionService
 * o AlertsService.
 *
 * Autore: Simone Bonuso
 */
public class HttpGtfsRtFeedClient implements GtfsRtFeedClient {

    /** Client HTTP configurato con redirect e timeout. */
    private final HttpClient client;

    /** Timeout massimo per la richiesta HTTP. */
    private final Duration requestTimeout;

    /**
     * Crea un client HTTP per GTFS-Realtime con timeout specificato.
     *
     * @param requestTimeout durata massima per ogni richiesta HTTP
     */
    public HttpGtfsRtFeedClient(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;

        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(requestTimeout)
                .build();
    }

    /**
     * Scarica e restituisce il feed GTFS-Realtime dall'URL specificato.
     *
     * Controlla:
     * - codice HTTP (solo 2xx considerati validi)
     * - body non vuoto
     * - parsing corretto del feed protobuf
     *
     * @param url URL del feed GTFS-Realtime
     * @return FeedMessage contenente i dati del feed
     * @throws Exception in caso di errore di rete, HTTP o parsing
     */
    @Override
    public GtfsRealtime.FeedMessage fetch(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .GET()
                .build();

        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());

        int code = resp.statusCode();

        // Se il codice HTTP non è 2xx, genera un'eccezione
        if (code < 200 || code >= 300) {
            String location = resp.headers().firstValue("location").orElse(null);
            if (location != null) {
                throw new RuntimeException(
                        "GTFS-RT HTTP error " + code + " for url=" + url + " (Location=" + location + ")"
                );
            }
            throw new RuntimeException("GTFS-RT HTTP error " + code + " for url=" + url);
        }

        byte[] body = resp.body();
        if (body == null || body.length == 0) {
            throw new RuntimeException("GTFS-RT empty body for url=" + url);
        }

        // Parsing del feed protobuf
        try (ByteArrayInputStream bin = new ByteArrayInputStream(body)) {
            return GtfsRealtime.FeedMessage.parseFrom(bin);
        } catch (Exception parseEx) {
            throw new RuntimeException(
                    "GTFS-RT parse error for url=" + url + " (bytes=" + body.length + ")",
                    parseEx
            );
        }
    }
}