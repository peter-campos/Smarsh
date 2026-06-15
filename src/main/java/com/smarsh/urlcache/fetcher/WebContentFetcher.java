package com.smarsh.urlcache.fetcher;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.smarsh.urlcache.exception.CacheFetchException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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

    /**
     * Fetches the HTML content of the given URL.
     * <p>
     * First performs a lightweight HTTP GET via {@link HttpClient}. If the response looks like
     * a Single-Page Application (empty {@code #root}/{@code #app} container or many JS bundle
     * scripts), it falls back to a headless Playwright browser that executes JavaScript and
     * returns the fully-rendered HTML. Otherwise the raw HTTP response body is returned.
     *
     * @param url the URL to fetch
     * @return the HTML content of the page
     * @throws CacheFetchException on network error, non-2xx status, or Playwright failure
     */
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

        String html = response.body();

        if (isSpa(html)) {
            log.info("SPA detected, rendering with Playwright: {}", url);
            return renderWithPlaywright(url);
        }

        log.info("Fetched \"{}\" ({} bytes), status={}", extractTitle(html, url), html.length(), status);
        return html;
    }

    /**
     * Returns {@code true} if the HTML looks like a client-side Single-Page Application
     * that requires JavaScript execution to produce meaningful content.
     * <p>
     * Two signals are checked: (1) an empty {@code #root} or {@code #app} element — the
     * standard React/Vue mounting point — and (2) three or more JS bundle/chunk script tags,
     * which indicate a heavily JS-driven page.
     */
    private boolean isSpa(String html) {
        Document doc = Jsoup.parse(html);
        Element root = doc.getElementById("root");
        Element app  = doc.getElementById("app");

        boolean emptyContainer = (root != null && root.childrenSize() == 0 && root.text().isEmpty())
                              || (app  != null && app.childrenSize()  == 0 && app.text().isEmpty());

        long bundleScripts = doc.select("script[src]").stream()
                .filter(s -> s.attr("src").contains("bundle") || s.attr("src").contains("chunk"))
                .count();

        return emptyContainer || bundleScripts >= 3;
    }

    /**
     * Launches a headless Chromium browser via Playwright, navigates to the URL, waits for
     * the network to go idle and for the SPA root element to have rendered children, then
     * returns the fully-rendered HTML.
     * <p>
     * A real browser user-agent is set so that bot-detection middleware does not serve an
     * empty shell. The Playwright instance is closed automatically via try-with-resources.
     *
     * @param url the URL to render
     * @return the rendered HTML after JavaScript execution
     * @throws CacheFetchException if Playwright fails to launch or the page cannot be loaded
     */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private String renderWithPlaywright(String url) throws CacheFetchException {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(true)
            );
            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions().setUserAgent(USER_AGENT)
            );
            Page page = context.newPage();
            page.navigate(url);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            // wait until #root or #app actually has children, up to 10 s
            page.waitForFunction(
                    "() => { const r = document.getElementById('root') || document.getElementById('app'); " +
                    "return !r || r.children.length > 0; }",
                    null,
                    new Page.WaitForFunctionOptions().setTimeout(10_000)
            );
            String html = page.content();
            log.info("Playwright rendered \"{}\" ({} bytes)", extractTitle(html, url), html.length());
            return html;
        } catch (Exception e) {
            throw new CacheFetchException("Playwright failed rendering URL: " + url, e);
        }
    }

    /** Extracts the {@code <title>} from an HTML string, returning {@code fallback} if absent. */
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
