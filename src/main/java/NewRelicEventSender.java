import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class NewRelicEventSender {

    private static final String NEW_RELIC_API_URL = "https://insights-collector.newrelic.com/v1/accounts/4462067/events";
    private static final String API_KEY = "29eeb234086a3d09c949e2af8a47560d711cNRAL";

    public static void main(String[] args) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "Purchase");
        event.put("account", 5);
        event.put("amount", 400);

        try {
            sendEvent(event);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sendEvent(Map<String, Object> event) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(event);

        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(NEW_RELIC_API_URL);

        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setHeader("Api-Key", API_KEY);
        httpPost.setEntity(new StringEntity(json));

        CloseableHttpResponse response = client.execute(httpPost);
        client.close();

        System.out.println("Response Status: " + response.getStatusLine().getStatusCode());
    }
}
