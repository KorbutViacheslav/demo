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
import ua.client.OpenMeteo;

import java.io.IOException;

@LambdaHandler(
        lambdaName = "api_handler",
        roleName = "api_handler-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
        layers = {"weather_sdk"}
)
@LambdaLayer(
        layerName = "weather_sdk",
        libraries = {"lib/weather-sdk-1.0.0.jar"},
        runtime = DeploymentRuntime.JAVA11,
        artifactExtension = ArtifactExtension.ZIP
)
@LambdaUrlConfig(
        authType = AuthType.NONE,
        invokeMode = InvokeMode.BUFFERED
)
public class ApiHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private static final String WEATHER_PATH = "/weather";
    private static final String GET_METHOD = "GET";
    private static final double DEFAULT_LATITUDE = 50.4375;
    private static final double DEFAULT_LONGITUDE = 30.5;

    private final OpenMeteo weatherClient = new OpenMeteo();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String path = event.getRawPath();
        String method = event.getRequestContext().getHttp().getMethod();

        if (isValidWeatherRequest(path, method)) {
            return handleWeatherRequest();
        }
        return createErrorResponse(400,
                String.format("Bad request syntax or unsupported method. Request path: %s. HTTP method: %s",
                        path, method));
    }

    private boolean isValidWeatherRequest(String path, String method) {
        return WEATHER_PATH.equals(path) && GET_METHOD.equalsIgnoreCase(method);
    }

    private APIGatewayV2HTTPResponse handleWeatherRequest() {
        try {
            String weatherData = weatherClient.getWeather(DEFAULT_LATITUDE, DEFAULT_LONGITUDE);
            return createSuccessResponse(weatherData);
        } catch (IOException | InterruptedException e) {
            return createErrorResponse(500, "Failed to fetch weather data");
        }
    }

    private APIGatewayV2HTTPResponse createSuccessResponse(String body) {
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setStatusCode(200);
        response.setBody(body);
        response.getHeaders().put("Content-Type", "application/json");
        return response;
    }

    private APIGatewayV2HTTPResponse createErrorResponse(int statusCode, String message) {
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setStatusCode(statusCode);
        response.setBody(String.format("{\"statusCode\": %d, \"message\": \"%s\"}",
                statusCode, message));
        response.getHeaders().put("Content-Type", "application/json");
        return response;
    }
}