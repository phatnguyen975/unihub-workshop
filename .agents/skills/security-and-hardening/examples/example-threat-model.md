# Worked Example: Threat Modeling a Payment Webhook Handler

> [!NOTE]
> This is an illustrative, domain-neutral example showing how to apply this skill. It threat-models a webhook handler that receives payment outcome events from an external payment provider — extending the "resource reservation" example used across the other skill examples to a paid-reservation variant where a payment must complete before the reservation is confirmed. Pseudocode is used deliberately, with no specific language, framework, or provider-specific syntax, so this stays applicable regardless of your actual stack.

## Step 1 — Map the Trust Boundaries

This feature — `POST /api/v1/payments/webhook`, receiving payment outcome events from an external payment provider — has exactly one external trust boundary, but it's worth being precise about what crosses it:

- **Boundary:** the HTTP request body and headers delivered by the payment provider to this endpoint.
- **What crosses it:** an event payload claiming a specific payment succeeded or failed, plus a signature header the provider attaches.

It's tempting to think of this as "internal" because it's a server-to-server call with no end user directly involved — but the endpoint is reachable by anyone who discovers its URL, not only the real provider. That's exactly the trust boundary that matters here.

## Step 2 — Run STRIDE

| Category                   |   Applies?    | Analysis                                                                                                                                                                           | Mitigation                                                                                                                                                                                     |
| :------------------------- | :-----------: | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **S**poofing               |      Yes      | Anyone who finds this URL can POST a fabricated event claiming a payment succeeded, with no signature check, the handler would believe them                                        | Verify the provider's signature on every request, using the provider's signing secret (Core Principle #8)                                                                                      |
| **T**ampering              |      Yes      | An attacker intercepting or replaying a legitimate-looking request could alter the amount or status fields                                                                         | The signature covers the full payload; any tampering invalidates it. Idempotency keyed on the provider's event ID also prevents a captured, unmodified replay from re-triggering the operation |
| **R**epudiation            | Low relevance | The provider is the actor here, not an end user with an account on this system; there's no "did the provider really send this" dispute scenario typical of user-facing repudiation | Logging the verified event ID and outcome is still kept for audit, but this isn't the primary concern at this boundary                                                                         |
| **I**nformation Disclosure |      Yes      | An invalid-signature response that includes detail about _why_ it failed (wrong secret format, missing header, etc.) helps an attacker iterate toward a forged signature           | Reject invalid signatures with a generic response; log full diagnostic detail only internally                                                                                                  |
| **D**enial of Service      |      Yes      | A flood of fabricated or replayed requests to this public endpoint could consume processing capacity                                                                               | Reject unverified requests as early and cheaply as possible (signature check first, before any expensive work); rate-limit at the edge if this project's infrastructure supports it            |
| **E**levation of Privilege |      Yes      | A forged "payment succeeded" event would let an attacker confirm a reservation without ever actually paying — privilege gained: a paid resource for free                           | Same mitigation as Spoofing — the entire business-logic effect of this endpoint depends on signature verification actually happening, every time                                               |

## Step 3 — Write the Abuse Case

**Abuse case:** _"An attacker who has discovered this webhook URL (e.g. from a leaked log, a misconfigured proxy, or simple guessing of a common path) sends a fabricated `payment_intent.succeeded` event for an arbitrary reservation ID, attempting to get that reservation confirmed without any real payment having occurred."_

This becomes the first test written for this endpoint — before the happy-path "valid signature, payment succeeded" test:

```
test "rejects a webhook event with an invalid signature, and does not confirm the reservation":
    forgedEvent = buildPaymentSucceededEvent(reservationId: "some-real-reservation-id")
    forgedSignatureHeader = "sig=totally-made-up"

    response = postWebhook(forgedEvent, signatureHeader: forgedSignatureHeader)

    expect: response.statusCode == 400  // generic rejection, no detail on *why*
    expect: reservationStatus("some-real-reservation-id") != "CONFIRMED"
    expect: no payment record was created or modified
```

## Step 4 — Apply the Relevant Checklist Sections

From `SECURITY_CHECKLIST.md`, the **Webhook Handling** and **Payment
Integration** sections both apply here. Key items pulled in:

- Signature verified on every delivery, using a secret the sender doesn't control. ✅ (designed in Step 2)
- Signing secret stored as a protected configuration value. ✅
- Processing is idempotent against redelivery — handled by `idempotency-and-exactly-once-design`, scoped on the provider's own event ID (already covered in that skill's worked example for this exact endpoint family).
- No raw payment credential logged — this event payload from the provider does not carry a raw card number, so this is not a concern for this specific payload shape, but is confirmed rather than assumed.

## Step 5 — Design the Fail Mode

The only external dependency this control relies on is the signature-verification mechanism itself — and verification doesn't depend on anything that can be "unavailable" the way a cache or a remote service can; it's a local cryptographic computation against a configured secret. There is, deliberately, no fail-open path here: if the secret is missing or misconfigured such that verification cannot be performed at all, the handler must fail closed (reject) — there is no scenario where skipping signature verification is an acceptable degraded mode for an endpoint whose entire purpose is to confirm financial outcomes.

```
function handleWebhook(request):
    if not paymentProviderSigningSecret.isConfigured():
        // Fail closed, no exceptions: an endpoint that confirms payment
        // outcomes must never process unverified payloads, regardless
        // of operational convenience.
        log.critical("Webhook signing secret missing — rejecting all webhook traffic")
        return response(500) // alerts on-call; does not silently accept unverified events

    if not verifySignature(request.body, request.headers["Provider-Signature"], paymentProviderSigningSecret):
        return response(400, genericRejectionBody) // no detail on why it failed

    // proceed with idempotent, signature-verified processing
```

## Step 6 — Verify Secret Handling

- The signing secret is read from this project's environment/secret configuration mechanism, never embedded in the verification function.
- A search of the diff introducing this handler confirms no literal secret value, no example/test secret that resembles a real one closely enough to be mistaken for one, and no debug log line that would print the secret or the raw signature header value.

## What This Example Demonstrates

- The trust boundary was named precisely, including the explicit observation that "server-to-server" does not mean "not externally reachable."
- STRIDE surfaced Information Disclosure as a real concern specifically in the _shape of the rejection response_, not just in "is data leaked" — a subtlety easy to miss without walking the category deliberately.
- The abuse case became an actual test, written and intended to be the first one implemented for this endpoint.
- The fail-mode decision for signature verification was made deliberately fail-closed, with explicit reasoning tied to this control's specific risk (financial confirmation) — not a default copied from elsewhere in the system.
- This example deliberately cross-references `idempotency-and-exactly-once-design` rather than re-deriving its content, consistent with this skill applying alongside that one rather than duplicating it.
