# UniHub Workshop — Local Infrastructure

## Scope

This directory owns the Docker Compose topology and component configuration for UniHub's local infrastructure: the API gateway, cache, message broker, search, object storage, development email and monitoring.

The [Compose stack](docker-compose.yml) runs infrastructure services. Backend and worker processes are separate application workloads; the default local topology connects containers to those workloads on the host. PostgreSQL is managed through Neon outside this stack. Application setup, feature behavior and public API contracts are owned by their respective guides.

## Services and Configuration

| Component             | Responsibility                                                                         | Configuration                                                                                                                                                                    |
| --------------------- | -------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Nginx                 | Reverse proxy and load balancing to host backend instances; no gateway rate limiting   | [Base configuration](nginx/nginx.conf), [upstream template](nginx/templates/default.conf.template)                                                                               |
| Redis                 | Cache, application rate-limit state, token revocation and transient event distribution | [redis.conf](redis/redis.conf)                                                                                                                                                   |
| RabbitMQ              | Background-work messaging and broker metrics                                           | [Broker configuration](rabbitmq/rabbitmq.conf), [plugins](rabbitmq/enabled_plugins), [definitions template](rabbitmq/definitions.json.template)                                  |
| Elasticsearch         | Workshop search read model                                                             | [elasticsearch.yml](elasticsearch/elasticsearch.yml)                                                                                                                             |
| MinIO and minio-init  | Local S3-compatible storage and repeatable bucket initialization                       | [Bucket bootstrap](minio/init/create-buckets.sh)                                                                                                                                 |
| Mailpit               | Captures development SMTP messages and provides an inbox UI                            | Service definition in [Compose](docker-compose.yml)                                                                                                                              |
| Prometheus            | Collects backend, worker and infrastructure metrics                                    | [Scrape targets](prometheus/prometheus.yml)                                                                                                                                      |
| Grafana               | Provisioned metrics datasource and UniHub dashboards                                   | [Datasource](grafana/provisioning/datasources/prometheus.yml), [dashboard provider](grafana/provisioning/dashboards/dashboards.yml), [dashboard definitions](grafana/dashboards) |
| Nginx/Redis exporters | Expose gateway/cache metrics to Prometheus                                             | Service definitions in [Compose](docker-compose.yml)                                                                                                                             |

Compose defines image versions, service dependencies, resource limits, health checks, published ports, environment defaults and volume mounts. Component files own the configuration mounted or rendered for that service.

## Directory Layout

```text
infra/
├── README.md
├── docker-compose.yml
├── .env.example
├── nginx/                 # Gateway configuration and rendered upstream template
├── redis/                 # Cache configuration
├── rabbitmq/              # Broker configuration, plugins and topology template
├── elasticsearch/         # Search node configuration
├── minio/init/            # Bucket bootstrap executed by minio-init
├── prometheus/            # Metrics scrape configuration
└── grafana/
    ├── provisioning/      # Datasource and dashboard provider configuration
    └── dashboards/        # Versioned dashboard JSON definitions
```

## Start, Inspect and Stop

All commands in this section run from `infra/` and operate on the Compose stack defined in this directory.

### Start and Inspect

```sh
docker compose up -d
docker compose ps -a
```

Inspect each service's configured health or exit status. `minio-init` is a one-shot job whose successful completion is an exit with code 0. It creates `unihub-brochures`, `unihub-csv-drops` and `unihub-media` if absent, and enables anonymous downloads for the brochure/media buckets.

Inspect startup problems by service, for example:

```sh
docker compose logs --tail 100 minio-init
docker compose logs --tail 100 nginx
```

### Application Connectivity

Nginx forwards to `BACKEND_1_HOST` and `BACKEND_2_HOST`, defaulting to `host.docker.internal:8081` and `host.docker.internal:8082`. Prometheus uses the backend/worker targets and host mappings defined by its scrape configuration and Compose.

Nginx's `/healthz` verifies the gateway process; backend availability is established through application checks. Monitoring target availability requires the corresponding application/exporter to be reachable and exposing its configured metrics endpoint. Container health, application readiness and completed initialization are distinct checks.

### Stop and Preserve Data

To stop the stack while retaining its named data volumes:

```sh
docker compose down
```

Redis, RabbitMQ, Elasticsearch, MinIO, Mailpit, Prometheus and Grafana use named volumes. Configuration files are mounted read-only where specified in Compose. Container recreation and application database reset are separate operations; Neon is outside this stack.

## Maintain Configuration and Monitoring

- Change service wiring, published ports, health checks and resource limits in Compose; edit the component configuration in its owning directory.
- Keep application and infrastructure credentials aligned using the repository setup instructions. Changing a template/environment value may require recreating the affected service so startup rendering runs again.
- Edit dashboard JSON under `grafana/dashboards/`. Grafana provisions these files with UI updates disabled; the versioned files own dashboard definitions.
- Dashboard coverage includes JVM/HTTP metrics, HikariCP, Redis/Bucket4j, RabbitMQ, Resilience4j, MinIO and Nginx. Scrape sources are defined in Prometheus configuration; not every application-specific metric exists before its producer is running.
