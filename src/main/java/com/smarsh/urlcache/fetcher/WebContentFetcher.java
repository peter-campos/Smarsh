package com.smarsh.urlcache.fetcher;

import com.smarsh.urlcache.exception.CacheFetchException;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class WebContentFetcher {

    private static final Logger log = LoggerFactory.getLogger(WebContentFetcher.class);

    private final HttpClient httpClient;

    public WebContentFetcher(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String fetch(String url) throws CacheFetchException {
        log.info("Fetching content from web: {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new CacheFetchException("Network error fetching URL: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CacheFetchException("Fetch interrupted for URL: " + url, e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new CacheFetchException("Non-2xx status " + status + " fetching URL: " + url);
        }

        String content = response.body();
        log.info("Fetched \"{}\" ({} bytes), status={}", extractTitle(content, url), content.length(), status);

        return content;
    }

    private String extractTitle(String html, String fallback) {
        try {
            String title = Jsoup.parse(html).title();
            return title.isBlank() ? fallback : title;
        } catch (Exception e) {
            log.debug("Could not extract title from HTML: {}", e.getMessage());
            return fallback;
        }
    }
}
