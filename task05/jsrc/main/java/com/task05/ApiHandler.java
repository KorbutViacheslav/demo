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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TABLE_NAME = "${target_table}";

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            context.getLogger().log("Received event: " + event.getBody());

            if (event.getBody() == null || event.getBody().isEmpty()) {
                return createErrorResponse(400, "Empty request body", context);
            }

            Map<String, Object> requestBody;
            try {
                requestBody = objectMapper.readValue(event.getBody(), Map.class);
            } catch (Exception e) {
                return createErrorResponse(400, "Invalid JSON: " + e.getMessage(), context);
            }

            if (!requestBody.containsKey("principalId") || !requestBody.containsKey("content")) {
                return createErrorResponse(400, "Missing required fields: principalId or content", context);
            }

            Integer principalId;
            try {
                principalId = Integer.parseInt(requestBody.get("principalId").toString());
            } catch (NumberFormatException e) {
                return createErrorResponse(400, "principalId must be an integer", context);
            }

            Map<String, String> content;
            try {
                content = (Map<String, String>) requestBody.get("content");
            } catch (ClassCastException e) {
                return createErrorResponse(400, "content must be a map", context);
            }

            String id = UUID.randomUUID().toString();
            String createdAt = Instant.now().toString();

            Table table = dynamoDB.getTable(TABLE_NAME);
            try {
                Item item = new Item()
                        .withPrimaryKey("id", id)
                        .withNumber("principalId", principalId)
                        .withString("createdAt", createdAt)
                        .withMap("body", content);
                table.putItem(item);
                context.getLogger().log("Successfully saved item with id: " + id);
            } catch (Exception e) {
                context.getLogger().log("DynamoDB error: " + e.getMessage());
                return createErrorResponse(500, "Failed to save to DynamoDB: " + e.getMessage(), context);
            }

            Map<String, Object> responseEvent = new HashMap<>();
            responseEvent.put("id", id);
            responseEvent.put("principalId", principalId);
            responseEvent.put("createdAt", createdAt);
            responseEvent.put("body", content);

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("statusCode", 201);
            responseBody.put("event", responseEvent);

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(201)
                    .withBody(objectMapper.writeValueAsString(responseBody))
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .build();

        } catch (Exception e) {
            context.getLogger().log("Unexpected error: " + e.getMessage());
            return createErrorResponse(500, "Internal server error: " + e.getMessage(), context);
        }
    }

    private APIGatewayV2HTTPResponse createErrorResponse(int statusCode, String message, Context context) {
        try {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("statusCode", statusCode);
            errorBody.put("error", message);

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(statusCode)
                    .withBody(objectMapper.writeValueAsString(errorBody))
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .build();
        } catch (Exception e) {
            context.getLogger().log("Error creating error response: " + e.getMessage());
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(500)
                    .withBody("{\"statusCode\": 500, \"error\": \"Internal server error\"}")
                    .build();
        }
    }
}