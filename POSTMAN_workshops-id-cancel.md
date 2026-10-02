# Postman Testing Guide: Workshop Cancellation

> **Endpoint:** `PATCH /api/v1/admin/workshops/{id}/cancel`  
> **Module:** `workshops` (Feature 2.4 — `workshop-cancellation`)  
> **Reference Spec:** `specs/workshop-cancellation/SPEC.md`  
> **Reference Contract:** `specs/workshop-cancellation/API_CONTRACT.md`  
> **Standard:** `POSTMAN_TEMPLATE.md`

## 1. Overview & Objectives

This guide provides an end-to-end Postman testing specification for **Feature 2.4 — Workshop Cancellation**.  
The endpoint transitions a workshop from `PUBLISHED` status to `CANCELED` status while enforcing:

- **Role & Multi-tenant Authorization:** Only users with `ROLE_ORGANIZER` belonging to the workshop's owning tenant can cancel it.
- **Optimistic Concurrency Control:** Concurrency is guarded via the required `version` payload integer.
- **Prohibited Field Rejection:** All fields other than `version` and optional `reason` are rejected with `422 Unprocessable Content`.
- **Temporal Invariants:** Concluded workshops (`ends_at <= now()`) cannot be canceled (`422 Unprocessable Content`). Ongoing live workshops (`starts_at <= now() < ends_at`) can be canceled.
- **Status Lifecycle Constraints:** Only `PUBLISHED` workshops can be canceled. `DRAFT`, `COMPLETED`, or already `CANCELED` workshops are rejected (`422 Unprocessable Content`).
- **Asynchronous Event Side-Effects:** Emits `WorkshopCanceledEvent` to RabbitMQ with a transient reason (the reason is never persisted to the PostgreSQL table).

## 2. Postman Environment Variables (`UniHub IAM Dev`)

Configure or select the `UniHub IAM Dev` environment in Postman with the following variables:

| Variable                   | Default / Example Value              | Description                                               |
| :------------------------- | :----------------------------------- | :-------------------------------------------------------- |
| `base_url`                 | `http://localhost:8080`              | Backend API base URL (or `http://localhost:80` via Nginx) |
| `organizer_email`          | `organizer.techlead@unihub.edu.vn`   | Primary organizer email                                   |
| `organizer_password`       | `StrongPass123!`                     | Primary organizer password                                |
| `organizer_token`          | _(Auto-captured)_                    | Bearer JWT token for primary organizer                    |
| `organizer_org_id`         | _(Auto-captured)_                    | Organization UUID of the primary organizer                |
| `other_organizer_email`    | `organizer.business@unihub.edu.vn`   | Organizer from a _different_ organization (tenant)        |
| `other_organizer_password` | `StrongPass123!`                     | Password for other tenant organizer                       |
| `other_organizer_token`    | _(Auto-captured)_                    | Bearer JWT token for other tenant organizer               |
| `student_email`            | `student.alice@student.hcmus.edu.vn` | Active student account email                              |
| `student_password`         | `StrongPass123!`                     | Student account password                                  |
| `student_token`            | _(Auto-captured)_                    | Bearer JWT token for student                              |
| `workshop_id`              | _(Auto-captured)_                    | Target workshop UUID created during test setup            |
| `workshop_version`         | _(Auto-captured)_                    | Target workshop version integer                           |
| `draft_workshop_id`        | _(Auto-captured)_                    | Workshop UUID left in DRAFT status for negative tests     |

## 3. Prerequisite Setup Requests (Run in Order)

Before running the cancellation test cases, execute these setup requests to prepare test accounts and sample workshops.

### Step 0.1: Auth — Organizer Login (Primary Tenant)

- **Method:** `POST`
- **URL:** `{{base_url}}/api/v1/auth/login`
- **Headers:** `Content-Type: application/json`
- **Body:**
  ```json
  {
    "email": "{{organizer_email}}",
    "password": "{{organizer_password}}"
  }
  ```
