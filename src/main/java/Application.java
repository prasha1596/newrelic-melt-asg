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

/*
curl -L \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer <YOUR-TOKEN>" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  https://api.github.com/repos/OWNER/REPO/community/profile
 */
public class Application {
    private static final String GITHUB_API_URL = "https://api.github.com/repos/newrelic/centurion/community/profile";
    private static final String GITHUB_TOKEN = "";
    private static final String NEW_RELIC_INSERT_KEY = "29eeb234086a3d09c949e2af8a47560d711cNRAL";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

    public static void main(String[] args) throws IOException, ResponseException {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(GITHUB_API_URL)
                .addHeader("Authorization", "token " + GITHUB_TOKEN)
                .build();

        Response response = client.newCall(request).execute();
        String responseBody = response.body().string();

        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();

        // Metrics from the community profile
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
                .put("repository.name", "centurion")
                .put("source", "github")
                .put("fetched_at", Instant.now().toString());

        metricBuffer.addMetric(new Gauge("github.community.health_percentage", healthPercentage, timestamp, commonAttributes));
        metricBuffer.addMetric(new Gauge("github.community.description_length", descriptionLength, timestamp, commonAttributes));
        metricBuffer.addMetric(new Gauge("github.community.updated_at_length", updatedAtLength, timestamp, commonAttributes));

        metricBatchSender.sendBatch(metricBuffer.createBatch());
        System.out.println("Metrics sent to New Relic.");
    }
}