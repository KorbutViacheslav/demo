package com.task11;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.AdminCreateUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminInitiateAuthRequest;
import com.amazonaws.services.cognitoidp.model.AdminInitiateAuthResult;
import com.amazonaws.services.cognitoidp.model.AdminSetUserPasswordRequest;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.AuthFlowType;
import com.amazonaws.services.cognitoidp.model.UsernameExistsException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.syndicate.deployment.model.environment.ValueTransformer.USER_POOL_NAME_TO_CLIENT_ID;
import static com.syndicate.deployment.model.environment.ValueTransformer.USER_POOL_NAME_TO_USER_POOL_ID;

@LambdaHandler(
        lambdaName = "api_handler",
        roleName = "api_handler-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@DependsOn(resourceType = ResourceType.COGNITO_USER_POOL, name = "${booking_userpool}")
@DependsOn(resourceType = ResourceType.DYNAMODB_TABLE, name = "${tables_table}")
@DependsOn(resourceType = ResourceType.DYNAMODB_TABLE, name = "${reservations_table}")
@EnvironmentVariables(value = {
        @EnvironmentVariable(key = "REGION", value = "${region}"),
        @EnvironmentVariable(key = "COGNITO_ID", value = "${booking_userpool}", valueTransformer = USER_POOL_NAME_TO_USER_POOL_ID),
        @EnvironmentVariable(key = "CLIENT_ID", value = "${booking_userpool}", valueTransformer = USER_POOL_NAME_TO_CLIENT_ID),
        @EnvironmentVariable(key = "TABLES_TABLE", value = "${tables_table}"),
        @EnvironmentVariable(key = "RESERVATIONS_TABLE", value = "${reservations_table}")
})
public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, RouteHandler> handlers = new HashMap<>();

    private AWSCognitoIdentityProvider cognitoClient;
    private DynamoDB dynamoDB;

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> request, Context context) {
        // Логування змінних середовища для перевірки
        context.getLogger().log("REGION: " + System.getenv("REGION"));
        context.getLogger().log("COGNITO_ID: " + System.getenv("COGNITO_ID"));
        context.getLogger().log("CLIENT_ID: " + System.getenv("CLIENT_ID"));
        context.getLogger().log("tablesTable: " + System.getenv("tablesTable"));
        context.getLogger().log("reservationsTable: " + System.getenv("reservationsTable"));

        // Ініціалізація сервісів
        initializeServices();

        // Ініціалізація обробників
        initializeHandlers(context);

        try {
            String resource = (String) request.get("resource");
            String httpMethod = (String) request.get("httpMethod");

            // Створення контексту запиту
            ApiRequestContext requestContext = new ApiRequestContext(
                    request,
                    parseBody((String) request.get("body")),
                    (Map<String, String>) request.get("pathParameters"),
                    (Map<String, String>) request.get("headers"),
                    context
            );

            // Пошук і виконання відповідного обробника
            String handlerKey = resource + ":" + httpMethod;
            RouteHandler handler = handlers.get(handlerKey);

            if (handler != null) {
                return handler.handle(requestContext);
            }

            return ResponseUtil.createResponse(400, "Невірний запит");
        } catch (Exception e) {
            return ResponseUtil.createResponse(400, "Помилка: " + e.getMessage());
        }
    }

    private void initializeServices() {
        String region = System.getenv("REGION");

        // Ініціалізація клієнта Cognito
        cognitoClient = AWSCognitoIdentityProviderClientBuilder.standard()
                .withRegion(region)
                .build();

        // Ініціалізація клієнта DynamoDB
        AmazonDynamoDB amazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withRegion(region)
                .build();
        dynamoDB = new DynamoDB(amazonDynamoDB);
    }

    private void initializeHandlers(Context context) {
        String cognitoId = System.getenv("COGNITO_ID");
        String clientId = System.getenv("CLIENT_ID");
        String tablesTableName = System.getenv("tablesTable");
        String reservationsTableName = System.getenv("reservationsTable");

        // Обробники авторизації
        AuthService authService = new AuthService(cognitoClient, cognitoId, clientId);
        handlers.put("/signup:POST", new SignupHandler(authService));
        handlers.put("/signin:POST", new SigninHandler(authService));

        // Обробники столів
        TableService tableService = new TableService(dynamoDB, tablesTableName);
        handlers.put("/tables:GET", new GetTablesHandler(tableService));
        handlers.put("/tables:POST", new CreateTableHandler(tableService));
        handlers.put("/tables/{tableId}:GET", new GetTableByIdHandler(tableService));

        // Обробники бронювань
        ReservationService reservationService = new ReservationService(dynamoDB, reservationsTableName, tablesTableName);
        handlers.put("/reservations:GET", new GetReservationsHandler(reservationService));
        handlers.put("/reservations:POST", new CreateReservationHandler(reservationService));
    }

    private Map<String, Object> parseBody(String body) throws JsonProcessingException {
        return body != null ? objectMapper.readValue(body, Map.class) : null;
    }
}
// Request context to pass around handler chain
class ApiRequestContext {
    private final Map<String, Object> request;
    private final Map<String, Object> body;
    private final Map<String, String> pathParams;
    private final Map<String, String> headers;
    private final Context lambdaContext;

