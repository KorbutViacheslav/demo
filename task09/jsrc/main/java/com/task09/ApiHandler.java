package com.task09;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.ArtifactExtension;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@LambdaHandler(
        lambdaName = "api_handler",
        roleName = "api_handler-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaLayer(
        layerName = "weather_sdk",
        libraries = {"lib/weather-sdk-1.0.0.jar"},
        runtime = DeploymentRuntime.JAVA11,
        artifactExtension = ArtifactExtension.ZIP
)
@LambdaUrlConfig(authType = AuthType.NONE, invokeMode = InvokeMode.BUFFERED)
public class ApiHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private static final String BASE_URL = "https://api.open-meteo.com/v1/forecast";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String path = event.getRawPath();
        String method = event.getRequestContext().getHttp().getMethod();

        if ("/weather".equals(path) && "GET".equalsIgnoreCase(method)) {
            return handleWeatherRequest();
        } else {
            return createErrorResponse(400, String.format(
                    "Bad request syntax or unsupported method. Request path: %s. HTTP method: %s", path, method
            ));
        }
    }

    private APIGatewayV2HTTPResponse handleWeatherRequest() {
        try {
            double latitude = 50.4375;
            double longitude = 30.5;
            String weatherData = getWeather(latitude, longitude);
            return createResponse(200, weatherData);
        } catch (IOException | InterruptedException e) {
            return createErrorResponse(500, "Failed to fetch weather data");
        }
    }

    private String getWeather(double latitude, double longitude) throws IOException, InterruptedException {
        Map<String, String> params = new HashMap<>();
        params.put("latitude", String.format("%.4f", latitude));
        params.put("longitude", String.format("%.4f", longitude));
        params.put("current", "temperature_2m,wind_speed_10m");
        params.put("hourly", "temperature_2m,relative_humidity_2m,wind_speed_10m");

        String url = buildUrl(BASE_URL, params);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String buildUrl(String baseUrl, Map<String, String> params) {
        String encodedParams = params.entrySet().stream()
                .map(e -> encodeUrlParameter(e.getKey()) + "=" + encodeUrlParameter(e.getValue()))
                .collect(Collectors.joining("&"));
        return baseUrl + "?" + encodedParams;
    }

    private String encodeUrlParameter(String param) {
        return URLEncoder.encode(param, StandardCharsets.UTF_8);
    }

    private APIGatewayV2HTTPResponse createResponse(int statusCode, String body) {
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setStatusCode(statusCode);
        response.setBody(body);
        return response;
    }

    private APIGatewayV2HTTPResponse createErrorResponse(int statusCode, String message) {
        return createResponse(statusCode, String.format("{\"statusCode\": %d, \"message\": \"%s\"}", statusCode, message));
    }
}