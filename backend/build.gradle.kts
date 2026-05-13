plugins {
	java
	id("org.springframework.boot") version "4.0.6"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.unihub"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

extra["springCloudVersion"] = "2025.1.1"
extra["springModulithVersion"] = "2.0.6"

dependencies {
	// Core Framework & Web
	implementation("org.springframework.boot:spring-boot-starter-web") // REST API surface
	implementation("org.springframework.boot:spring-boot-starter-validation") // DTO validation
	implementation("org.springframework.boot:spring-boot-starter-cache") // Cache abstractions for Redis
	implementation("org.springframework.boot:spring-boot-starter-actuator") // Health/readiness endpoints
	implementation("org.springframework.modulith:spring-modulith-starter-core") // Modulith boundaries
	implementation("org.springframework.modulith:spring-modulith-starter-jpa") // Modulith persistence support
	implementation("org.springframework.modulith:spring-modulith-events-api") // Modulith event contracts
	runtimeOnly("org.springframework.modulith:spring-modulith-runtime") // Modulith runtime wiring

	// Database, Migration & Connection Pooling
	implementation("org.springframework.boot:spring-boot-starter-data-jpa") // JPA + HikariCP
	implementation("org.springframework.boot:spring-boot-starter-flyway") // Migration-first schema control
	implementation("org.flywaydb:flyway-database-postgresql") // Postgres-specific Flyway support
	runtimeOnly("org.postgresql:postgresql") // Postgres JDBC driver

	// Security, Auth & JWT
	implementation("org.springframework.boot:spring-boot-starter-security") // RBAC and request security
	implementation("io.jsonwebtoken:jjwt-api:0.12.5") // JWT creation/validation
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

	// Messaging & Async (RabbitMQ, Batch)
	implementation("org.springframework.boot:spring-boot-starter-amqp") // RabbitMQ integration
	implementation("org.springframework.boot:spring-boot-starter-batch") // CSV ingestion jobs
	runtimeOnly("org.springframework.modulith:spring-modulith-events-amqp") // Modulith events via AMQP

	// Resiliency & Rate Limiting (Circuit Breakers, Token Bucket)
	implementation("org.springframework.boot:spring-boot-starter-data-redis") // Redis cache + token bucket state
	implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j") // Circuit breakers
	implementation("com.bucket4j:bucket4j_jdk17-core:8.18.0") // Token bucket algorithm
	implementation("com.bucket4j:bucket4j_jdk17-redis-common:8.18.0") // Redis-backed rate limiter common module
	implementation("com.bucket4j:bucket4j_jdk17-lettuce:8.18.0") // Lettuce-backed distributed rate limiter

	// Third-Party Integrations (Storage, Payment)
	implementation(platform("software.amazon.awssdk:bom:2.25.70")) // AWS SDK BOM
	implementation("software.amazon.awssdk:s3") // S3-compatible object storage (MinIO)
	implementation("com.stripe:stripe-java:32.1.0") // Stripe payment gateway
	implementation("org.springframework.boot:spring-boot-starter-mail") // Email delivery

	// Observability & Documentation
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2") // OpenAPI/Swagger UI

	// Developer Tools & Utils
	implementation("org.apache.pdfbox:pdfbox:3.0.2") // PDF extraction for AI summaries
	implementation("io.github.cdimascio:java-dotenv:5.2.2") // Local env loading
	compileOnly("org.projectlombok:lombok") // Boilerplate reduction
	annotationProcessor("org.projectlombok:lombok")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor") // Config metadata
	developmentOnly("org.springframework.boot:spring-boot-devtools") // Hot reload during development

	// Testing
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-amqp-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-mail-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testImplementation("org.springframework.batch:spring-batch-test")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testRuntimeOnly("com.h2database:h2")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
