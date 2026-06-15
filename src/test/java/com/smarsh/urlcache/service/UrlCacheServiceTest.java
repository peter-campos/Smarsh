package com.smarsh.urlcache.service;

import com.smarsh.urlcache.exception.CacheFetchException;
import com.smarsh.urlcache.fetcher.WebContentFetcher;
import com.smarsh.urlcache.model.CacheEntry;
import com.smarsh.urlcache.repository.FileCacheRepository;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlCacheServiceTest {

    @Mock
    private WebContentFetcher fetcher;

    @Mock
    private FileCacheRepository cacheRepository;

    private UrlCacheService service;

    @BeforeEach
    void setUp() {
        service = new UrlCacheService(fetcher, cacheRepository);
    }

    @Test
    void process_fetchesAndWritesCache_onCacheMiss() throws CacheFetchException {
        String url = "https://example.com";
        String content = "<html><body>Hello</body></html>";

        when(cacheRepository.find(url)).thenReturn(Optional.empty());
        when(fetcher.fetch(url)).thenReturn(content);

        service.process(url);

        verify(fetcher).fetch(url);
        verify(cacheRepository).write(eq(url), eq(content), any(Instant.class));
    }

    @Test
    void process_readsFromCache_onCacheHit_withoutFetching() throws CacheFetchException {
        String url = "https://example.com";
        String content = "<html><body>Cached!</body></html>";
        CacheEntry entry = new CacheEntry(url, Instant.now(), Path.of("/tmp/cache/x.html"), "hash");

        when(cacheRepository.find(url)).thenReturn(Optional.of(entry));
        when(cacheRepository.readContent(entry)).thenReturn(content);

        service.process(url);

        verify(fetcher, never()).fetch(any());
        verify(cacheRepository).readContent(entry);
    }

    @Test
    void process_printsCachedFetchedAt_onCacheHit() throws Exception {
        String url = "https://example.com";
        Instant originalFetchedAt = Instant.parse("2026-01-01T10:00:00Z");
        CacheEntry entry = new CacheEntry(url, originalFetchedAt, Path.of("/tmp/x.html"), "hash");

        when(cacheRepository.find(url)).thenReturn(Optional.of(entry));
        when(cacheRepository.readContent(entry)).thenReturn("<html/>");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(out));
        try {
            service.process(url);
        } finally {
            System.setOut(original);
        }

        assertThat(out.toString()).contains("2026-01-01T");
    }

    @Test
    void process_propagatesCacheFetchException_onFetchError() throws CacheFetchException {
        String url = "https://unreachable.example.com";

        when(cacheRepository.find(url)).thenReturn(Optional.empty());
        when(fetcher.fetch(url)).thenThrow(new CacheFetchException("Network error"));

        assertThatThrownBy(() -> service.process(url))
                .isInstanceOf(CacheFetchException.class)
                .hasMessageContaining("Network error");

        verify(cacheRepository, never()).write(any(), any(), any());
    }

    @Test
    void process_propagatesCacheFetchException_onWriteError() throws CacheFetchException {
        String url = "https://example.com";
        String content = "<html/>";

        when(cacheRepository.find(url)).thenReturn(Optional.empty());
        when(fetcher.fetch(url)).thenReturn(content);
        doThrow(new CacheFetchException("Disk full"))
                .when(cacheRepository).write(eq(url), eq(content), any(Instant.class));

        assertThatThrownBy(() -> service.process(url))
                .isInstanceOf(CacheFetchException.class)
                .hasMessageContaining("Disk full");
    }

    @Test
    void process_printResultContainsUrlAndContent_onCacheMiss() throws CacheFetchException {
        String url = "https://example.com";
        String content = "<html><body>Fresh content</body></html>";

        when(cacheRepository.find(url)).thenReturn(Optional.empty());
        when(fetcher.fetch(url)).thenReturn(content);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(out));
        try {
            service.process(url);
        } finally {
            System.setOut(original);
        }

        String output = out.toString();
        assertThat(output).contains("=== RESULT ===");
        assertThat(output).contains("URL: " + url);
        assertThat(output).contains(Jsoup.parse(content).toString());
    }
}
