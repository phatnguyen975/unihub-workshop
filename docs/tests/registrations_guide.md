# UniHub Workshop - Registrations Postman Guide

This guide covers the authenticated registrations APIs under `/api/v1/registrations` and `/api/v1/payments`. Send requests to `http://localhost/api/v1/...` through Nginx so you can verify load balancing through `metadata.processedBy` in the JSON envelope.

## 1. Test Setup

### Postman Environment

Create a Postman environment with these variables:

- `baseUrl` = `http://localhost/api/v1`
- `accessToken` = empty string, and it must belong to a STUDENT account
- `freeWorkshopId` = empty string
- `paidWorkshopId` = empty string
- `idempotencyKey` = `{{$guid}}`
- `registrationId` = empty string

### Global Checks

- Send all requests through the Nginx entrypoint at `http://localhost/api/v1/...` when it is available so `metadata.processedBy` shows which backend instance handled the request.
- Confirm all responses use the standard JSON envelope with `success`, `message`, `data`, `metadata`, and `error`.
- Use a STUDENT access token for all authenticated tests in this guide.
- For payment and webhook checks, ensure Stripe test mode is active in the development environment.

> [!NOTE]
>
> - When requests are routed through Nginx, `metadata.processedBy` should alternate between backend instances depending on the load balancer.
> - For manual API checks, keep the database console available so you can confirm capacity changes and payment state transitions.

## 2. POST `/registrations` - FREE Ticket - UC1

### Purpose

Reserve a seat for a free workshop and confirm the registration immediately.

### Postman Steps

1. **Method:** `POST`
2. **URL:** `{{baseUrl}}/registrations`
3. **Headers:**
   - `Authorization: Bearer {{accessToken}}`
   - `Idempotency-Key: {{$guid}}`
4. **Body:** `raw` JSON

```json
{
  "workshopId": "{{freeWorkshopId}}"
}
```

### Sample Response

```json
{
  "success": true,
  "message": "Created",
  "data": {
    "registrationId": "3f2f0f43-1d7e-4a17-bb08-2b1f8a9f3f11"
  },
  "metadata": {
    "statusCode": 201,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-17T10:00:00Z"
  },
  "error": null
}
```

### Expected Data

- The registration is created with status `CONFIRMED`.
- The registration uses ticket type `FREE`.
- The workshop capacity is decremented by exactly one.

### Edge Cases

- Manual idempotency test in Postman:
  1. Keep `Idempotency-Key` as `{{$guid}}` and click **Send** to create the baseline registration.
  2. Open the **Postman Console** in the bottom-left corner of Postman, expand the last sent request, and copy the actual generated UUID value from the `Idempotency-Key` header.
  3. Return to the request **Headers** tab, replace `{{$guid}}` with the hardcoded UUID you copied, and click **Send** again.
  4. Confirm the backend returns the same cached JSON response envelope and does **not** decrement workshop capacity a second time.
- Send a request for a workshop that overlaps in time with an existing `PENDING` or `CONFIRMED` registration and confirm `409 Conflict`.
- Send a request for a sold-out workshop and confirm `400 Bad Request`.
- Send the request with a missing or invalid access token and confirm `401 Unauthorized`.

## 3. POST `/registrations` - PAID Ticket - UC1

### Purpose

Reserve a seat for a paid workshop and create a pending registration.

### Postman Steps

1. **Method:** `POST`
2. **URL:** `{{baseUrl}}/registrations`
3. **Headers:**
   - `Authorization: Bearer {{accessToken}}`
   - `Idempotency-Key: {{$guid}}`
4. **Body:** `raw` JSON

```json
{
  "workshopId": "{{paidWorkshopId}}"
}
```

### Sample Response

```json
{
  "success": true,
  "message": "Created",
  "data": {
    "registrationId": "7c3b8d2e-8d95-47cb-b1e4-6db1b6f4c6a8"
  },
  "metadata": {
    "statusCode": 201,
    "processedBy": "unihub-app-2",
    "timestamp": "2026-05-17T10:00:00Z"
  },
  "error": null
}
```

### Expected Data

- The registration is created with status `PENDING`.
- The registration uses ticket type `PAID`.
- The workshop capacity is decremented by exactly one.

### Edge Cases

- Manual idempotency test in Postman:
  1. Keep `Idempotency-Key` as `{{$guid}}` and click **Send** to create the baseline registration.
  2. Open the **Postman Console** in the bottom-left corner of Postman, expand the last sent request, and copy the actual generated UUID value from the `Idempotency-Key` header.
  3. Return to the request **Headers** tab, replace `{{$guid}}` with the hardcoded UUID you copied, and click **Send** again.
  4. Confirm the backend returns the same cached JSON response envelope and does **not** decrement workshop capacity a second time.