    public ApiRequestContext(Map<String, Object> request, Map<String, Object> body,
                             Map<String, String> pathParams, Map<String, String> headers,
                             Context lambdaContext) {
        this.request = request;
        this.body = body;
        this.pathParams = pathParams;
        this.headers = headers;
        this.lambdaContext = lambdaContext;
    }

    public Map<String, Object> getRequest() {
        return request;
    }

    public Map<String, Object> getBody() {
        return body;
    }

    public Map<String, String> getPathParams() {
        return pathParams;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Context getLambdaContext() {
        return lambdaContext;
    }
}

// Interface for all route handlers
interface RouteHandler {
    Map<String, Object> handle(ApiRequestContext context);
}

// Utility methods for responses
class ResponseUtil {
    public static Map<String, Object> createResponse(int statusCode, Object body) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        response.put("body", body instanceof String ? body : toJson(body));
        response.put("headers", Map.of("Content-Type", "application/json"));
        return response;
    }

    private static String toJson(Object obj) {
        try {
            return new ObjectMapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON: " + e.getMessage());
        }
    }
}

// Auth Service and Handlers
class AuthService {
    private final AWSCognitoIdentityProvider cognitoClient;
    private final String cognitoId;
    private final String clientId;

    public AuthService(AWSCognitoIdentityProvider cognitoClient, String cognitoId, String clientId) {
        this.cognitoClient = cognitoClient;
        this.cognitoId = cognitoId;
        this.clientId = clientId;
    }

