package com.task10;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class WeatherDataMapper {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, AttributeValue> mapToDynamoDBItem(JsonNode weatherData) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", new AttributeValue(UUID.randomUUID().toString()));
        item.put("forecast", new AttributeValue().withM(buildForecast(weatherData)));
        return item;
    }

    private Map<String, AttributeValue> buildForecast(JsonNode weatherData) {
        Map<String, AttributeValue> forecast = new HashMap<>();
        if (weatherData == null) {
            return forecast;
        }

        forecast.put("elevation", new AttributeValue().withN(String.valueOf(weatherData.get("elevation") != null ? weatherData.get("elevation").asDouble() : 0.0)));
        forecast.put("generationtime_ms", new AttributeValue().withN(String.valueOf(weatherData.get("generationtime_ms") != null ? weatherData.get("generationtime_ms").asDouble() : 0.0)));
        forecast.put("latitude", new AttributeValue().withN(String.valueOf(weatherData.get("latitude") != null ? weatherData.get("latitude").asDouble() : 0.0)));
        forecast.put("longitude", new AttributeValue().withN(String.valueOf(weatherData.get("longitude") != null ? weatherData.get("longitude").asDouble() : 0.0)));
        forecast.put("timezone", new AttributeValue(weatherData.get("timezone") != null ? weatherData.get("timezone").asText() : ""));
        forecast.put("timezone_abbreviation", new AttributeValue(weatherData.get("timezone_abbreviation") != null ? weatherData.get("timezone_abbreviation").asText() : ""));
        forecast.put("utc_offset_seconds", new AttributeValue().withN(String.valueOf(weatherData.get("utc_offset_seconds") != null ? weatherData.get("utc_offset_seconds").asInt() : 0)));

        JsonNode hourlyData = weatherData.get("hourly");
        if (hourlyData != null) {
            forecast.put("hourly", new AttributeValue().withM(buildHourly(hourlyData)));
        }

        JsonNode hourlyUnitsData = weatherData.get("hourly_units");
        if (hourlyUnitsData != null) {
            forecast.put("hourly_units", new AttributeValue().withM(buildHourlyUnits(hourlyUnitsData)));
        }

        return forecast;
    }

    private Map<String, AttributeValue> buildHourly(JsonNode hourlyData) {
        Map<String, AttributeValue> hourly = new HashMap<>();
        hourly.put("temperature_2m", new AttributeValue().withL(
                Arrays.stream(objectMapper.convertValue(hourlyData.get("temperature_2m"), double[].class))
                        .mapToObj(d -> new AttributeValue().withN(String.valueOf(d)))
                        .collect(Collectors.toList())
        ));
        hourly.put("time", new AttributeValue().withL(
                Arrays.stream(objectMapper.convertValue(hourlyData.get("time"), String[].class))
                        .map(AttributeValue::new)
                        .collect(Collectors.toList())
        ));
        return hourly;
    }

    private Map<String, AttributeValue> buildHourlyUnits(JsonNode unitsNode) {
        Map<String, AttributeValue> hourlyUnits = new HashMap<>();
        hourlyUnits.put("temperature_2m", new AttributeValue(unitsNode.get("temperature_2m") != null ? unitsNode.get("temperature_2m").asText() : ""));
        hourlyUnits.put("time", new AttributeValue(unitsNode.get("time") != null ? unitsNode.get("time").asText() : ""));
        return hourlyUnits;
    }
}