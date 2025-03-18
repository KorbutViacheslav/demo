package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
        lambdaName = "hello_world",
        roleName = "hello_world-role",
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(
        authType = AuthType.NONE,
        invokeMode = InvokeMode.BUFFERED
)
public class HelloWorld implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final String HELLO_PATH = "/hello";
    private static final String GET_METHOD = "GET";

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String path = event.getRawPath() != null ? event.getRawPath() : "";
        String method = (event.getRequestContext() != null && event.getRequestContext().getHttp() != null)
                ? event.getRequestContext().getHttp().getMethod()
                : "";

        if (HELLO_PATH.equals(path) && GET_METHOD.equalsIgnoreCase(method)) {
            return createResponse(200, "Hello from Lambda");
        } else {
            return createResponse(400, String.format(
                    "Bad request syntax or unsupported method. Request path: %s. HTTP method: %s", path, method
            ));
        }
    }

    private APIGatewayV2HTTPResponse createResponse(int statusCode, String message) {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("statusCode", statusCode);
        responseBody.put("message", message);

        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(statusCode)
                .withBody(toJson(responseBody))
                .build();
    }

    private String toJson(Map<String, Object> data) {
        return String.format(
                "{\"statusCode\": %d, \"message\": \"%s\"}",
                (Integer) data.get("statusCode"),
                data.get("message")
        );
    }
}