- **Tests Tab Script:**
  ```javascript
  pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
  });
  var jsonData = pm.response.json();
  if (jsonData.data && jsonData.data.accessToken) {
    pm.environment.set("organizer_token", jsonData.data.accessToken);
  }
  ```

### Step 0.2: Auth — Other Tenant Organizer Login

- **Method:** `POST`
- **URL:** `{{base_url}}/api/v1/auth/login`
- **Headers:** `Content-Type: application/json`
- **Body:**
  ```json
  {
    "email": "{{other_organizer_email}}",
    "password": "{{other_organizer_password}}"
  }
  ```
- **Tests Tab Script:**
  ```javascript
  pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
  });
  var jsonData = pm.response.json();
  if (jsonData.data && jsonData.data.accessToken) {
    pm.environment.set("other_organizer_token", jsonData.data.accessToken);
  }
  ```

### Step 0.3: Auth — Student Login (Non-Organizer Role)

- **Method:** `POST`
- **URL:** `{{base_url}}/api/v1/auth/login`
- **Headers:** `Content-Type: application/json`
- **Body:**
  ```json
  {
    "email": "{{student_email}}",
    "password": "{{student_password}}"
  }
  ```
- **Tests Tab Script:**
  ```javascript
  pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
  });
  var jsonData = pm.response.json();
  if (jsonData.data && jsonData.data.accessToken) {
    pm.environment.set("student_token", jsonData.data.accessToken);
  }
  ```

### Step 0.4: Setup — Create Draft Workshop (Primary Workshop)

- **Method:** `POST`
- **URL:** `{{base_url}}/api/v1/admin/workshops/drafts`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer {{organizer_token}}`
- **Body:**
  ```json
  {
    "title": "Clean Architecture & Event-Driven Monoliths",
    "description": "Comprehensive workshop on Spring Modulith and DDD.",
    "location": "Hall A, Science Campus",
    "timezone": "Asia/Ho_Chi_Minh",
    "startsAt": "2026-11-20T09:00:00Z",
    "endsAt": "2026-11-20T12:00:00Z",
    "capacity": 150,
    "price": 0,
    "currency": "USD"
  }
  ```
- **Tests Tab Script:**
  ```javascript
  pm.test("Status code is 201", function () {
    pm.response.to.have.status(201);
  });
  var jsonData = pm.response.json();
  if (jsonData.data && jsonData.data.id) {
    pm.environment.set("workshop_id", jsonData.data.id);
    pm.environment.set("workshop_version", jsonData.data.version);
  }
  ```

### Step 0.5: Setup — Publish Primary Workshop

- **Method:** `PATCH`
- **URL:** `{{base_url}}/api/v1/admin/workshops/{{published_workshop_id}}/publish`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer {{organizer_token}}`
- **Body:**
  ```json
  {
    "version": {{workshop_version}}
  }
  ```
- **Tests Tab Script:**
  ```javascript
  pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
  });
  var jsonData = pm.response.json();
  if (jsonData.data && jsonData.data.version) {
    pm.environment.set("workshop_version", jsonData.data.version);
  }
  ```

### Step 0.6: Setup — Create Second Workshop Left in DRAFT Status

- **Method:** `POST`
- **URL:** `{{base_url}}/api/v1/admin/workshops/drafts`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer {{organizer_token}}`
- **Body:**
  ```json
  {
    "title": "Unpublished Draft Workshop for Negative Tests",
    "capacity": 50
  }
  ```
- **Tests Tab Script:**
  ```javascript
  pm.test("Status code is 201", function () {
    pm.response.to.have.status(201);
  });
  var jsonData = pm.response.json();
  if (jsonData.data && jsonData.data.id) {
    pm.environment.set("draft_workshop_id", jsonData.data.id);
  }
  ```

## 4. Test Suite Execution (`PATCH /api/v1/admin/workshops/{id}/cancel`)

### Request 1: `Happy Path — Cancel Published Workshop with Reason`

- **Method:** `PATCH`
- **URL:** `{{base_url}}/api/v1/admin/workshops/{{published_workshop_id}}/cancel`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer {{organizer_token}}`
- **Body:**
  ```json
  {
    "version": {{workshop_version}},
    "reason": "Auditorium emergency electrical inspection scheduled by campus facilities."
  }
  ```
