# SPEC-2-ChatBot Production Gap Fill Checklist

> Goal: Convert SPEC-1 MVP into a production-ready, complete ChatBot system by filling gaps, resolving conflicts, and adding missing operational details.

## 0) Scope and Principles
- Keep existing architecture (Spring Boot WebFlux + WebSocket + MySQL R2DBC + Redis + React).
- Add missing production requirements and remove ambiguity.
- All items below must be resolvable to concrete implementation and acceptance criteria.

---

## 1) Core Logic and Data Model Fixes (Critical)

### 1.1 Guest Mode vs Data Model
- Decide one model:
  - Option A: Create a dedicated guest user record per session (or shared guest pool) and use `user_id` as NOT NULL.
  - Option B: Allow `user_id` NULL and add `is_guest` + `guest_id` (or `anon_id`) columns with strict ACL rules.
- Update schema, indexes, and authorization rules accordingly.
- Update all endpoints (REST/WS) to enforce consistent ownership rules.

### 1.2 Idempotency and Replay Window
- Define idempotency TTL aligned with recovery requirements (e.g., >= message retention or >= session cache TTL).
- Specify exact recovery behavior when TTL expired (return error vs. re-generate).
- Add DB fallback query by `message_id` to support recovery if Redis keys expired.

### 1.3 WS Subprotocol Handshake
- Server must return the negotiated `Sec-WebSocket-Protocol` value, or the connection may be rejected by the client or proxy.
- Define failure behavior when invalid/expired token is provided.

---

## 2) Security, Auth, and Session Lifecycle

### 2.1 JWT and Refresh Tokens
- Define refresh token storage (DB table + indexed columns) and rotation policy.
- Define revoke/blacklist strategy and concurrency rules (multi-login, logout).
- Define token expiry times and error codes.

### 2.2 Secret Management
- Define required secrets and their sources (env vars, vault, KMS).
- Define rotation and redeploy process.

### 2.3 Input Validation and Abuse Prevention
- Define max message length, max tokens, rate limits per user and per IP.
- Define spam detection / duplicate prevention rules.
- Define invalid payload handling and close codes.

---

## 3) WebSocket Protocol and State Machine

### 3.1 Envelope Spec
- Fix allowed values for `type`, `role`, `error.code`.
- Add `requestId` and `traceId` for troubleshooting.
- Define strict schema validation and error response for invalid payloads.

### 3.2 State Machine
- Define allowed transitions:
  - `chat.send` -> `chat.stream` -> `chat.done`
  - errors (retryable vs non-retryable)
- Define how to handle concurrent sends within one conversation.

### 3.3 Reconnect and Resume
- Define how `lastMessageSeq` is computed, stored, and replayed.
- Define behavior when stream was interrupted mid-response.
- Define max replay window and fallback to full history fetch.

---

## 4) Reliability and Performance

### 4.1 Timeouts and Retries
- Define LLM call timeout, upstream retry policy (max retries, backoff).
- Define when to fail-fast and how to inform clients.

### 4.2 Backpressure and Buffering
- Define buffering limits and drop policy (per connection).
- Define response truncation behavior when limits are hit.

### 4.3 Performance Objectives
- Replace external-dependent SLO ("first token <= 1s") with internal SLO:
  - "server dispatch latency" <= X ms
  - "token relay latency" <= Y ms
- Keep external LLM latency as best-effort with clarity in acceptance criteria.

---

## 5) Observability and Audit

### 5.1 Metrics
- Final list of metrics with names, tags, and units.
- Separate metrics for error classes (UPSTREAM/SERVER/VALIDATION/RLIMIT).

### 5.2 Logging
- Define structured log fields: `traceId`, `userId`, `convId`, `messageId`.
- Define PII redaction rules (mask emails, tokens).

### 5.3 Audit Trail
- Define what actions are audited and retention period.
- Define query or export interface for audit events.

---

## 6) Data Retention and Compliance

- Define retention job schedule and implementation (batch delete, soft delete policy).
- Define data export scope and format for GDPR-lite requests.
- Define schema for deleted records (soft delete columns).

---

## 7) Deployment and Ops

### 7.1 Rolling Upgrades and WS Disconnects
- Define how to drain connections and re-route in rolling deploys.

### 7.2 Backup and Recovery
- Define MySQL backup strategy, restore test frequency.
- Define Redis backup / persistence configuration.

### 7.3 Capacity and Limits
- Define max concurrent WS per instance and per user.
- Define memory and CPU budgets for token streaming.

---

## 8) Testing and Validation

- Define unit, integration, and load test coverage targets.
- Include a mandatory end-to-end flow test:
  - register -> login -> create conv -> WS stream -> persist -> replay
- Add chaos tests for LLM timeout and Redis unavailability.

---

## 9) Acceptance Criteria Updates

- Add acceptance criteria for all new requirements above.
- Ensure each requirement is testable with clear expected results.

---

## 10) Spec Updates Needed in SPEC-1

- Update Guest Mode section and data model.
- Add WS subprotocol negotiation details.
- Add explicit token/session lifecycle.
- Add reconnect/resume algorithm with edge cases.
- Add operational requirements (backups, rotations, upgrades).

