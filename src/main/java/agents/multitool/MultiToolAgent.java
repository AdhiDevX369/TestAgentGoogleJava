package agents.multitool;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.io.IOException; // Added
import java.net.URI; // Added
import java.net.URLEncoder; // Added
import java.net.http.HttpClient; // Added
import java.net.http.HttpRequest; // Added
import java.net.http.HttpResponse; // Added
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration; // Added
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import org.json.JSONArray;
import org.json.JSONObject;

public class MultiToolAgent {

    private static String USER_ID = "student";
    private static String NAME = "multi_tool_agent";

    // For OpenWeatherMap API
    private static final String OPENWEATHERMAP_API_URL_BASE = "https://api.openweathermap.org/data/2.5/weather";
    private static String OPENWEATHERMAP_API_KEY;
    private static HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    static {
        OPENWEATHERMAP_API_KEY = System.getenv("OPENWEATHERMAP_API_KEY");
        if (OPENWEATHERMAP_API_KEY == null || OPENWEATHERMAP_API_KEY.isEmpty()) {
            System.err.println(
                    "WARNING: OPENWEATHERMAP_API_KEY environment variable not set. "
                            + "The weather tool will not function correctly.");
        }
    }

    public static BaseAgent ROOT_AGENT = initAgent();

    public static BaseAgent initAgent() {
        return LlmAgent.builder()
                .name(NAME)
                .model("gemini-2.0-flash")
                .description("Agent to answer questions about the time and weather in multiple cities.")
                .instruction(
                        "You are a helpful agent who can answer user questions about the time and weather"
                                + " in one or multiple cities. When asked about multiple cities, provide information for each city separately."
                                + " When reporting time or weather, use the city name as provided in the query.")
                .tools(
                        FunctionTool.create(MultiToolAgent.class, "getCurrentTime"),
                        FunctionTool.create(MultiToolAgent.class, "getWeather"))
                .build();
    }

    public static Map<String, String> getCurrentTime(
            @Schema(description = "The name of the city or ZoneId (e.g., 'London', 'New York', 'Europe/Paris', 'UTC') for which to retrieve the current time")
            String cityOrZoneIdFromLlm) {

        String normalizedInput = Normalizer.normalize(cityOrZoneIdFromLlm, Normalizer.Form.NFD)
                .trim()
                .toLowerCase();
        normalizedInput = normalizedInput.replaceAll("\\p{IsM}+", "");

        if (normalizedInput.contains("/")) {
            normalizedInput = normalizedInput.replaceAll("[^a-z0-9/_]+", "");
        } else {
            normalizedInput = normalizedInput.replaceAll("\\p{IsP}+", "");
            normalizedInput = normalizedInput.replaceAll("\\s+", "_");
        }

        final String finalNormalizedInput = normalizedInput;

        Optional<String> foundZoneIdOpt = ZoneId.getAvailableZoneIds().stream()
                .filter(zid -> {
                    if (zid.equalsIgnoreCase(finalNormalizedInput)) {
                        return true;
                    }
                    if (!finalNormalizedInput.contains("/") && zid.toLowerCase().endsWith("/" + finalNormalizedInput)) {
                        return true;
                    }
                    return false;
                })
                .findFirst();

        return foundZoneIdOpt
                .map(zid -> Map.of(
                        "status", "success",
                        "report", "The current time in " + cityOrZoneIdFromLlm + " is " +
                                ZonedDateTime.now(ZoneId.of(zid)).format(DateTimeFormatter.ofPattern("HH:mm")) + "."))
                .orElse(Map.of(
                        "status", "error",
                        "report", "Sorry, I don't have timezone information for " + cityOrZoneIdFromLlm + "."));
    }