    public Map<String, Object> signup(String email, String password, String firstName, String lastName, Context context) {
        context.getLogger().log("Спроба реєстрації для email: " + email + ", firstName: " + firstName + ", lastName: " + lastName);
        context.getLogger().log("Використовується cognitoId: " + cognitoId);

        // Валідація вхідних даних
        if (!ValidationUtil.isValidEmail(email)) {
            context.getLogger().log("Невірний формат email: " + email);
            return ResponseUtil.createResponse(400, "Невірний формат email");
        }
        if (!ValidationUtil.isValidPassword(password)) {
            context.getLogger().log("Невірний формат пароля для email: " + email + ". Пароль має бути щонайменше 12 символів, із великими та малими літерами, цифрою та спеціальним символом.");
            return ResponseUtil.createResponse(400, "Невірний формат пароля");
        }
        if (firstName == null || lastName == null || firstName.trim().isEmpty() || lastName.trim().isEmpty()) {
            context.getLogger().log("Відсутнє або порожнє firstName чи lastName: firstName=" + firstName + ", lastName=" + lastName);
            return ResponseUtil.createResponse(400, "Відсутнє або порожнє firstName чи lastName");
        }

        try {
            context.getLogger().log("Створення користувача в Cognito для email: " + email);
            AdminCreateUserRequest createUserRequest = new AdminCreateUserRequest()
                    .withUserPoolId(cognitoId)
                    .withUsername(email)
                    .withUserAttributes(
                            new AttributeType().withName("email").withValue(email),
                            new AttributeType().withName("given_name").withValue(firstName),
                            new AttributeType().withName("family_name").withValue(lastName),
                            new AttributeType().withName("email_verified").withValue("true")
                    )
                    .withTemporaryPassword(password)
                    .withMessageAction("SUPPRESS");

            cognitoClient.adminCreateUser(createUserRequest);
            context.getLogger().log("Користувач успішно створений для email: " + email);

            context.getLogger().log("Встановлення постійного пароля для email: " + email);
            AdminSetUserPasswordRequest setPasswordRequest = new AdminSetUserPasswordRequest()
                    .withUserPoolId(cognitoId)
                    .withUsername(email)
                    .withPassword(password)
                    .withPermanent(true);

            cognitoClient.adminSetUserPassword(setPasswordRequest);
            context.getLogger().log("Пароль успішно встановлено для email: " + email);

            return ResponseUtil.createResponse(200, "Реєстрація успішна");
        } catch (UsernameExistsException e) {
            context.getLogger().log("Користувач уже існує, спроба оновити пароль для email: " + email);
            try {
                AdminSetUserPasswordRequest setPasswordRequest = new AdminSetUserPasswordRequest()
                        .withUserPoolId(cognitoId)
                        .withUsername(email)
                        .withPassword(password)
                        .withPermanent(true);
                cognitoClient.adminSetUserPassword(setPasswordRequest);
                context.getLogger().log("Пароль успішно оновлено для існуючого користувача: " + email);
                return ResponseUtil.createResponse(200, "Реєстрація успішна (користувач уже існував, пароль оновлено)");
            } catch (Exception ex) {
                context.getLogger().log("Не вдалося оновити пароль для існуючого користувача: " + ex.getMessage());
                return ResponseUtil.createResponse(400, "Не вдалося оновити пароль: " + ex.getMessage());
            }
        } catch (Exception e) {
            context.getLogger().log("Реєстрація не вдалася для email " + email + ": " + e.getMessage());
            return ResponseUtil.createResponse(400, "Реєстрація не вдалася: " + e.getMessage());
        }
    }

    public Map<String, Object> signin(String email, String password) {
        if (!ValidationUtil.isValidEmail(email) || !ValidationUtil.isValidPassword(password)) {
            return ResponseUtil.createResponse(400, "Невірні облікові дані");
        }

        AdminInitiateAuthRequest authRequest = new AdminInitiateAuthRequest()
                .withAuthFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                .withUserPoolId(cognitoId)
                .withClientId(clientId)
                .withAuthParameters(new HashMap<String, String>() {{
                    put("USERNAME", email);
                    put("PASSWORD", password);
                }});

        AdminInitiateAuthResult authResult = cognitoClient.adminInitiateAuth(authRequest);
        String idToken = authResult.getAuthenticationResult().getIdToken();

        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("idToken", idToken);

        return ResponseUtil.createResponse(200, responseBody);
    }
}

class SignupHandler implements RouteHandler {
    private final AuthService authService;

    public SignupHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public Map<String, Object> handle(ApiRequestContext context) {
        Map<String, Object> body = context.getBody();
        String email = (String) body.get("email");
        String password = (String) body.get("password");
        String firstName = (String) body.get("firstName");
        String lastName = (String) body.get("lastName");

        return authService.signup(email, password, firstName, lastName, context.getLambdaContext());
    }
}

class SigninHandler implements RouteHandler {
    private final AuthService authService;

    public SigninHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public Map<String, Object> handle(ApiRequestContext context) {
        Map<String, Object> body = context.getBody();
        String email = (String) body.get("email");
        String password = (String) body.get("password");

        return authService.signin(email, password);
    }
}

// Table Service and Handlers
class TableService {
    private final DynamoDB dynamoDB;
    private final String tablesTableName;

    public TableService(DynamoDB dynamoDB, String tablesTableName) {
        this.dynamoDB = dynamoDB;
        this.tablesTableName = tablesTableName;
    }

