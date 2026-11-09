# ADR-024: Core Observability and Monitoring Stack

> **Scope:** server

- **Context:** High-concurrency surges can cause silent failures, connection pool starvation, or cache stampedes. We need real-time telemetry without introducing performance overhead to request threads.
- **Decision:** Use Spring Boot Actuator with Micrometer to expose core metrics at `/actuator/prometheus`. Deploy Prometheus as a pull-based Time Series Database (TSDB) to scrape these metrics, and Grafana for centralized visualization dashboarding.
- **Rationale:** Micrometer provides out-of-the-box, low-overhead instrumentation for JVM, Tomcat threads, HikariCP, Redis, and Resilience4j components. The pull-model of Prometheus ensures that backend instances are not burdened with pushing telemetry data during traffic spikes.
- **Consequences:** Adds two lightweight infrastructure containers (Prometheus & Grafana) to the Docker environment. Requires metrics storage provisioning.
