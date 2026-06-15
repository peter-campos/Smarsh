package com.smarsh.urlcache.service;

import com.smarsh.urlcache.exception.CacheFetchException;
import com.smarsh.urlcache.fetcher.WebContentFetcher;
import com.smarsh.urlcache.model.CacheEntry;
import com.smarsh.urlcache.repository.FileCacheRepository;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class UrlCacheService {

    private static final Logger log = LoggerFactory.getLogger(UrlCacheService.class);
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault());

    private final WebContentFetcher fetcher;
    private final FileCacheRepository cacheRepository;

    public UrlCacheService(WebContentFetcher fetcher, FileCacheRepository cacheRepository) {
        this.fetcher = fetcher;
        this.cacheRepository = cacheRepository;
    }

    /**
     * Main entry point: checks the local cache for the given URL.
     * <p>
     * On a cache hit the stored HTML is read from disk and printed. On a miss the page is
     * fetched (with SPA fallback via Playwright if needed), persisted to the cache, and then
     * printed. In both cases the result is written to {@code stdout} via {@link #printResult}.
     *
     * @param url the URL to look up or fetch
     * @throws CacheFetchException if the network fetch or a disk read/write fails
     */
    public void process(String url) throws CacheFetchException {
        log.info("Checking cache for URL: {}", url);

        Optional<CacheEntry> cached = cacheRepository.find(url);

        if (cached.isPresent()) {
            CacheEntry entry = cached.get();
            log.info("Cache hit — reading from: {}", entry.filePath());
            String content = cacheRepository.readContent(entry);
            printResult(entry.fetchedAt(), url, content);
            log.info("Done — content served from cache: {}", url);
        } else {
            String content;
            try {
                content = fetcher.fetch(url);
            } catch (CacheFetchException e) {
                log.error("Failed to fetch URL: {} at {}", url, Instant.now(), e);
                throw e;
            }

            Instant fetchedAt = Instant.now();

            try {
                cacheRepository.write(url, content, fetchedAt);
            } catch (CacheFetchException e) {
                log.error("Failed to write cache for URL: {} at {}", url, Instant.now(), e);
                throw e;
            }

            printResult(fetchedAt, url, content);
            log.info("Done — content fetched from web and cached: {}", url);
        }
    }

    /** Prints the fetch result block to {@code stdout}, pretty-printing the HTML via Jsoup. */
    private void printResult(Instant fetchedAt, String url, String content) {
        System.out.println();
        System.out.println("=== RESULT ===");
        System.out.println("Fetched at: " + DISPLAY_FORMAT.format(fetchedAt));
        System.out.println("URL: " + url);
        System.out.println("Content:");
        System.out.println(Jsoup.parse(content).toString());
        System.out.println("===  ===");
        System.out.println();
    }
}
