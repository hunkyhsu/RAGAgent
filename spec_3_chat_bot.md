# SPEC-3-ChatBot Minimal MVP Checklist

> Goal: A minimal, shippable MVP with strict scope control. Anything not listed is deferred.

## 0) Scope
- Single bot, single region, single instance.
- No multi-tenant, no SSO, no complex ops or audit.

---

## 1) Must Have (MVP)

### 1.1 WebSocket Chat
- Single WS endpoint `/ws/chat`.
- JSON envelope with minimal fields: `type`, `conversationId`, `messageId`, `role`, `content`, `ts`.
- Server streams `chat.stream` and ends with `chat.done`.

### 1.2 Persistence
- MySQL with R2DBC for `users`, `conversations`, `messages`.
- Store only full messages (no token-level storage).
- Minimal schema with `conversation` ownership.

### 1.3 Auth
- Email + password login.
- JWT access token only (no refresh token).
- Guest mode disabled in MVP to avoid model conflicts.

### 1.4 Basic Rate Limit
- Redis token bucket per IP only.
- Simple error response `error{code:RLIMIT}`.

### 1.5 LLM Provider
- One provider only (OpenAI) with streaming output.
- One system prompt configured via env.

### 1.6 Frontend MVP
- React + Vite + Tailwind.
- Login page + chat page.
- Sidebar conversation list + main chat window.
- Stream rendering with auto-scroll.

---

## 2) Deferred (Out of Scope)

- Refresh token, logout sessions, role-based access, admin exports.
- Redis hot cache and replay/resume.
- Audit log, i18n, moderation, advanced observability.
- Advanced error taxonomy and state machine.
- Docker compose (optional, not required for MVP).

---

## 3) Minimal Data Model

- `users(id, email, password_bcrypt, created_at)`
- `conversations(id, user_id, title, created_at)`
- `messages(id, conv_id, role, content, created_at)`

---

## 4) Minimal API Surface

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/conversations`
- `GET /api/conversations`
- `GET /api/conversations/{id}/messages`
- `WS /ws/chat?convId=...`

---

## 5) Acceptance Criteria

- User can register and login, then create a conversation.
- Sending a message begins streaming within a reasonable time (< 2s typical).
- Reloading page shows conversation history from DB.
- Rate limit triggers and returns `RLIMIT` after burst.

