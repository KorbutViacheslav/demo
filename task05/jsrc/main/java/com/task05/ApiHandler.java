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
import java.time.format.DateTimeFormatter;
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
    private final String tableName = "cmtr-a81b9485-Events-hrwn";

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            context.getLogger().log("Received event: " + event.getBody());

            Map<String, Object> requestBody = objectMapper.readValue(event.getBody(), Map.class);

            // Validate input
            if (!requestBody.containsKey("principalId") || !requestBody.containsKey("content")) {
                return APIGatewayV2HTTPResponse.builder()
                        .withStatusCode(400)
                        .withBody("{\"error\": \"Missing required fields\"}")
                        .build();
            }

            int principalId = (int) requestBody.get("principalId");
            Map<String, String> content = (Map<String, String>) requestBody.get("content");

            String id = UUID.randomUUID().toString();
            String createdAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

            Table table = dynamoDB.getTable(tableName);
            Item item = new Item()
                    .withPrimaryKey("id", id)
                    .withNumber("principalId", principalId)
                    .withString("createdAt", createdAt)
                    .withMap("body", content);
            table.putItem(item);

            Map<String, Object> responseEvent = new HashMap<>();
            responseEvent.put("id", id);
            responseEvent.put("principalId", principalId);
            responseEvent.put("createdAt", createdAt);
            responseEvent.put("body", content);

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("statusCode", 201);
            responseBody.put("event", responseEvent);

            String jsonResponse = objectMapper.writeValueAsString(responseBody);
            context.getLogger().log("Response: " + jsonResponse);

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(201)
                    .withBody(jsonResponse)
                    .build();

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            e.printStackTrace();
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }
}