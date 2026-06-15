# URL Content Cache

A Java 21 command-line tool that fetches a web page and caches it locally. Each URL is fetched from the network only once; subsequent runs read from the local cache.

## Build

```bash
mvn package
```

## Run

**Via Maven (simplest):**
```bash
# Uses default URL (https://example.com)
mvn exec:java

# Custom URL
mvn exec:java -Dexec.args="https://example.com"
```

**Via fat JAR:**
```bash
java -jar target/url-cache-1.0-SNAPSHOT-shaded.jar [url]
```

## Test

```bash
mvn test
```

## Output example

On first run (cache miss):
```
14:32:01.123 [main] INFO  UrlCacheService - Checking cache for URL: https://example.com
14:32:01.124 [main] DEBUG FileCacheRepository - Cache miss: ./cache/<hash>.html
14:32:01.125 [main] INFO  WebContentFetcher - Fetching content from web: https://example.com
14:32:01.890 [main] INFO  WebContentFetcher - Fetched "Example Domain" (1256 bytes), status=200
14:32:01.895 [main] INFO  FileCacheRepository - Content saved to cache: ./cache/<hash>.html

=== RESULT ===
Fetched at: 2026-06-15T14:32:01
URL: https://example.com
Content:
<!doctype html>...
```

On subsequent runs (cache hit), the same result is printed but no network request is made.

> **Note:** Logs are written to `stderr`; the `=== RESULT ===` block is written to `stdout`. You can suppress logs with `2>/dev/null` (Unix) or `2>$null` (PowerShell).

## Architecture

| Class | Responsibility |
|---|---|
| `WebContentFetcher` | Fetches a URL via `java.net.http.HttpClient`; extracts the HTML `<title>` via Jsoup for logging |
| `FileCacheRepository` | Reads and writes cache entries to `./cache/`; each entry is a `<url-hash>.html` + `<url-hash>.meta` file pair |
| `CacheEntry` | Immutable record: `url`, `fetchedAt`, `filePath`, `contentHash` (SHA-256 of content) |
| `UrlCacheService` | Orchestrates the cache-check → fetch-or-read → print flow |
| `CacheFetchException` | Checked exception wrapping network and I/O errors, caught and logged with context at the service layer |

## Cache storage

- Directory: `./cache` (created automatically).
- File naming: SHA-256 hex of the URL — avoids filesystem-unsafe characters and collisions.
- Content file: `<hash>.html` (UTF-8).
- Metadata file: `<hash>.meta` — stores `url`, `fetchedAt` (ISO-8601), and `contentHash` (SHA-256 of content).

## Assumptions

- The URL is configured via `args[0]` with a hardcoded default (`https://example.com`) — no interactive console input.
- Cached content is stored as plain text (UTF-8 HTML). Binary assets are not supported.
- Metadata is stored explicitly in a `.meta` file rather than relying on filesystem timestamps, for clarity and testability.
- Cache filenames are derived from SHA-256 of the URL to handle special characters and avoid collisions.
- On fetch errors (network failure, non-2xx status, timeout) or I/O errors, the error is logged with context and the program exits with status code 1 — no unhandled stack traces.
- Each run is an independent process; the cache persists on disk between runs (no in-memory state).
- The `CacheEntry` record is self-contained (`url`, `fetchedAt`, `filePath`, `contentHash`), making it straightforward to later add a `compare(CacheEntry a, CacheEntry b)` method that diffs by `contentHash` or by file contents.

## Possible future extension

This could be exposed via a REST endpoint (Spring Boot) for a web-based cache comparison UI — fetching two URLs and comparing their cached `contentHash` values or diffing the stored HTML files.
