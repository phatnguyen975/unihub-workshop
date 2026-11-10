# Experience Map: <Platform>

> **Status:** <DRAFT|APPROVED>  
> **Revision:** <revision>  
> **Approval:** <explicit human decision/date/scope; pending until granted>

## Product References and Roles

Link product feature IDs and scoped ADRs; identify actors, goals and access boundaries. Separate established requirements from questions.

## Scope and Exclusions

Declare the platform, included journeys and explicit exclusions. Supporting UX must enable an included goal; new business powers or money flows require a product decision.

## Priority Journeys and Navigation

For each journey give its trigger, entry point, ordered screens/actions, successful end state and recovery exits. Identify shared navigation and cross-capability dependencies.

## Screen and State Inventory

Assign stable screen/scenario IDs. Include applicable initial, loading, empty, success, validation, failure, permission and offline states; explain exclusions rather than adding irrelevant states.

| Journey | Screen/state | Actor | Expected outcome |
| ------- | ------------ | ----- | ---------------- |

## Capabilities and Dependencies

Use stable capability IDs, product feature references, priority and dependency IDs. Label supporting UX without inventing a product feature ID. Detect cycles before planning implementation.

| Capability | Core feature reference | Supporting UX | Priority | Depends on |
| ---------- | ---------------------- | ------------- | -------- | ---------- |

## Decisions Requiring Human Input

Record the question, options, affected journeys and owner. Mark resolved choices with the explicit decision; unresolved material scope questions block dependent work.
