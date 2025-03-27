package com.task05;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
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
@DependsOn(name = "Events", resourceType = ResourceType.DYNAMODB_TABLE)
@EnvironmentVariables(value = {
        @EnvironmentVariable(key = "region", value = "${region}"),
        @EnvironmentVariable(key = "table", value = "${target_table}")})
public class ApiHandler implements RequestHandler<Map<String, Object>, APIGatewayV2HTTPResponse> {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.defaultClient();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(Map<String, Object> event, Context context) {
        LambdaLogger logger = context.getLogger();
        logger.log("Received event: " + event);

        try {
            Map<String, Object> body = parseRequestBody(event, logger);

            int principalId = extractPrincipalId(body);
            Map<String, String> content = extractContent(body);

            Map<String, AttributeValue> item = createEventItem(principalId, content);

            saveToDatabase(item, logger);

            return createSuccessResponse(item);

        } catch (Exception e) {
            logger.log("Error in processing request: " + e.getMessage());
            return createErrorResponse();
        }
    }

    private Map<String, Object> parseRequestBody(Map<String, Object> event, LambdaLogger logger) throws Exception {
        String bodyString = (String) event.get("body");
        if (bodyString == null) {
            throw new IllegalArgumentException("Missing request body");
        }
        logger.log("Request body: " + bodyString);
        return objectMapper.readValue(bodyString, Map.class);
    }

    private int extractPrincipalId(Map<String, Object> body) {
        Object principalIdObject = body.get("principalId");
        if (principalIdObject == null) {
            throw new IllegalArgumentException("Missing required field: principalId");
        }
        return (principalIdObject instanceof Number)
                ? ((Number) principalIdObject).intValue()
                : Integer.parseInt(principalIdObject.toString());
    }

    private Map<String, String> extractContent(Map<String, Object> body) {
        Object contentObject = body.get("content");
        if (contentObject == null) {
            throw new IllegalArgumentException("Missing required field: content");
        }
        return objectMapper.convertValue(contentObject, Map.class);
    }

    private Map<String, AttributeValue> createEventItem(int principalId, Map<String, String> content) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", new AttributeValue(UUID.randomUUID().toString()));
        item.put("principalId", new AttributeValue().withN(String.valueOf(principalId)));
        item.put("createdAt", new AttributeValue(Instant.now().toString()));
        item.put("body", new AttributeValue().withM(convertToAttributeValueMap(content)));
        return item;
    }

    private Map<String, AttributeValue> convertToAttributeValueMap(Map<String, String> map) {
        Map<String, AttributeValue> attributeValueMap = new HashMap<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            attributeValueMap.put(entry.getKey(), new AttributeValue(entry.getValue()));
        }
        return attributeValueMap;
    }

    private void saveToDatabase(Map<String, AttributeValue> item, LambdaLogger logger) {
        PutItemRequest putItemRequest = new PutItemRequest()
                .withTableName(System.getenv("table"))
                .withItem(item);
        dynamoDB.putItem(putItemRequest);
        logger.log("Item saved to DynamoDB");
    }

    private APIGatewayV2HTTPResponse createSuccessResponse(Map<String, AttributeValue> item) throws Exception {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("statusCode", 201);
        responseBody.put("event", convertFromAttributeValueMap(item));

        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(201)
                .withBody(objectMapper.writeValueAsString(responseBody))
                .withHeaders(Map.of("Content-Type", "application/json"))
                .build();
    }

    private Map<String, Object> convertFromAttributeValueMap(Map<String, AttributeValue> item) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, AttributeValue> entry : item.entrySet()) {
            AttributeValue value = entry.getValue();
            if (value.getS() != null) {
                result.put(entry.getKey(), value.getS());
            } else if (value.getN() != null) {
                result.put(entry.getKey(), Integer.parseInt(value.getN()));
            } else if (value.getM() != null) {
                result.put(entry.getKey(), convertFromAttributeValueMap(value.getM()));
            }
        }
        return result;
    }

    private APIGatewayV2HTTPResponse createErrorResponse() {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(500)
                .withBody("{\"message\": \"Internal server error\"}")
                .withHeaders(Map.of("Content-Type", "application/json"))
                .build();
    }
}
