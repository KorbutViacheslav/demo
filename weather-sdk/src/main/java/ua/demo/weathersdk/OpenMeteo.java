package ua.demo.weathersdk;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class OpenMeteo {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final String URL_TEMPLATE = "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,wind_speed_10m&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m";

    public String getWeather(double latitude, double longitude)
            throws IOException, InterruptedException {
        String url = String.format(URL_TEMPLATE, latitude, longitude);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }
}