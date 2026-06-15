package com.smarsh.urlcache.model;

import java.nio.file.Path;
import java.time.Instant;

public record CacheEntry(
        String url,
        Instant fetchedAt,
        Path filePath,
        String contentHash
) {}
