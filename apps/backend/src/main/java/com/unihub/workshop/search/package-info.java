/**
 * Search & Catalog Discovery Module.
 * <p>
 * Provides full-text search, catalog filtering, keyset pagination, and SWR caching
 * for published workshops backed by Elasticsearch and Redis.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Search Module",
    allowedDependencies = { "shared", "workshops::api" }
)
package com.unihub.workshop.search;
