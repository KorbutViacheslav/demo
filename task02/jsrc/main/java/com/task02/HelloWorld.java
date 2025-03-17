package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
        lambdaName = "hello_world",
        roleName = "hello_world-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
public class HelloWorld implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> request, Context context) {
        String path = (String) request.getOrDefault("rawPath", "");
        Map<String, Object> requestContext = (Map<String, Object>) request.get("requestContext");
        String method = requestContext != null ? (String) ((Map<String, Object>) requestContext.get("http")).get("method") : "";

        if ("/hello".equals(path) && "GET".equalsIgnoreCase(method)) {
            return createResponse(200, "Hello from Lambda");
        }

        return createResponse(400, "Bad request syntax or unsupported method. Request path: " + path + ". HTTP method: " + method);
    }

    private Map<String, Object> createResponse(int statusCode, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        response.put("message", message);
        return response;
    }
}
