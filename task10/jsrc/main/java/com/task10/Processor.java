package com.task10;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.TracingMode;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
        lambdaName = "processor",
        roleName = "processor-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
        tracingMode = TracingMode.Active
)
@LambdaUrlConfig(
        authType = AuthType.NONE,
        invokeMode = InvokeMode.BUFFERED
)
@EnvironmentVariables(value = {@EnvironmentVariable(key = "table", value = "${target_table}")})
public class Processor implements RequestHandler<Object, Map<String, Object>> {

    private static final double DEFAULT_LATITUDE = 50.4375;
    private static final double DEFAULT_LONGITUDE = 30.5;

    private final AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.standard().build();
    private final OpenMeteo weatherClient = new OpenMeteo();
    private final WeatherDataMapper dataMapper = new WeatherDataMapper();

    @Override
    public Map<String, Object> handleRequest(Object request, Context context) {
        try {
            JsonNode weatherData = weatherClient.getWeather(DEFAULT_LATITUDE, DEFAULT_LONGITUDE);
            Map<String, AttributeValue> item = dataMapper.mapToDynamoDBItem(weatherData);

            String tableName = System.getenv("table");
            dynamoDB.putItem(tableName, item);

            return buildSuccessResponse();
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return buildErrorResponse(e.getMessage());
        }
    }

    private Map<String, Object> buildSuccessResponse() {
        Map<String, Object> result = new HashMap<>();
        result.put("statusCode", 200);
        result.put("body", "Weather forecast successfully saved");
        return result;
    }

    private Map<String, Object> buildErrorResponse(String errorMessage) {
        Map<String, Object> error = new HashMap<>();
        error.put("statusCode", 500);
        error.put("body", "Error processing weather data: " + errorMessage);
        return error;
    }
}