- **Tests Tab Script:**

```javascript
pm.test("Status code is 200", function () {
  pm.response.to.have.status(200);
});

pm.test("Envelope structure conforms to API standards", function () {
  var jsonData = pm.response.json();
  pm.expect(jsonData.success).to.be.true;
  pm.expect(jsonData.message).to.eql("Workshop canceled successfully.");
  pm.expect(jsonData.metadata).to.be.an("object");
  pm.expect(jsonData.metadata.traceId).to.exist;
  pm.expect(jsonData.metadata.processedBy).to.exist;
});

pm.test(
  "Response contains CANCELED status and canceledAt timestamp",
  function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.data.id).to.eql(pm.environment.get("workshop_id"));
    pm.expect(jsonData.data.status).to.eql("CANCELED");
    pm.expect(jsonData.data.canceledAt).to.be.a("string");
    pm.expect(jsonData.data.version).to.eql(
      pm.environment.get("workshop_version") + 1,
    );

    // Update workshop_version for terminal state tests
    pm.environment.set("workshop_version", jsonData.data.version);
  },
);
```

### Request 2: `Validation Error — Already Canceled Workshop Cannot Be Canceled`

- **Method:** `PATCH`
- **URL:** `{{base_url}}/api/v1/admin/workshops/{{published_workshop_id}}/cancel`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer {{organizer_token}}`
- **Body:**
  ```json
  {
    "version": {{workshop_version}},
    "reason": "Attempting second cancellation."
  }
  ```
- **Tests Tab Script:**
```javascript
pm.test("Status code is 422", function () {
  pm.response.to.have.status(422);
});

pm.test("RFC 7807 validation error structure", function () {
  var jsonData = pm.response.json();
  pm.expect(jsonData.success).to.be.false;
  pm.expect(jsonData.error.type).to.eql("urn:unihub:error:validation");
  pm.expect(jsonData.error.status).to.eql(422);
  pm.expect(jsonData.error.validationErrors).to.be.an("array").that.is.not
    .empty;
  pm.expect(jsonData.error.validationErrors[0]).to.include(
    "Workshops in terminal status (CANCELED) cannot be canceled.",
  );
});
```

### Request 3: `Validation Error — Workshop in DRAFT Status`

- **Method:** `PATCH`
- **URL:** `{{base_url}}/api/v1/admin/workshops/{{draft_workshop_id}}/cancel`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer {{organizer_token}}`
- **Body:**
  ```json
  {
    "version": 0,
    "reason": "Trying to cancel an unpublished draft."
  }
  ```
- **Tests Tab Script:**
```javascript
pm.test("Status code is 422", function () {
  pm.response.to.have.status(422);
});

pm.test(
  "RFC 7807 validation error directing to delete endpoint",
  function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.success).to.be.false;
    pm.expect(jsonData.error.type).to.eql("urn:unihub:error:validation");
    pm.expect(jsonData.error.status).to.eql(422);
    pm.expect(jsonData.error.validationErrors[0]).to.include(
      "Workshop is in DRAFT status. Please use DELETE",
    );
  },
);
```

### Request 4: `Validation Error — Missing Required Version Field`

- **Method:** `PATCH`
- **URL:** `{{base_url}}/api/v1/admin/workshops/{{published_workshop_id}}/cancel`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer {{organizer_token}}`
- **Body:**
  ```json
  {
    "reason": "Missing version payload key."
  }
  ```
- **Tests Tab Script:**
```javascript
pm.test("Status code is 422", function () {
  pm.response.to.have.status(422);
});

pm.test(
  "RFC 7807 validation error identifies version requirement",
  function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.success).to.be.false;
    pm.expect(jsonData.error.type).to.eql("urn:unihub:error:validation");
    pm.expect(jsonData.error.status).to.eql(422);
    pm.expect(jsonData.error.validationErrors).to.be.an("array");
  },
);
```

### Request 5: `Validation Error — Prohibited Field Present (title)`

- **Method:** `PATCH`
- **URL:** `{{base_url}}/api/v1/admin/workshops/{{published_workshop_id}}/cancel`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer {{organizer_token}}`
- **Body:**
  ```json
  {
    "version": 1,
    "title": "Renamed Title Before Canceling"
  }
  ```
