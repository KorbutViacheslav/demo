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

    private final AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.defaultClient();
    private final DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setHeaders(Map.of("Content-Type", "application/json"));

        try {
            // Validate input
            if (event.getBody() == null || event.getBody().isEmpty()) {
                response.setStatusCode(400);
                response.setBody("{\"error\":\"Empty request body\"}");
                return response;
            }

            // Parse input
            Map<String, Object> requestBody = objectMapper.readValue(event.getBody(), Map.class);

            // Validate required fields
            if (!requestBody.containsKey("principalId") || !requestBody.containsKey("content")) {
                response.setStatusCode(400);
                response.setBody("{\"error\":\"Missing required fields: principalId or content\"}");
                return response;
            }

            int principalId = ((Number) requestBody.get("principalId")).intValue();
            @SuppressWarnings("unchecked")
            Map<String, String> content = (Map<String, String>) requestBody.get("content");

            // Create event
            String id = UUID.randomUUID().toString();
            String createdAt = Instant.now().toString();

            // Save to DynamoDB
            Table table = dynamoDB.getTable("${target_table}");
            Item item = new Item()
                    .withPrimaryKey("id", id)
                    .withNumber("principalId", principalId)
                    .withString("createdAt", createdAt)
                    .withMap("body", content);
            table.putItem(item);

            // Prepare response
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("id", id);
            eventData.put("principalId", principalId);
            eventData.put("createdAt", createdAt);
            eventData.put("body", content);

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("statusCode", 201);
            responseBody.put("event", eventData);

            response.setStatusCode(201);
            response.setBody(objectMapper.writeValueAsString(responseBody));
            return response;

        } catch (NumberFormatException e) {
            response.setStatusCode(400);
            response.setBody("{\"error\":\"principalId must be a number\"}");
            return response;
        } catch (ClassCastException e) {
            response.setStatusCode(400);
            response.setBody("{\"error\":\"content must be a key-value map\"}");
            return response;
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            response.setStatusCode(500);
            response.setBody("{\"error\":\"Internal server error\"}");
            return response;
        }
    }
}