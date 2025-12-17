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

import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class MovieService {

    private static final String BASE_URL = "http://localhost:8080/api";

    private final RestClient restClient;

    public MovieService() {

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Accept", "application/vnd.api+json")
                .defaultHeader("User-Agent", "WeatherApiClient/1.0 (kai@toedter.com)")
                .build();
    }

    /**
     * Get the top ranked movies at imdb
     *
     * @param pageNumber page Number
     * @param pageSize   page size
     * @return The ranked list of movies
     * @throws RestClientException if the request fails
     */
    @McpTool(name = "top_ranked_imdb_movies",
            description = """
                    Get the top-ranked movies from IMDb.
                    The API supports pagination using the pageNumber and pageSize parameters.
                    The pageNumber starts at 0.
                    The default pageSize is 10, with a maximum of 250.
                    """)
    public String getTopRankedMovies(int pageNumber, int pageSize) {

        return restClient.get()
                .uri("/movies?page[number]={pageNumber}&page[size]={pageSize}", pageNumber, pageSize)
                .retrieve()
                .body(String.class);
    }

    public static void main(String[] args) {
        MovieService client = new MovieService();
        System.out.println(client.getTopRankedMovies(0, 250));
    }

}