- **Tests Tab Script:**
```javascript
pm.test("Status code is 422", function () {
  pm.response.to.have.status(422);
});

pm.test("RFC 7807 validation error rejects prohibited field", function () {
  var jsonData = pm.response.json();
  pm.expect(jsonData.success).to.be.false;
  pm.expect(jsonData.error.type).to.eql("urn:unihub:error:validation");
  pm.expect(jsonData.error.status).to.eql(422);
  pm.expect(jsonData.error.validationErrors[0]).to.include(
    "title: Field is prohibited on cancel.",
  );
});
```

### Request 6: `Validation Error — Cancellation Reason Exceeds 1000 Characters`

- **Method:** `PATCH`
- **URL:** `{{base_url}}/api/v1/admin/workshops/{{published_workshop_id}}/cancel`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer {{organizer_token}}`
- **Pre-request Script:**
  ```javascript
  var longReason = "a".repeat(1005);
  pm.variables.set("excessive_reason", longReason);
  ```
- **Body:**
  ```json
  {
    "version": 1,
    "reason": "{{excessive_reason}}"
  }
  ```
- **Tests Tab Script:**
```javascript
pm.test("Status code is 422", function () {
  pm.response.to.have.status(422);
});

pm.test(
  "RFC 7807 validation error for reason length constraint",
  function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.success).to.be.false;
    pm.expect(jsonData.error.type).to.eql("urn:unihub:error:validation");
    pm.expect(jsonData.error.status).to.eql(422);
    pm.expect(jsonData.error.validationErrors[0]).to.include(
      "1000 characters",
    );
  },
);
```

### Request 7: `Conflict — Stale Version Optimistic Lock Conflict`

_(Note: Setup a fresh published workshop or use stale version number on an existing workshop)_

- **Method:** `PATCH`
- **URL:** `{{base_url}}/api/v1/admin/workshops/{{published_workshop_id}}/cancel`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer {{organizer_token}}`
- **Body:**
  ```json
  {
    "version": 999
  }
  ```
- **Tests Tab Script:**
```javascript
pm.test("Status code is 409", function () {
  pm.response.to.have.status(409);
});

pm.test("RFC 7807 conflict error structure", function () {
  var jsonData = pm.response.json();
  pm.expect(jsonData.success).to.be.false;
  pm.expect(jsonData.error.type).to.eql("urn:unihub:error:conflict");
  pm.expect(jsonData.error.status).to.eql(409);
  pm.expect(jsonData.error.detail).to.include("conflict");
});
```

### Request 8: `Not Found — Non-Existent Workshop UUID`

- **Method:** `PATCH`
- **URL:** `{{base_url}}/api/v1/admin/workshops/00000000-0000-0000-0000-000000000000/cancel`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer {{organizer_token}}`
- **Body:**
  ```json
  {
    "version": 1
  }
  ```
- **Tests Tab Script:**
```javascript
pm.test("Status code is 404", function () {
  pm.response.to.have.status(404);
});

pm.test("RFC 7807 not-found error structure", function () {
  var jsonData = pm.response.json();
  pm.expect(jsonData.success).to.be.false;
  pm.expect(jsonData.error.type).to.eql("urn:unihub:error:not-found");
  pm.expect(jsonData.error.status).to.eql(404);
});
```

### Request 9: `Forbidden — Cross-Tenant Organizer Access Attempt`

- **Method:** `PATCH`
- **URL:** `{{base_url}}/api/v1/admin/workshops/{{published_workshop_id}}/cancel`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer {{other_organizer_token}}`
- **Body:**
  ```json
  {
    "version": 1
  }
  ```
