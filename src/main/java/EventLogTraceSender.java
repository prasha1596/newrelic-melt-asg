import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.newrelic.telemetry.Attributes;
import com.newrelic.telemetry.exceptions.ResponseException;
import com.newrelic.telemetry.http.HttpPoster;
import com.newrelic.telemetry.http.HttpResponse;
import com.newrelic.telemetry.metrics.Gauge;
import com.newrelic.telemetry.metrics.MetricBatchSender;
import com.newrelic.telemetry.metrics.MetricBuffer;
import okhttp3.*;

import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class EventLogTraceSender {
    private static final String GITHUB_API_URL = "https://api.github.com/repos/newrelic/node-newrelic/community/profile";
    private static final String GITHUB_TOKEN = "";  // Set your GitHub token here
    private static final String NEW_RELIC_INSERT_KEY = ""; // Set your New Relic insert key here
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

    public static void main(String[] args) throws IOException, ResponseException {
        OkHttpClient client = new OkHttpClient();

        // GitHub API request
        Request request = new Request.Builder()
                .url(GITHUB_API_URL)
                .addHeader("Authorization", "token " + GITHUB_TOKEN)
                .build();

        Response response = client.newCall(request).execute();
        String responseBody = response.body().string();
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();

        // Extract metrics from the community profile
        int healthPercentage = jsonObject.get("health_percentage").getAsInt();
        int descriptionLength = jsonObject.get("description").getAsString().length();
        int updatedAtLength = jsonObject.get("updated_at").getAsString().length();

        MetricBatchSender metricBatchSender = MetricBatchSender.create(
                MetricBatchSender.configurationBuilder().httpPoster(new HttpPoster() {
                    @Override
                    public HttpResponse post(URL url, Map<String, String> map, byte[] bytes, String s) throws IOException {
                        Request.Builder requestBuilder = new Request.Builder()
                                .url(url)
                                .post(RequestBody.create(bytes, JSON_MEDIA_TYPE));
                        map.forEach(requestBuilder::addHeader);
                        Response response = client.newCall(requestBuilder.build()).execute();
                        return new HttpResponse(response.body().string(), response.code(), "", new HashMap<>());
                    }
                })
                        .apiKey(NEW_RELIC_INSERT_KEY)
                        .build()
        );

        MetricBuffer metricBuffer = new MetricBuffer(new Attributes().put("source", "github"));
        long timestamp = Instant.now().toEpochMilli();
        Attributes commonAttributes = new Attributes()
                .put("repository.owner", "newrelic")
                .put("repository.name", "node-newrelic")
                .put("source", "github")
                .put("fetched_at", Instant.now().toString());

        metricBuffer.addMetric(new Gauge("github.community.health_percentage", healthPercentage, timestamp, commonAttributes));
        metricBuffer.addMetric(new Gauge("github.community.description_length", descriptionLength, timestamp, commonAttributes));
        metricBuffer.addMetric(new Gauge("github.community.updated_at_length", updatedAtLength, timestamp, commonAttributes));

        metricBatchSender.sendBatch(metricBuffer.createBatch());

        // Send events to New Relic
        sendEventToNewRelic(client, jsonObject, timestamp);
        // Send logs to New Relic
        sendLogToNewRelic(client, jsonObject, timestamp);
        // Send traces to New Relic
        sendTraceToNewRelic(client, jsonObject, timestamp);

        System.out.println("Metrics, events, logs, and traces sent to New Relic.");
    }

    private static void sendEventToNewRelic(OkHttpClient client, JsonObject jsonObject, long timestamp) throws IOException {
        String url = "https://insights-collector.newrelic.com/v1/accounts/4462067/events"; // Update with your New Relic account ID
        JsonObject event = new JsonObject();
        event.addProperty("eventType", "GitHubEvent");
        event.addProperty("repository", "newrelic/centurion");
        event.addProperty("health_percentage", jsonObject.get("health_percentage").getAsInt());
        event.addProperty("timestamp", timestamp);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(event.toString(), JSON_MEDIA_TYPE))
                .addHeader("Content-Type", "application/json")
                .addHeader("Api-Key", NEW_RELIC_INSERT_KEY)
                .build();

        Response response = client.newCall(request).execute();
        System.out.println("Event response: " + response.body().string());
    }

    private static void sendLogToNewRelic(OkHttpClient client, JsonObject jsonObject, long timestamp) throws IOException {
        String url = "https://log-api.newrelic.com/log/v1"; // New Relic log API endpoint
        JsonObject log = new JsonObject();
        log.addProperty("message", "Prachi pushed this log");
        log.addProperty("repository", "newrelic/centurion");
        log.addProperty("level", "info");
        log.addProperty("timestamp", timestamp);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(log.toString(), JSON_MEDIA_TYPE))
                .addHeader("Content-Type", "application/json")
                .addHeader("Api-Key", NEW_RELIC_INSERT_KEY)
                .build();

        Response response = client.newCall(request).execute();
        System.out.println("Log response: " + response.body().string());
    }

    private static void sendTraceToNewRelic(OkHttpClient client, JsonObject jsonObject, long timestamp) throws IOException {
        String url = "https://trace-api.newrelic.com/trace/v1"; // New Relic trace API endpoint
        JsonObject trace = new JsonObject();
        trace.addProperty("trace.id", "github.trace." + timestamp);
        trace.addProperty("span.id", "github.span." + timestamp);
        trace.addProperty("name", "Fetch GitHub Community Profile");
        trace.addProperty("timestamp", timestamp);
        trace.addProperty("duration.ms", 1000); // Example duration
        trace.addProperty("repository", "newrelic/centurion");

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(trace.toString(), JSON_MEDIA_TYPE))
                .addHeader("Content-Type", "application/json")
                .addHeader("Api-Key", NEW_RELIC_INSERT_KEY)
                .build();

        Response response = client.newCall(request).execute();
        System.out.println("Trace response: " + response.body().string());
    }
}