- Send a request for an overlapping workshop and confirm `409 Conflict`.
- Send a request for a sold-out workshop and confirm `400 Bad Request`.

## 4. POST `/payments` - Initiate Payment - UC2

### Purpose

Start the Stripe checkout flow for a pending registration.

### Postman Steps

1. **Method:** `POST`
2. **URL:** `{{baseUrl}}/payments`
3. **Headers:**
   - `Authorization: Bearer {{accessToken}}`
4. **Body:** `raw` JSON

```json
{
  "registrationId": "{{registrationId}}"
}
```

### Sample Response

```json
{
  "success": true,
  "message": "Checkout created",
  "data": {
    "checkoutUrl": "https://checkout.stripe.com/c/pay/cs_test_123",
    "providerRef": "cs_test_123"
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-17T10:00:00Z"
  },
  "error": null
}
```

### Expected Data

- The response contains a `checkoutUrl` for the Stripe Checkout session.
- The response contains a `providerRef` that can be used to correlate webhook events.

### Edge Cases

- Try to initiate payment for a `CONFIRMED` registration and confirm `409 Conflict`.
- Try to initiate payment for a `CANCELED` registration and confirm `409 Conflict`.
- Try to initiate payment with a missing registration and confirm `404 Not Found`.

## 5. Stripe Webhook Testing - UC3

### Purpose

Validate asynchronous payment finalization through the Stripe webhook flow.

### Postman Notes

- This endpoint is not easy to test directly in Postman because the request must include a valid `Stripe-Signature` header.
- If you need to simulate a successful payment event, use the Stripe CLI instead of Postman.

### Stripe CLI Command

```bash
stripe trigger checkout.session.completed
```

### Manual Verification

After the webhook runs, verify the database state manually:

1. Check that the `payments` table contains a record with status `COMPLETED`.
2. Confirm that the linked `registrations` row changed to `CONFIRMED`.
3. Confirm that the related workshop capacity was not double-decremented.

### Edge Cases

- Send the webhook without a valid `Stripe-Signature` and confirm `401 Unauthorized`.
- Send malformed payloads and confirm the endpoint returns an error response.

## 6. GET `/registrations` - List - UC5

### Purpose

Return the authenticated student's registrations with pagination.

### Postman Steps

1. **Method:** `GET`
2. **URL:** `{{baseUrl}}/registrations?page=0&size=10`
3. **Headers:** `Authorization: Bearer {{accessToken}}`
4. **Body:** none

### Sample Response

```json
{
  "success": true,
  "message": "User registrations",
  "data": {
    "items": [
      {
        "id": "3f2f0f43-1d7e-4a17-bb08-2b1f8a9f3f11",
        "status": "CONFIRMED",
        "ticketType": "FREE",
        "workshopId": "9a0ec9d4-6f45-4d1d-8d8e-9b8d7f3f5a10"
      }
    ],
    "pagination": {
      "page": 0,
      "size": 10,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false,
      "hasPrevious": false
    }
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-2",
    "timestamp": "2026-05-17T10:00:00Z"
  },
  "error": null
}
```

### Expected Data

- The response is paginated.
- The `items` array contains only registrations owned by the authenticated student.
- The `pagination` object includes page metadata.

### Edge Cases

- Send an invalid `page` or `size` value and confirm validation fails with `400 Bad Request`.
- Confirm the result set never includes another student's registration rows.

## 7. GET `/registrations/{id}` - Detail - UC4

### Purpose

Return the details for a single registration owned by the authenticated student.

### Postman Steps

1. **Method:** `GET`
2. **URL:** `{{baseUrl}}/registrations/{{registrationId}}`
3. **Headers:** `Authorization: Bearer {{accessToken}}`
4. **Body:** none

### Sample Response

```json
{
  "success": true,
  "message": "Registration detail",
  "data": {
    "registration": {
      "id": "3f2f0f43-1d7e-4a17-bb08-2b1f8a9f3f11",
      "status": "CONFIRMED",
      "ticketType": "FREE",
      "workshopId": "9a0ec9d4-6f45-4d1d-8d8e-9b8d7f3f5a10"
    },
    "payment": {
      "id": "0c3f6b5d-f7d9-4d73-8f0f-4b0f4d81bce2",
      "status": "COMPLETED",
      "provider": "stripe",
      "providerRef": "cs_test_123"
    }
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-17T10:00:00Z"
  },
  "error": null
}
```

