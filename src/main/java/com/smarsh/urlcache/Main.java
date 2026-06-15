package com.smarsh.urlcache;

import com.smarsh.urlcache.exception.CacheFetchException;
import com.smarsh.urlcache.fetcher.WebContentFetcher;
import com.smarsh.urlcache.repository.FileCacheRepository;
import com.smarsh.urlcache.service.UrlCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.nio.file.Path;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String DEFAULT_URL = "https://example.com";

    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : DEFAULT_URL;

        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        WebContentFetcher fetcher = new WebContentFetcher(httpClient);
        FileCacheRepository cacheRepository = new FileCacheRepository(Path.of("./cache"));
        UrlCacheService service = new UrlCacheService(fetcher, cacheRepository);

        try {
            service.process(url);
        } catch (CacheFetchException e) {
            log.error("Unrecoverable error — exiting", e);
            System.exit(1);
        }
    }
}
