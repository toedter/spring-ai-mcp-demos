/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.toedter.spring.mcpserver;


import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class WeatherService {

    private static final String BASE_URL = "https://api.open-meteo.com/v1/forecast";

    private final RestClient restClient;

    public WeatherService() {

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Accept", "application/vnd.api+json")
                .defaultHeader("User-Agent", "WeatherApiClient/1.0 (kai@toedter.com)")
                .build();
    }

    /**
     * Get current weather for specific latitude and longitude
     *
     * @param latitude  Latitude
     * @param longitude Longitude
     * @return The current weather for the given location
     * @throws RestClientException if the request fails
     */
    @McpTool(name = "get_weather_forecast_by_location",
            description = "Get the current weather forecast for a specific location.")
    public String getWeatherForecastByLocation(
            @McpToolParam(description = "Latitude of the location") double latitude,
            @McpToolParam(description = "Longitude of the location") double longitude) {

        return restClient.get()
                .uri("?latitude={latitude}&longitude={longitude}&current=temperature_2m,weathercode,windspeed_10m,precipitation", latitude, longitude)
                .retrieve()
                .body(String.class);
    }

    public static void main(String[] args) {
        WeatherService client = new WeatherService();
        System.out.println(client.getWeatherForecastByLocation(47.6062, -122.3321));
    }

}