- **Tests Tab Script:**
  ```javascript
  pm.test("Status code is 403", function () {
    pm.response.to.have.status(403);
  });

  pm.test("RFC 7807 forbidden error structure", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.success).to.be.false;
    pm.expect(jsonData.error.type).to.eql("urn:unihub:error:forbidden");
    pm.expect(jsonData.error.status).to.eql(403);
  });
  ```

### Request 10: `Forbidden — Non-Organizer Role Access Attempt (Student)`

- **Method:** `PATCH`
- **URL:** `{{base_url}}/api/v1/admin/workshops/{{published_workshop_id}}/cancel`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer {{student_token}}`
- **Body:**
  ```json
  {
    "version": 1
  }
  ```
- **Tests Tab Script:**
  ```javascript
  pm.test("Status code is 403", function () {
    pm.response.to.have.status(403);
  });

  pm.test("RFC 7807 forbidden error structure", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.success).to.be.false;
    pm.expect(jsonData.error.type).to.eql("urn:unihub:error:forbidden");
    pm.expect(jsonData.error.status).to.eql(403);
  });
  ```

### Request 11: `Unauthorized — Missing Bearer Authorization Header`

- **Method:** `PATCH`
- **URL:** `{{base_url}}/api/v1/admin/workshops/{{published_workshop_id}}/cancel`
- **Headers:**
  - `Content-Type: application/json`
  - _(Omit `Authorization` header)_
- **Body:**
  ```json
  {
    "version": 1
  }
  ```
- **Tests Tab Script:**
```javascript
pm.test("Status code is 401", function () {
  pm.response.to.have.status(401);
});

pm.test("RFC 7807 unauthorized error structure", function () {
  var jsonData = pm.response.json();
  pm.expect(jsonData.success).to.be.false;
  pm.expect(jsonData.error.type).to.eql("urn:unihub:error:unauthorized");
  pm.expect(jsonData.error.status).to.eql(401);
});
```

### Request 12: `Bad Request — Malformed JSON Request Body`

- **Method:** `PATCH`
- **URL:** `{{base_url}}/api/v1/admin/workshops/{{published_workshop_id}}/cancel`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer {{organizer_token}}`
- **Body:** (Raw text with syntax error)
  ```
  { "version": 1, "reason":
  ```
- **Tests Tab Script:**
```javascript
pm.test("Status code is 400", function () {
  pm.response.to.have.status(400);
});

pm.test("RFC 7807 bad-request error structure", function () {
  var jsonData = pm.response.json();
  pm.expect(jsonData.success).to.be.false;
  pm.expect(jsonData.error.type).to.eql("urn:unihub:error:bad-request");
  pm.expect(jsonData.error.status).to.eql(400);
});
```

### Request 13: `Unsupported Media Type — Text Plain Content-Type`

- **Method:** `PATCH`
- **URL:** `{{base_url}}/api/v1/admin/workshops/{{published_workshop_id}}/cancel`
- **Headers:**
  - `Content-Type: text/plain`
  - `Authorization: Bearer {{organizer_token}}`
- **Body:**
  ```
  version=1
  ```
- **Tests Tab Script:**
  ```javascript
  pm.test("Status code is 415", function () {
    pm.response.to.have.status(415);
  });

  pm.test("RFC 7807 unsupported-media-type error structure", function () {
    var jsonData = pm.response.json();
    pm.expect(jsonData.success).to.be.false;
    pm.expect(jsonData.error.type).to.eql(
      "urn:unihub:error:unsupported-media-type",
    );
    pm.expect(jsonData.error.status).to.eql(415);
  });
  ```

## 5. Verification Checklist

When executing this test suite, verify the following:

- [ ] Every request passes its automated assertions in the Postman `Tests` tab.
- [ ] For successful cancellations (`200 OK`), verify that `data.status` is `"CANCELED"`, and `data.canceledAt` is populated.
- [ ] Verify that optimistic lock increments the version number.
- [ ] In PostgreSQL `workshops` table, verify that column `status` = `'CANCELED'`, `canceled_at` is set, and no `reason` column was added.
- [ ] In RabbitMQ or application logs, verify that `WorkshopCanceledEvent` was published with exchange `unihub.events` and routing key `workshop.canceled`.
