package com.task08;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.events.RuleEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@LambdaHandler(
        lambdaName = "uuid_generator",
        roleName = "uuid_generator-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@RuleEventSource(targetRule = "uuid_trigger")
@DependsOn(name = "uuid_trigger", resourceType = ResourceType.CLOUDWATCH_RULE)
@EnvironmentVariable(key = "target_bucket", value = "${target_bucket}")
public class UuidGenerator implements RequestHandler<ScheduledEvent, String> {

    private static final String BUCKET_NAME = System.getenv("target_bucket");
    private static final AmazonS3 S3_CLIENT = AmazonS3ClientBuilder.defaultClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SUCCESS = "SUCCESS";
    private static final String ERROR = "ERROR";

    @Override
    public String handleRequest(ScheduledEvent event, Context context) {
        LambdaLogger logger = context.getLogger();

        try {
            List<String> uuids = Stream.generate(UUID::randomUUID)
                    .limit(10)
                    .map(UUID::toString)
                    .collect(Collectors.toList());

            String fileName = Instant.now().toString();
            String jsonContent = MAPPER.writeValueAsString(Map.of("ids", uuids));

            S3_CLIENT.putObject(BUCKET_NAME, fileName, jsonContent);
            logger.log("Uploaded file: " + fileName);
            return SUCCESS;

        } catch (Exception e) {
            logger.log("Error: " + e.getMessage());
            return ERROR;
        }
    }
}