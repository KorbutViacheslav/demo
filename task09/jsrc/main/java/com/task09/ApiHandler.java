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

    private final OpenMeteo weatherClient = new OpenMeteo();
    private static final double DEFAULT_LATITUDE = 50.4375;
    private static final double DEFAULT_LONGITUDE = 30.5;

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String path = event.getRawPath();
        String method = event.getRequestContext().getHttp().getMethod();

        if ("/weather".equals(path) && "GET".equalsIgnoreCase(method)) {
            return handleWeatherRequest();
        }
        return buildBadRequestResponse(path, method);
    }

    private APIGatewayV2HTTPResponse handleWeatherRequest() {
        try {
            String weatherData = weatherClient.getWeather(DEFAULT_LATITUDE, DEFAULT_LONGITUDE);
            return buildResponse(200, weatherData);
        } catch (IOException | InterruptedException e) {
            return buildResponse(500, "{\"error\": \"Failed to fetch weather data\"}");
        }
    }

    private APIGatewayV2HTTPResponse buildBadRequestResponse(String path, String method) {
        String body = String.format("{\"statusCode\": 400, \"message\": \"Bad request. Path: %s, Method: %s\"}", path, method);
        return buildResponse(400, body);
    }

    private APIGatewayV2HTTPResponse buildResponse(int statusCode, String body) {
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setStatusCode(statusCode);
        response.setBody(body);
        return response;
    }
}