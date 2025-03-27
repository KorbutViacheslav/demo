package com.task05;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
        lambdaName = "api_handler",
        roleName = "api_handler-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(
        authType = AuthType.NONE,
        invokeMode = InvokeMode.BUFFERED
)
public class ApiHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
    private final DynamoDB dynamoDB = new DynamoDB(client);
    private final Table table = dynamoDB.getTable("Events");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            // Парсимо вхідний JSON
            Map<String, Object> requestBody = objectMapper.readValue(event.getBody(), Map.class);

            // Отримуємо principalId та content з запиту
            Integer principalId = (Integer) requestBody.get("principalId");
            Map<String, String> content = (Map<String, String>) requestBody.get("content");

            // Генеруємо UUID та поточну дату
            String id = UUID.randomUUID().toString();
            String createdAt = Instant.now().toString();

            // Створюємо об'єкт події
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("id", id);
            eventData.put("principalId", principalId);
            eventData.put("createdAt", createdAt);
            eventData.put("body", content);

            // Зберігаємо в DynamoDB
            Item item = new Item()
                    .withPrimaryKey("id", id)
                    .withInt("principalId", principalId)
                    .withString("createdAt", createdAt)
                    .withMap("body", content);
            table.putItem(item);

            // Формуємо відповідь
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("statusCode", 201);
            responseBody.put("event", eventData);

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(201)
                    .withBody(objectMapper.writeValueAsString(responseBody))
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .build();

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(500)
                    .withBody("{\"statusCode\": 500, \"message\": \"Internal Server Error\"}")
                    .build();
        }
    }
}