package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
        lambdaName = "hello_world",
        roleName = "hello_world-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
public class HelloWorld implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        if ("/hello".equals(path) && "GET".equalsIgnoreCase(httpMethod)) {
            Map<String, String> responseBody = new HashMap<>();
            responseBody.put("message", "Hello from Lambda");

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody("{\"statusCode\": 200, \"message\": \"Hello from Lambda\"}");
        } else {
            String errorMessage = String.format("Bad request syntax or unsupported method. Request path: %s. HTTP method: %s", path, httpMethod);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody("{\"statusCode\": 400, \"message\": \"" + errorMessage + "\"}");
        }
    }
}
