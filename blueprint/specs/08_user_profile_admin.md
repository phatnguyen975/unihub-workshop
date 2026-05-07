# UniHub Workshop - User Profile & Admin Provisioning Specs

## 1. Get My Profile

Defines retrieval of the current user's profile details.

| Field               | Details                                                                                                                                     |
| :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------ |
| Use Case Name       | Get My Profile                                                                                                                              |
| Description         | Retrieves the authenticated user's profile data scoped by identity.                                                                         |
| Actors              | Student, Organizer, Staff                                                                                                                   |
| Main Flow           | 1. Resolve user identity from Security Context (`jwt.sub`).<br>2. Query user profile by the resolved user ID.<br>3. Return profile details. |
| Error Scenarios     | - Unauthorized access returns 401.<br>- User not found returns 404.                                                                         |
| Constraints         | - Query must be strictly scoped to `jwt.sub`.<br>- No cross-user profile access is allowed.                                                 |
| Acceptance Criteria | - Response contains only the caller's profile data.<br>- Non-authenticated requests are rejected.                                           |

## 2. Update My Profile

Defines update of basic user profile information.

| Field               | Details                                                                                                                                                                                               |
| :------------------ | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | Update My Profile                                                                                                                                                                                     |
| Description         | Updates basic profile details for the authenticated user.                                                                                                                                             |
| Actors              | Student, Organizer, Staff                                                                                                                                                                             |
| Main Flow           | 1. Resolve user identity from Security Context (`jwt.sub`).<br>2. Validate input fields (name, phone).<br>3. Update user profile for the resolved user ID only.<br>4. Return updated profile details. |
| Error Scenarios     | - Unauthorized access returns 401.<br>- Invalid input returns 400.<br>- User not found returns 404.                                                                                                   |
| Constraints         | - Only name and phone are mutable.<br>- Role and password updates are not allowed in this endpoint.                                                                                                   |
| Acceptance Criteria | - Updates apply only to the caller's profile.<br>- Restricted fields are never modified.                                                                                                              |

## 3. Provision Internal Account

Defines organizer provisioning of staff accounts.

| Field               | Details                                                                                                                                                                                                                                                                                                              |
| :------------------ | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Use Case Name       | Provision Internal Account                                                                                                                                                                                                                                                                                           |
| Description         | Creates staff accounts and assigns them to an organization.                                                                                                                                                                                                                                                          |
| Actors              | Organizer                                                                                                                                                                                                                                                                                                            |
| Main Flow           | 1. Validate input payload (name, email, organization_id).<br>2. Enforce organizer authorization for provisioning.<br>3. Create staff account with a temporary password and assign organization.<br>4. Emit a Welcome Email outbox event with the temporary password.<br>5. Return the created staff account details. |
| Error Scenarios     | - Unauthorized access returns 401.<br>- Invalid input returns 400.<br>- Organization not found returns 404.<br>- Duplicate email returns 409.                                                                                                                                                                        |
| Constraints         | - Only Organizers can provision staff accounts.<br>- Temporary passwords must be one-time and expired after first login.                                                                                                                                                                                             |
| Acceptance Criteria | - Staff account is created with organization assignment.<br>- Welcome Email outbox event is emitted.                                                                                                                                                                                                                 |
