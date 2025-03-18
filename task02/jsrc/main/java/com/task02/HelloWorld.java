package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;

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

        String requestPath = request.getPath();
        String httpMethod = request.getHttpMethod();

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        if ("GET".equalsIgnoreCase(httpMethod) && "/hello".equals(requestPath)) {
            response.setStatusCode(200);
            response.setBody("{\"statusCode\": 200, \"message\": \"Hello from Lambda\"}");
        } else {

            String errorMessage = String.format(
                    "{\"statusCode\": 400, \"message\": \"Bad request syntax or unsupported method. Request path: %s. HTTP method: %s\"}",
                    requestPath, httpMethod
            );
            response.setStatusCode(400);
            response.setBody(errorMessage);
        }

        return response;
    }
}