    public Map<String, Object> getAllTables() {
        Table table = dynamoDB.getTable(tablesTableName);
        List<Map<String, Object>> tables = new ArrayList<>();

        for (Item item : table.scan()) {
            Map<String, Object> tableData = new LinkedHashMap<>();
            tableData.put("id", Integer.parseInt(item.getString("id")));
            tableData.put("number", item.getInt("number"));
            tableData.put("places", item.getInt("places"));
            tableData.put("isVip", item.getBoolean("isVip"));
            if (item.isPresent("minOrder")) {
                tableData.put("minOrder", item.getInt("minOrder"));
            }
            tables.add(tableData);
        }

        tables.sort(Comparator.comparingInt(t -> (Integer) t.get("id")));

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("tables", tables);
        return ResponseUtil.createResponse(200, responseBody);
    }

    public Map<String, Object> createTable(Map<String, Object> tableData) {
        // Перевірка наявності обов'язкових полів
        if (!tableData.containsKey("id") || tableData.get("id") == null) {
            return ResponseUtil.createResponse(400, "Помилка: поле 'id' є обов'язковим і не може бути null");
        }
        if (!tableData.containsKey("number") || tableData.get("number") == null) {
            return ResponseUtil.createResponse(400, "Помилка: поле 'number' є обов'язковим і не може бути null");
        }
        if (!tableData.containsKey("places") || tableData.get("places") == null) {
            return ResponseUtil.createResponse(400, "Помилка: поле 'places' є обов'язковим і не може бути null");
        }
        if (!tableData.containsKey("isVip") || tableData.get("isVip") == null) {
            return ResponseUtil.createResponse(400, "Помилка: поле 'isVip' є обов'язковим і не може бути null");
        }

        Integer id;
        Integer number;
        Integer places;
        Boolean isVip;
        try {
            id = (Integer) tableData.get("id");
            number = (Integer) tableData.get("number");
            places = (Integer) tableData.get("places");
            isVip = (Boolean) tableData.get("isVip");
        } catch (ClassCastException e) {
            return ResponseUtil.createResponse(400, "Помилка: некоректний тип даних у вхідних полях (id, number, places, isVip)");
        }

        // Перевірка на поле 'name', яке не використовується
        if (tableData.containsKey("name")) {
            return ResponseUtil.createResponse(400, "Помилка: поле 'name' не підтримується. Використовуйте 'number' для номера столу");
        }

        String idString = String.valueOf(id);
        Table table = dynamoDB.getTable(tablesTableName);

        Item item = new Item()
                .withPrimaryKey("id", idString)
                .withInt("number", number)
                .withInt("places", places)
                .withBoolean("isVip", isVip);

        if (tableData.containsKey("minOrder")) {
            try {
                item.withInt("minOrder", (Integer) tableData.get("minOrder"));
            } catch (ClassCastException e) {
                return ResponseUtil.createResponse(400, "Помилка: поле 'minOrder' має бути цілим числом");
            }
        }

        table.putItem(item);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("id", id);
        return ResponseUtil.createResponse(200, responseBody);
    }

    public Map<String, Object> getTableById(String tableId, Context context) {
        context.getLogger().log("Retrieving table with id: " + tableId);

        Table table = dynamoDB.getTable(tablesTableName);
        Item item = table.getItem(new GetItemSpec().withPrimaryKey("id", tableId));

        if (item == null) {
            return ResponseUtil.createResponse(400, "Table not found");
        }

        Map<String, Object> tableData = new LinkedHashMap<>();
        tableData.put("id", Integer.parseInt(item.getString("id")));
        tableData.put("number", item.getInt("number"));
        tableData.put("places", item.getInt("places"));
        tableData.put("isVip", item.getBoolean("isVip"));
        if (item.isPresent("minOrder")) {
            tableData.put("minOrder", item.getInt("minOrder"));
        }

        return ResponseUtil.createResponse(200, tableData);
    }
}

class GetTablesHandler implements RouteHandler {
    private final TableService tableService;

    public GetTablesHandler(TableService tableService) {
        this.tableService = tableService;
    }

    @Override
    public Map<String, Object> handle(ApiRequestContext context) {
        return tableService.getAllTables();
    }
}

class CreateTableHandler implements RouteHandler {
    private final TableService tableService;

    public CreateTableHandler(TableService tableService) {
        this.tableService = tableService;
    }

    @Override
    public Map<String, Object> handle(ApiRequestContext context) {
        return tableService.createTable(context.getBody());
    }
}