### Expected Data

- The registration details are returned in the response.
- The response includes payment information when a payment record exists.

### Edge Cases

- Use a student access token to read a registration ID owned by a different student and confirm `403 Forbidden`.
- Request a registration ID that does not exist and confirm `404 Not Found`.

## 8. GET `/registrations/{id}/ticket` - QR Code - UC8

### Purpose

Return the signed JWT that represents the QR ticket for a confirmed registration.

### Postman Steps

1. **Method:** `GET`
2. **URL:** `{{baseUrl}}/registrations/{{registrationId}}/ticket`
3. **Headers:** `Authorization: Bearer {{accessToken}}`
4. **Body:** none

### Sample Response

```json
{
  "success": true,
  "message": "Ticket issued",
  "data": "eyJhbGciOiJSUzI1NiJ9.eyJyZWdpc3RyYXRpb25faWQiOiIzZjJmMGY0My0xZDdlLTRhMTctYmIwOC0yYjFmOGE5ZjNmMTEiLCJ3b3Jrc2hvcF9pZCI6IjlhMGVjOWQ0LTZmNDUtNGQxZC04ZDhlLTliOGQ3ZjNmNWExMCIsInVzZXJfaWQiOiI5OTVhYjVjYS0xY2I4LTRiNjQtYjNkOS1jOTc4N2YxNDAyZTQiLCJpYXQiOjE3NDc0NzIwMDB9.signature",
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-2",
    "timestamp": "2026-05-17T10:00:00Z"
  },
  "error": null
}
```

### Expected Data

- The response is a string containing the signed JWT used as the QR ticket.
- The token should be treated as tamper-evident and verifiable by the backend.

### Edge Cases

- Try to call this endpoint for a `PENDING` registration and confirm `409 Conflict`.
- Try to call this endpoint as a different student and confirm `403 Forbidden`.

## 9. POST `/registrations/{id}/cancel` - Cancel & Compensate - UC6 & UC7

### Purpose

Cancel an eligible registration and restore workshop capacity in the same transaction.

### Postman Steps

1. **Method:** `POST`
2. **URL:** `{{baseUrl}}/registrations/{{registrationId}}/cancel`
3. **Headers:** `Authorization: Bearer {{accessToken}}`
4. **Body:** none

### Sample Response

```json
{
  "success": true,
  "message": "Canceled",
  "data": {
    "registrationId": "3f2f0f43-1d7e-4a17-bb08-2b1f8a9f3f11",
    "status": "CANCELED"
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-1",
    "timestamp": "2026-05-17T10:00:00Z"
  },
  "error": null
}
```

### Expected Data

- The registration status changes to `CANCELED`.
- The workshop seat is restored exactly once.

### Manual Database Check

Run this query to verify the capacity restoration:

```sql
SELECT capacity_remaining
FROM workshops
WHERE id = '<workshop-id>';
```

### Edge Cases

- Cancel a workshop that has already started and confirm `400 Bad Request`.
- Cancel a registration owned by a different student and confirm `403 Forbidden`.
- Cancel a missing registration and confirm `404 Not Found`.

## 10. Payment Timeout Sweeper - UC9

### Purpose

Verify that expired pending registrations are automatically canceled and seats are restored.

### Manual QA Steps

1. Create a PAID registration and leave it in `PENDING` status.
2. Do not initiate or complete payment.
3. Wait for the cron interval, such as the configured 15-minute timeout window or the scheduled sweep cycle.
4. Check the database or call the registration detail endpoint to confirm the registration switched to `CANCELED`.
5. Confirm the workshop `capacity_remaining` value increased by one.

### Expected Result

```json
{
  "success": true,
  "message": "Registration detail",
  "data": {
    "registration": {
      "id": "7c3b8d2e-8d95-47cb-b1e4-6db1b6f4c6a8",
      "status": "CANCELED",
      "ticketType": "PAID"
    },
    "payment": {
      "status": "PENDING"
    }
  },
  "metadata": {
    "statusCode": 200,
    "processedBy": "unihub-app-2",
    "timestamp": "2026-05-17T10:00:00Z"
  },
  "error": null
}
```

### Edge Cases

- Verify the sweep skips rows locked by another instance and processes them in the next cycle.
- Confirm that already-canceled registrations are not processed again.
- Confirm the capacity is restored only once per expired registration.
