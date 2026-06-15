package com.smarsh.urlcache.fetcher;

import com.smarsh.urlcache.exception.CacheFetchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebContentFetcherTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private WebContentFetcher fetcher;

    @BeforeEach
    void setUp() {
        fetcher = new WebContentFetcher(httpClient);
    }

    @Test
    void fetch_returnsContent_onSuccess() throws Exception {
        String html = "<html><head><title>Test Page</title></head><body>Hello</body></html>";
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(html);

        String result = fetcher.fetch("https://example.com");

        assertThat(result).isEqualTo(html);
    }

    @Test
    void fetch_throwsCacheFetchException_onNon2xxStatus() throws Exception {
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());
        when(httpResponse.statusCode()).thenReturn(404);

        assertThatThrownBy(() -> fetcher.fetch("https://example.com/missing"))
                .isInstanceOf(CacheFetchException.class)
                .hasMessageContaining("404");
    }

    @Test
    void fetch_throwsCacheFetchException_on500Status() throws Exception {
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());
        when(httpResponse.statusCode()).thenReturn(500);

        assertThatThrownBy(() -> fetcher.fetch("https://example.com"))
                .isInstanceOf(CacheFetchException.class)
                .hasMessageContaining("500");
    }

    @Test
    void fetch_throwsCacheFetchException_onIOException() throws Exception {
        doThrow(new IOException("Connection refused")).when(httpClient).send(any(), any());

        assertThatThrownBy(() -> fetcher.fetch("https://unreachable.example.com"))
                .isInstanceOf(CacheFetchException.class)
                .hasMessageContaining("unreachable.example.com");
    }

    @Test
    void fetch_throwsCacheFetchException_onInterruptedException() throws Exception {
        doThrow(new InterruptedException("Interrupted")).when(httpClient).send(any(), any());

        assertThatThrownBy(() -> fetcher.fetch("https://example.com"))
                .isInstanceOf(CacheFetchException.class)
                .hasMessageContaining("interrupted");

        // ensure interrupt flag is restored
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted(); // clear for subsequent tests
    }
}
