package com.smarsh.urlcache.repository;

import com.smarsh.urlcache.exception.CacheFetchException;
import com.smarsh.urlcache.model.CacheEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileCacheRepositoryTest {

    @TempDir
    Path tempDir;

    private FileCacheRepository repository;

    @BeforeEach
    void setUp() {
        repository = new FileCacheRepository(tempDir);
    }

    @Test
    void find_returnsEmpty_whenNoCacheExists() {
        assertThat(repository.find("https://example.com")).isEmpty();
    }

    @Test
    void find_returnsEmpty_whenOnlyHtmlFileMissing() throws Exception {
        String url = "https://example.com";
        repository.write(url, "<html/>", Instant.now());
        // remove the .html file to simulate corruption
        String hash = FileCacheRepository.sha256Hex(url);
        Files.delete(tempDir.resolve(hash + ".html"));

        assertThat(repository.find(url)).isEmpty();
    }

    @Test
    void write_createsBothCacheFiles() throws CacheFetchException {
        String url = "https://example.com";
        String hash = FileCacheRepository.sha256Hex(url);

        repository.write(url, "<html><body>Hello</body></html>", Instant.now());

        assertThat(tempDir.resolve(hash + ".html")).exists();
        assertThat(tempDir.resolve(hash + ".meta")).exists();
    }

    @Test
    void write_persistsCorrectFields() throws CacheFetchException {
        String url = "https://example.com";
        String content = "<html/>";
        Instant fetchedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        repository.write(url, content, fetchedAt);
        CacheEntry entry = repository.find(url).orElseThrow();

        assertThat(entry.url()).isEqualTo(url);
        assertThat(entry.fetchedAt()).isEqualTo(fetchedAt);
        assertThat(entry.contentHash()).hasSize(64); // SHA-256 hex is always 64 chars
    }

    @Test
    void find_returnsCacheEntry_afterWrite() throws CacheFetchException {
        String url = "https://example.com";
        Instant fetchedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        repository.write(url, "<html/>", fetchedAt);

        Optional<CacheEntry> result = repository.find(url);

        assertThat(result).isPresent();
        assertThat(result.get().url()).isEqualTo(url);
        assertThat(result.get().fetchedAt()).isEqualTo(fetchedAt);
    }

    @Test
    void find_preservesContentHash() throws CacheFetchException {
        String url = "https://example.com";
        String content = "hello world";

        repository.write(url, content, Instant.now());
        CacheEntry found = repository.find(url).orElseThrow();

        assertThat(found.contentHash()).isEqualTo(FileCacheRepository.sha256Hex(content));
    }

    @Test
    void readContent_returnsOriginalContent() throws CacheFetchException {
        String url = "https://example.com";
        String content = "<html><body>Cached content</body></html>";

        repository.write(url, content, Instant.now());
        CacheEntry entry = repository.find(url).orElseThrow();

        assertThat(repository.readContent(entry)).isEqualTo(content);
    }

    @Test
    void readContent_throwsCacheFetchException_whenFileDeleted() throws Exception {
        String url = "https://example.com";
        repository.write(url, "<html/>", Instant.now());
        CacheEntry entry = repository.find(url).orElseThrow();
        Files.delete(entry.filePath());

        assertThatThrownBy(() -> repository.readContent(entry))
                .isInstanceOf(CacheFetchException.class)
                .hasMessageContaining(url);
    }

    @Test
    void write_handlesUrlWithQueryParams() throws CacheFetchException {
        String url = "https://example.com/search?q=hello&lang=en";
        repository.write(url, "<html/>", Instant.now());

        Optional<CacheEntry> found = repository.find(url);
        assertThat(found).isPresent();
        assertThat(found.get().url()).isEqualTo(url);
    }

    @Test
    void write_createsCacheDir_whenItDoesNotExist() throws CacheFetchException {
        Path nestedDir = tempDir.resolve("nested/cache");
        FileCacheRepository nested = new FileCacheRepository(nestedDir);

        nested.write("https://example.com", "<html/>", Instant.now());

        assertThat(nestedDir).isDirectory();
    }
}
