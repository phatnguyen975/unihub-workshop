package com.unihub.workshop.registrations.presentation.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unihub.workshop.registrations.domain.IdempotencyKey;
import com.unihub.workshop.registrations.domain.Registration;
import com.unihub.workshop.registrations.repository.IdempotencyKeyRepository;
import com.unihub.workshop.registrations.service.RegistrationService;
import com.unihub.workshop.shared.web.ResponseFactory;
import com.unihub.workshop.shared.web.ApiResponse;
import com.unihub.workshop.shared.web.UseCaseResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.unihub.workshop.registrations.repository.PaymentRepository;
import com.unihub.workshop.registrations.repository.RegistrationRepository;

@RestController
@RequestMapping("/api/v1/registrations")
public class RegistrationController {

    private final RegistrationService registrationService;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ResponseFactory responseFactory;
    private final RegistrationRepository registrationRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    public RegistrationController(RegistrationService registrationService,
                                  IdempotencyKeyRepository idempotencyKeyRepository,
                                  ResponseFactory responseFactory,
                                  RegistrationRepository registrationRepository,
                                  PaymentRepository paymentRepository,
                                  ObjectMapper objectMapper) {
        this.registrationService = registrationService;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.responseFactory = responseFactory;
        this.registrationRepository = registrationRepository;
        this.paymentRepository = paymentRepository;
        this.objectMapper = objectMapper;
    }

    public static record RegistrationRequest(@NotNull UUID workshopId) {}

    @PostMapping
    public ResponseEntity<ApiResponse<?>> reserve(
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody RegistrationRequest request,
            Authentication authentication) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest().body(responseFactory.success(null, "Missing Idempotency-Key", HttpStatus.BAD_REQUEST));
        }

        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseFactory.success(null, "Invalid user", HttpStatus.UNAUTHORIZED));
        }

        String requestHash = hashRequest(idempotencyKey, userId, request.workshopId());

        // absolute first business gate: return cached response when the same request already succeeded
        var existing = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyKey key = existing.get();
            if (!Objects.equals(key.getRequestHash(), requestHash)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(responseFactory.success(null, "Idempotency key reuse with different request", HttpStatus.CONFLICT));
            }

            int status = key.getResponseStatusCode() == null ? HttpStatus.OK.value() : key.getResponseStatusCode();
            ApiResponse<Object> cachedResponse = deserializeCachedResponse(key.getResponseBody());
            return ResponseEntity.status(status).body(cachedResponse);
        }

        Registration reg;
        try {
            reg = registrationService.reserveSeat(request.workshopId(), userId, idempotencyKey);
        } catch (RuntimeException ex) {
            String msg = ex.getMessage();
            if ("SCHEDULE_OVERLAP".equals(msg)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(responseFactory.success(null, "Schedule overlap", HttpStatus.CONFLICT));
            }
            if ("SOLD_OUT".equals(msg)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseFactory.success(null, "Sold out", HttpStatus.BAD_REQUEST));
            }
            if (ex instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(responseFactory.success(null, "Workshop not found", HttpStatus.NOT_FOUND));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseFactory.success(null, "Internal error", HttpStatus.INTERNAL_SERVER_ERROR));
        }

        ApiResponse<Object> api = responseFactory.success(java.util.Map.of("registrationId", reg.getId()), "Created", HttpStatus.CREATED);

        // persist idempotency record
        IdempotencyKey key = new IdempotencyKey();
        key.setIdempotencyKey(idempotencyKey);
        key.setRequestHash(requestHash);
        key.setResponseStatusCode(201);
        key.setResponseBody(serializeResponse(api));
        key.setExpiresAt(OffsetDateTime.now().plusHours(24));
        idempotencyKeyRepository.save(key);

        return ResponseEntity.status(HttpStatus.CREATED).body(api);
    }

    private ApiResponse<Object> deserializeCachedResponse(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, new TypeReference<ApiResponse<Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize cached idempotency response", e);
        }
    }

    private String serializeResponse(ApiResponse<Object> response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize idempotency response", e);
        }
    }

    private String hashRequest(String idempotencyKey, UUID userId, UUID workshopId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = idempotencyKey + "|" + userId + "|" + workshopId;
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to compute request hash", e);
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<?>> cancel(
            @PathVariable("id") UUID id,
            Authentication authentication) {
        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(responseFactory.success(null, "Invalid user", HttpStatus.UNAUTHORIZED));
        }

        Registration registration = registrationService.cancelRegistration(id, userId);
        if ("CANCELED".equals(registration.getStatus())) {
            ApiResponse<Object> api = responseFactory.success(java.util.Map.of("registrationId", registration.getId(), "status", registration.getStatus()), "Canceled", HttpStatus.OK);
            return ResponseEntity.ok(api);
        }

        ApiResponse<Object> api = responseFactory.success(java.util.Map.of("registrationId", registration.getId(), "status", registration.getStatus()), "Already canceled", HttpStatus.OK);
        return ResponseEntity.ok(api);
    }

    @org.springframework.web.bind.annotation.GetMapping("/{id}/ticket")
    public ResponseEntity<ApiResponse<String>> ticket(
            @PathVariable("id") UUID id,
            Authentication authentication) {
        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(responseFactory.success(null, "Invalid user", HttpStatus.UNAUTHORIZED));
        }

        String jwt = registrationService.issueTicket(id, userId);
        UseCaseResult<String> result = new UseCaseResult<>(HttpStatus.OK, "Ticket issued", jwt);
        return ResponseEntity.status(result.status())
                .body(responseFactory.success(result.data(), result.message(), result.status()));
    }

    @org.springframework.web.bind.annotation.GetMapping
    public ResponseEntity<ApiResponse<?>> listUserRegistrations(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "status", required = false) String status,
            Authentication authentication) {
        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(responseFactory.success(null, "Invalid user", HttpStatus.UNAUTHORIZED));
        }

        Pageable pageable = PageRequest.of(page, size);
        var pageResult = registrationRepository.findByUserId(userId, pageable);
        return ResponseEntity.ok(responseFactory.success(pageResult.getContent(), "User registrations", HttpStatus.OK));
    }

    @org.springframework.web.bind.annotation.GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getRegistrationDetail(@PathVariable("id") UUID id, Authentication authentication) {
        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(responseFactory.success(null, "Invalid user", HttpStatus.UNAUTHORIZED));
        }

        var regOpt = registrationRepository.findById(id);
        if (regOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(responseFactory.success(null, "Not found", HttpStatus.NOT_FOUND));
        }
        var reg = regOpt.get();
        if (!reg.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(responseFactory.success(null, "Forbidden", HttpStatus.FORBIDDEN));
        }

        var paymentOpt = paymentRepository.findByRegistrationId(id);
        var detail = java.util.Map.of(
                "registration", reg,
                "payment", paymentOpt.orElse(null)
        );
        return ResponseEntity.ok(responseFactory.success(detail, "Registration detail", HttpStatus.OK));
    }
}
