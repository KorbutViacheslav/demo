package com.task06;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
        lambdaName = "audit_producer",
        roleName = "audit_producer-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@DynamoDbTriggerEventSource(targetTable = "Configuration", batchSize = 1)
@EnvironmentVariables(value = {
        @EnvironmentVariable(key = "region", value = "${region}"),
        @EnvironmentVariable(key = "table", value = "${target_table}")
})
public class AuditProducer implements RequestHandler<DynamodbEvent, Void> {

    private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
    private final DynamoDB dynamoDB = new DynamoDB(client);
    private final Table auditTable = dynamoDB.getTable(System.getenv("table"));

    @Override
    public Void handleRequest(DynamodbEvent event, Context context) {
        for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {
            processRecord(record, context);
        }
        return null;
    }

    private void processRecord(DynamodbEvent.DynamodbStreamRecord record, Context context) {
        String eventName = record.getEventName();
        String itemKey = record.getDynamodb().getKeys().get("key").getS();
        String modificationTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

        context.getLogger().log("Processing record: " + record.toString());

        Item auditItem = new Item()
                .withPrimaryKey("id", UUID.randomUUID().toString())
                .withString("itemKey", itemKey)
                .withString("modificationTime", modificationTime);

        if ("INSERT".equals(eventName)) {
            Map<String, Object> newValue = new HashMap<>();
            newValue.put("key", itemKey);
            newValue.put("value", Integer.parseInt(record.getDynamodb().getNewImage().get("value").getN()));
            auditItem.withMap("newValue", newValue);
        } else if ("MODIFY".equals(eventName)) {
            int oldValue = Integer.parseInt(record.getDynamodb().getOldImage().get("value").getN());
            int newValue = Integer.parseInt(record.getDynamodb().getNewImage().get("value").getN());
            auditItem.withInt("oldValue", oldValue)
                    .withInt("newValue", newValue)
                    .withString("updatedAttribute", "value");
        }

        context.getLogger().log("Saving audit item: " + auditItem.toJSON());
        auditTable.putItem(auditItem);
    }
}