    public static Map<String, String> getWeather(
            @Schema(description = "The name of the city for which to retrieve the weather report")
            String cityFromLlm) {

        if (OPENWEATHERMAP_API_KEY == null || OPENWEATHERMAP_API_KEY.isEmpty()) {
            return Map.of("status", "error", "report", "Weather service is not configured (API key missing).");
        }

        try {
            String encodedCity = URLEncoder.encode(cityFromLlm, StandardCharsets.UTF_8.toString());
            String apiUrl = String.format("%s?q=%s&appid=%s&units=metric", // Using metric for Celsius
                    OPENWEATHERMAP_API_URL_BASE, encodedCity, OPENWEATHERMAP_API_KEY);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject jsonResponse = new JSONObject(response.body());

                if (!jsonResponse.has("weather") || !jsonResponse.has("main")) {
                    return Map.of("status", "error", "report",
                            "Received an unexpected response from weather service for " + cityFromLlm + ".");
                }

                JSONArray weatherArray = jsonResponse.getJSONArray("weather");
                String description = "unknown";
                if (weatherArray.length() > 0) {
                    description = weatherArray.getJSONObject(0).getString("description");
                }

                JSONObject mainObject = jsonResponse.getJSONObject("main");
                double tempCelsius = mainObject.getDouble("temp");
                double feelsLikeCelsius = mainObject.getDouble("feels_like");
                int humidity = mainObject.getInt("humidity");

                double tempFahrenheit = (tempCelsius * 9 / 5) + 32;

                String report = String.format(
                        "The weather in %s: %s, with a temperature of %.1f°C (%.1f°F). " +
                                "Feels like %.1f°C. Humidity is %d%%.",
                        cityFromLlm,
                        description,
                        tempCelsius,
                        tempFahrenheit,
                        feelsLikeCelsius,
                        humidity);

                return Map.of("status", "success", "report", report);

            } else if (response.statusCode() == 401) {
                System.err.println("OpenWeatherMap API Error: Invalid API Key or unauthorized.");
                return Map.of("status", "error", "report", "Weather service authentication failed. Check API key.");
            } else if (response.statusCode() == 404) {
                return Map.of("status", "error", "report", "Sorry, I couldn't find weather information for " + cityFromLlm + ".");
            } else {
                System.err.println("OpenWeatherMap API Error: " + response.statusCode() + " - " + response.body());
                return Map.of("status", "error", "report", "Sorry, there was an issue fetching weather for " + cityFromLlm +
                        ". Service returned: " + response.statusCode());
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Error calling OpenWeatherMap API: " + e.getMessage());
            Thread.currentThread().interrupt(); // Restore interruption status
            return Map.of("status", "error", "report", "There was a network or interruption error while fetching weather data.");
        } catch (Exception e) { // Catch other potential errors like JSONException
            System.err.println("Error processing weather data: " + e.getMessage());
            e.printStackTrace();
            return Map.of("status", "error", "report", "There was an error processing the weather data for " + cityFromLlm + ".");
        }
    }

    public static void main(String[] args) throws Exception {
        InMemoryRunner runner = new InMemoryRunner(ROOT_AGENT);

        Session session =
                runner
                        .sessionService()
                        .createSession(NAME, USER_ID)
                        .blockingGet();

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            System.out.println("Agent initialized. Type 'quit' to exit.");
            System.out.println(
                    "Note: For weather, ensure OPENWEATHERMAP_API_KEY environment variable is set and org.json library is available.");
            while (true) {
                System.out.print("\nYou > ");
                String userInput = scanner.nextLine();

                if ("quit".equalsIgnoreCase(userInput)) {
                    break;
                }
                if (userInput.trim().isEmpty()) {
                    continue;
                }

                Content userMsg = Content.fromParts(Part.fromText(userInput));
                Flowable<Event> events = runner.runAsync(USER_ID, session.id(), userMsg);

                System.out.print("\nAgent > ");
                StringBuilder agentResponse = new StringBuilder();
                events.blockingForEach(event -> {
                    agentResponse.append(event.stringifyContent());
                });
                String response = agentResponse.toString().trim();
                if (response.isEmpty()) {
                    System.out.println("No response from agent.");
                } else {
                    response = response.replaceAll("Function Call:.*?\\}\\}", "");
                    response = response.replaceAll("FunctionCall:.*?\\]\\}", "");
                    response = response.replaceAll("report=", "");
                    response = response.replaceAll("\\{status=success, ", "");
                    response = response.replaceAll("\\}", "");

                    response = response.trim();

                    System.out.println(response);
                }
            }
        }
        System.out.println("Session ended.");
    }
}