# CLAUDE.md — Project Guide

## What this project does

`url-cache` is a Java 21 CLI tool that fetches a URL and caches the HTML locally. On subsequent runs the cached file is returned without hitting the network. It handles both static pages (lightweight HTTP) and SPAs (Playwright headless browser fallback).

## Build and run

```bash
# Build (produces two JARs — see below)
mvn package

# Run via Maven
mvn exec:java -Dexec.args="https://example.com"

# Run via fat JAR (after package)
java -jar target/url-cache-1.0-SNAPSHOT-shaded.jar https://example.com

# Run tests
mvn test
```

## Two JARs

`mvn package` produces:
- `url-cache-1.0-SNAPSHOT.jar` — classes only, no dependencies
- `url-cache-1.0-SNAPSHOT-shaded.jar` — fat JAR with all dependencies merged flat (via `maven-shade-plugin`). Use this for standalone execution.

## Key dependencies

| Dependency | Purpose |
|---|---|
| `jsoup` | HTML parsing and title extraction |
| `playwright 1.60.0` | Headless browser for SPA rendering |
| `slf4j` + `logback` | Logging |
| `JUnit 5` + `Mockito` + `AssertJ` | Testing |

## Architecture

```
Main
 └── UrlCacheService          orchestrates cache-check → fetch → store → print
      ├── WebContentFetcher   HTTP fetch + SPA detection + Playwright fallback
      └── FileCacheRepository reads/writes ./cache/<sha256>.html + .meta pairs
```

## SPA detection logic (`WebContentFetcher.isSpa`)

Two signals trigger the Playwright fallback:
1. `#root` or `#app` element exists and has no children (React/Vue mounting point, not yet rendered)
2. Three or more `<script src>` tags whose `src` contains `bundle` or `chunk`

## Cache format

Each cached URL produces two files under `./cache/`:
- `<sha256-of-url>.html` — raw HTML (UTF-8)
- `<sha256-of-url>.meta` — plain `key=value` with `url`, `fetchedAt` (ISO-8601), `contentHash` (SHA-256 of content)

## Testing approach

- `FileCacheRepositoryTest` — real filesystem via JUnit `@TempDir`; no mocks
- `UrlCacheServiceTest` — mocks `WebContentFetcher` and `FileCacheRepository`
- `WebContentFetcherTest` — mocks `HttpClient`; the Playwright path is not unit-tested (requires a real browser) and is covered by manual/integration testing