class GetTableByIdHandler implements RouteHandler {
    private final TableService tableService;

    public GetTableByIdHandler(TableService tableService) {
        this.tableService = tableService;
    }

    @Override
    public Map<String, Object> handle(ApiRequestContext context) {
        String tableId = context.getPathParams().get("tableId");
        return tableService.getTableById(tableId, context.getLambdaContext());
    }
}

// Reservation Service and Handlers
class ReservationService {
    private final DynamoDB dynamoDB;
    private final String reservationsTableName;
    private final String tablesTableName;

    public ReservationService(DynamoDB dynamoDB, String reservationsTableName, String tablesTableName) {
        this.dynamoDB = dynamoDB;
        this.reservationsTableName = reservationsTableName;
        this.tablesTableName = tablesTableName;
    }

    public Map<String, Object> getAllReservations() {
        Table table = dynamoDB.getTable(reservationsTableName);
        List<Map<String, Object>> reservations = new ArrayList<>();

        for (Item item : table.scan()) {
            reservations.add(item.asMap());
        }

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("reservations", reservations);
        return ResponseUtil.createResponse(200, responseBody);
    }

    public Map<String, Object> createReservation(Map<String, Object> reservationData) {
        // Verify table exists
        Table tablesTable = dynamoDB.getTable(tablesTableName);
        Integer tableNumber = (Integer) reservationData.get("tableNumber");
        boolean tableExists = false;

        for (Item item : tablesTable.scan()) {
            if (item.getInt("number") == tableNumber) {
                tableExists = true;
                break;
            }
        }

        if (!tableExists) {
            return ResponseUtil.createResponse(400, "Table not found");
        }

        String date = (String) reservationData.get("date");
        String newStart = (String) reservationData.get("slotTimeStart");
        String newEnd = (String) reservationData.get("slotTimeEnd");

        // Check for overlapping reservations
        Table reservationsTable = dynamoDB.getTable(reservationsTableName);
        for (Item existing : reservationsTable.scan()) {
            if (existing.getInt("tableNumber") == tableNumber && existing.getString("date").equals(date)) {
                String existingStart = existing.getString("slotTimeStart");
                String existingEnd = existing.getString("slotTimeEnd");

                if (isOverlapping(newStart, newEnd, existingStart, existingEnd)) {
                    return ResponseUtil.createResponse(400, "Reservation overlaps with an existing reservation");
                }
            }
        }

        // Create new reservation
        String reservationId = UUID.randomUUID().toString();
        Item reservation = new Item()
                .withPrimaryKey("id", reservationId)
                .withInt("tableNumber", tableNumber)
                .withString("clientName", (String) reservationData.get("clientName"))
                .withString("phoneNumber", (String) reservationData.get("phoneNumber"))
                .withString("date", date)
                .withString("slotTimeStart", newStart)
                .withString("slotTimeEnd", newEnd);

        reservationsTable.putItem(reservation);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("reservationId", reservationId);
        return ResponseUtil.createResponse(200, responseBody);
    }

    private boolean isOverlapping(String newStart, String newEnd, String existingStart, String existingEnd) {
        int newStartMins = timeToMinutes(newStart);
        int newEndMins = timeToMinutes(newEnd);
        int existingStartMins = timeToMinutes(existingStart);
        int existingEndMins = timeToMinutes(existingEnd);

        return newStartMins < existingEndMins && newEndMins > existingStartMins;
    }

    private int timeToMinutes(String time) {
        String[] parts = time.split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        return hours * 60 + minutes;
    }
}

class GetReservationsHandler implements RouteHandler {
    private final ReservationService reservationService;

    public GetReservationsHandler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Override
    public Map<String, Object> handle(ApiRequestContext context) {
        return reservationService.getAllReservations();
    }
}

class CreateReservationHandler implements RouteHandler {
    private final ReservationService reservationService;

    public CreateReservationHandler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Override
    public Map<String, Object> handle(ApiRequestContext context) {
        return reservationService.createReservation(context.getBody());
    }
}

// Validation Utility
class ValidationUtil {
    public static boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 12 &&
                password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[$%^*-_])[A-Za-z\\d$%^*-_]+$");
    }
}