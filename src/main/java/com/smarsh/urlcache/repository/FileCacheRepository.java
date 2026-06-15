package com.smarsh.urlcache.repository;

import com.smarsh.urlcache.exception.CacheFetchException;
import com.smarsh.urlcache.model.CacheEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FileCacheRepository {

    private static final Logger log = LoggerFactory.getLogger(FileCacheRepository.class);

    private final Path cacheDir;

    public FileCacheRepository(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    /**
     * Looks up the cache for the given URL.
     * <p>
     * Both the {@code .html} and {@code .meta} files must exist and be readable for a hit to
     * be returned. Corrupt or missing metadata is treated as a cache miss with a warning log.
     *
     * @param url the URL to look up
     * @return an {@link Optional} containing the cache entry, or empty on a miss
     */
    public Optional<CacheEntry> find(String url) {
        String urlHash = sha256Hex(url);
        Path htmlFile = cacheDir.resolve(urlHash + ".html");
        Path metaFile = cacheDir.resolve(urlHash + ".meta");

        if (!Files.exists(htmlFile) || !Files.exists(metaFile)) {
            log.debug("Cache miss: {}", htmlFile);
            return Optional.empty();
        }

        log.debug("Cache hit: {}", htmlFile);
        try {
            Map<String, String> meta = parseMeta(Files.readString(metaFile, StandardCharsets.UTF_8));
            String storedUrl = meta.get("url");
            Instant fetchedAt = Instant.parse(meta.get("fetchedAt"));
            String contentHash = meta.get("contentHash");
            return Optional.of(new CacheEntry(storedUrl, fetchedAt, htmlFile, contentHash));
        } catch (IOException | NullPointerException | java.time.format.DateTimeParseException e) {
            log.warn("Corrupt or unreadable cache metadata for URL: {}", url, e);
            return Optional.empty();
        }
    }

    /**
     * Persists the fetched HTML and its metadata to the cache directory.
     * <p>
     * Creates two files: {@code <url-hash>.html} (raw HTML, UTF-8) and
     * {@code <url-hash>.meta} (plain key=value with {@code url}, {@code fetchedAt},
     * and {@code contentHash}). The cache directory is created if it does not exist.
     *
     * @param url       the source URL
     * @param content   the HTML content to store
     * @param fetchedAt the timestamp to record in metadata
     * @throws CacheFetchException if any file write fails
     */
    public void write(String url, String content, Instant fetchedAt) throws CacheFetchException {
        try {
            Files.createDirectories(cacheDir);

            String urlHash = sha256Hex(url);
            String contentHash = sha256Hex(content);
            Path htmlFile = cacheDir.resolve(urlHash + ".html");
            Path metaFile = cacheDir.resolve(urlHash + ".meta");

            Files.writeString(htmlFile, content, StandardCharsets.UTF_8);

            String meta = "url=" + url + "\n"
                    + "fetchedAt=" + fetchedAt + "\n"
                    + "contentHash=" + contentHash + "\n";
            Files.writeString(metaFile, meta, StandardCharsets.UTF_8);

            log.info("Content saved to cache: {}", htmlFile);
        } catch (IOException e) {
            throw new CacheFetchException("Failed to write cache for URL: " + url, e);
        }
    }

    /**
     * Reads and returns the cached HTML for the given entry.
     *
     * @param entry the cache entry whose file path to read
     * @return the HTML content as a UTF-8 string
     * @throws CacheFetchException if the file cannot be read
     */
    public String readContent(CacheEntry entry) throws CacheFetchException {
        try {
            return Files.readString(entry.filePath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CacheFetchException("Failed to read cached content for: " + entry.url(), e);
        }
    }

    /** Parses a {@code key=value} metadata string into a map, one entry per line. */
    private static Map<String, String> parseMeta(String raw) {
        Map<String, String> props = new HashMap<>();
        for (String line : raw.split("\n")) {
            int idx = line.indexOf('=');
            if (idx > 0) {
                props.put(line.substring(0, idx), line.substring(idx + 1));
            }
        }
        return props;
    }

    /** Returns the SHA-256 hex digest of the given string (UTF-8 encoded). */
    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
