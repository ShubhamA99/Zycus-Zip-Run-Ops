# Architecture Decision Records

This document captures key architectural decisions made during the development of the AI Reassignment Engine.

---

## ADR-1: Where does routing logic live?

### Context
The system needs to recommend agents for order reassignment. The recommendation logic could live in multiple places: directly in controllers, within domain entities, in a service layer, or in dedicated strategy components. The same logic must be callable from both synchronous HTTP requests and asynchronous event handlers (agentic re-planning loop). Sprint 2 will add `ZoneAffinityStrategy`, so the location must support adding new algorithms without touching existing code.

### Options considered
**(a) Controller layer** — Simple for MVP, but couples HTTP concerns with business logic. Adding async caller means duplicating logic or extracting later.

**(b) Domain entity (Order.findBestAgent())** — Rich domain model approach, but routing depends on external agent roster and config, making the entity heavy with dependencies.

**(c) Application Service with embedded logic** — Single service class holding all routing variants. Quick to build but becomes a god class as strategies multiply.

**(d) Strategy Pattern in dedicated `routing` package + thin Application Service** — Interface defines contract, each algorithm is a separate class, service orchestrates selection. Clear boundaries, testable in isolation.

### Decision
Chose **(d)** — Strategy Pattern with Application Service orchestration.

- `RoutingStrategy` interface in `com.ziprun.reassignment.routing` defines the contract
- `RuleBasedRoutingStrategy` and `AIRoutingStrategy` are separate implementations
- `RoutingService` (Application Service) handles strategy selection via `ConfigService`
- `ReplanningService` reuses the same strategies for async re-planning

This keeps routing algorithms isolated, testable, and allows both HTTP (`RoutingService`) and async (`ReplanningService`) callers to use identical logic without duplication.

### Tradeoffs accepted
- More files than embedding logic in a single service (3 routing classes + 2 service classes vs 1 monolithic service)
- Slight indirection: caller must go through service → strategy rather than direct method call
- Strategy implementations must be Spring beans for auto-wiring to work, coupling them to the framework

---

## ADR-2: How does runtime strategy switching work?

### Context
The routing engine supports multiple strategies — rule-based and AI — and the active strategy needs to be switchable at runtime without a restart. The same routing contract is called from two places: an HTTP endpoint (`POST /orders/{id}/suggest`) and an async event handler (`ReplanningService` in the agentic loop). Sprint 2 will introduce `ZoneAffinityStrategy`, so the mechanism must accommodate new strategies without changing existing code.

### Options considered
**(a) Spring `@Qualifier` with application.properties** — Straightforward but requires restart to switch. Doesn't support true runtime changes.

**(b) Auto-wired `Map<String, RoutingStrategy>` + application.properties** — Spring auto-populates map by bean name. Strategy read from config at call time. Requires env var change or restart.

**(c) Auto-wired `Map<String, RoutingStrategy>` + database-backed config** — Same as (b), but strategy name stored in `AppConfig` table. Changeable via API endpoint without any restart.

**(d) Manual factory with switch statement** — Explicit but requires modifying factory every time a new strategy is added.

### Decision
Chose **(c)** — Auto-wired bean map with database-backed `ConfigService`.

- `Map<String, RoutingStrategy>` injected into both `RoutingService` and `ReplanningService`
- `ConfigService.getRoutingStrategy()` reads active strategy from `app_config` table
- `PUT /config/routing-strategy` endpoint allows ops to switch strategies live
- Strategy lookup happens at call time: `strategies.get(configService.getRoutingStrategy())`

Adding `ZoneAffinityStrategy` in sprint 2 requires:
1. Implement `RoutingStrategy` interface
2. Annotate with `@Component("zone-affinity")`
3. Add to `ConfigService.getValidStrategies()` list

No changes to selection logic, no changes to callers.

### Tradeoffs accepted
- Database read on every routing call (mitigated: config table is tiny, queries are fast)
- No compile-time guarantee that configured strategy name exists — invalid name falls back to rule-based with warning log
- Slightly more complex than properties file — but enables true runtime switching via API

---

## ADR-3: How does the system stay healthy when the LLM is unavailable?

### Context
LLM calls fail in multiple ways: network timeouts, quota exhaustion, malformed JSON responses, and hallucinated agent IDs that don't exist. The system must handle all failure modes gracefully. The async re-planning path is especially critical — a failed AI call there should produce a rule-based suggestion, not a silent drop that leaves orders stranded.

### Options considered
**(a) Fail fast, surface error to caller** — Simple but leaves orders without suggestions. Unacceptable for async re-plan where there's no human waiting to retry.

**(b) Single fallback layer in AI strategy** — AI strategy catches exceptions and falls back to rule-based. Works for sync calls but doesn't handle transient failures that might succeed on retry.

**(c) Two-layer resilience: strategy-level fallback + service-level retry with final fallback** — AI strategy handles parsing/validation errors. ReplanningService adds retry with jitter for transient failures, then explicit rule-based fallback, then `FailedReplan` record as last resort.

### Decision
Chose **(c)** — Two-layer resilience.

**Layer 1: `AIRoutingStrategy`**
- Catches `JsonProcessingException` → falls back to rule-based
- Validates agent ID against candidate list → falls back if hallucinated
- Catches all other exceptions (timeout, quota) → falls back to rule-based
- Strips markdown code blocks if LLM wraps JSON in ```

**Layer 2: `ReplanningService` (async re-plan)**
- Configurable retry: 3 attempts with exponential backoff + jitter (500ms base, 300ms jitter)
- After all retries exhausted, explicitly calls rule-based strategy
- If even rule-based fails, creates `FailedReplan` record with `PENDING_MANUAL_REVIEW` status
- Zero silent drops — every order gets a suggestion or a tracked failure

**Observability:**
- All fallbacks logged with reason: `"Falling back to rule-based strategy for order {}"`
- Fallback suggestions marked: `"Fallback (AI unavailable): ..."`
- Failed replans queryable via `FailedReplanRepository`

### Tradeoffs accepted
- Rule-based fallback means degraded recommendation quality, not failure — acceptable for a reassignment system where "any valid agent" beats "no suggestion"
- Retry with jitter adds latency to async path (up to ~2.4s worst case) — acceptable since it's background processing
- `FailedReplan` table requires manual ops attention — but this is the correct behavior when all automated options exhausted

---

## ADR-4: How is the agentic loop triggered and kept off the request path?

### Context
When `PATCH /agents/{id}/status` sets an agent to `OFFLINE`, the system must identify stranded orders and queue reassignment suggestions. The PATCH endpoint must return immediately — re-planning happens asynchronously. The mechanism should be event-driven (fires because something changed, not because a timer ticked) and handle failures without affecting the original request.

### Options considered
**(a) Synchronous inline processing** — Call `ReplanningService` directly in `AgentService.updateStatus()`. Simple but blocks the HTTP response for potentially slow AI calls.

**(b) Scheduled poller** — Background job checks for offline agents every N seconds. Adds latency (up to N seconds delay), doesn't fire on event, and requires tracking "already processed" state.

**(c) `@Async` method call** — Mark replan method as `@Async`. Fire-and-forget but loses transaction context — if the status update rolls back, replan still fires.

**(d) `ApplicationEventPublisher` + `@TransactionalEventListener` + `@Async`** — Publish domain event after status change. Listener fires only after transaction commits. Async execution on dedicated thread pool. Clean separation of concerns.

### Decision
Chose **(d)** — Domain event with transactional listener and async execution.

**Flow:**
1. `AgentService.updateStatus()` saves agent, publishes `AgentOfflineEvent`
2. `PATCH` endpoint returns immediately (event queued, not processed)
3. After transaction commits, `ReplanningEventListener.handleAgentOffline()` fires
4. `@Async("replanExecutor")` runs on dedicated `ThreadPoolTaskExecutor` (2-5 threads, queue of 25)
5. `ReplanningService.replanForAgent()` processes all stranded orders

**Why `@TransactionalEventListener(phase = AFTER_COMMIT)`:**
- If status update transaction rolls back, no replan fires (correct behavior)
- Event handling is decoupled from the HTTP request thread
- No race condition between status persistence and replan reading stale data

**Error handling:**
- All exceptions caught in listener — logged but never propagate to caller
- Individual order failures tracked via `FailedReplan` records (from ADR-3)
- Comprehensive result logging: SUCCESS, PARTIAL, FAILED with IDs

### Tradeoffs accepted
- Event-driven adds indirection — debugging requires tracing event flow rather than direct method calls
- `AFTER_COMMIT` means slight delay before replan starts (transaction must fully commit)
- Thread pool limits concurrent replans — but this is intentional backpressure to avoid overwhelming LLM

---

## ADR-5: What did you design to extend, and what did you deliberately leave for later?

### Context
Sprint 2 adds zone awareness, capacity constraints, and weight classes. Sprint 3 adds proactive SLA-breach detection and a full dispatch board. The current sprint must build a foundation that makes these extensions straightforward — ideally "implement interface and register" rather than "restructure half the codebase."

---

### Part A: Extension Seams

**1. Routing Strategy Interface** → Sprint 2 `ZoneAffinityStrategy`

Location: `com.ziprun.reassignment.routing.RoutingStrategy`

```java
public interface RoutingStrategy {
    RecommendationResult recommendWithContext(Order order, List<Agent> agents, TriggerReason trigger);
    String getStrategyName();
}
```

To add `ZoneAffinityStrategy`:
1. Create `ZoneAffinityRoutingStrategy implements RoutingStrategy`
2. Annotate with `@Component("zone-affinity")`
3. Use existing `order.getPickupZone()` and `agent.getCurrentZone()` fields
4. Add `"zone-affinity"` to `ConfigService.getValidStrategies()`

Zero changes to `RoutingService`, `ReplanningService`, or any caller.

**2. Entity Fields** → Sprint 2 Zone & Capacity

Already present as nullable placeholders:

```java
// Order.java:35-41
private String weightClass;
private String pickupZone;
private String dropoffZone;
private LocalDateTime slaDeadline;

// Agent.java:31-33
private String currentZone;
private Integer maxCapacity;
```

No migration needed — fields exist, just unused. Sprint 2 populates them via seed data and uses them in `ZoneAffinityStrategy`.

**3. Event Mechanism** → Sprint 3 Proactive SLA Breach

Location: `com.ziprun.reassignment.event.AgentOfflineEvent`

The same `ApplicationEventPublisher` + `@TransactionalEventListener` pattern works for `SLABreachEvent`:

```java
// Future: SLABreachEvent.java
public record SLABreachEvent(String orderId, LocalDateTime deadline) {}

// Future: SLAMonitorService (scheduled job)
eventPublisher.publishEvent(new SLABreachEvent(order.getId(), order.getSlaDeadline()));
```

`ReplanningEventListener` can add a second handler or a new listener can be created. The async executor pool is already configured.

**4. TriggerReason Enum** → Sprint 3 SLA Triggers

Location: `com.ziprun.reassignment.domain.enums.TriggerReason`

```java
public enum TriggerReason {
    INITIAL,
    AGENT_OFFLINE,
    MANUAL_REQUEST,
    SUGGESTION_REJECTED
    // Sprint 3: SLA_BREACH
}
```

Adding `SLA_BREACH` requires no code changes — `AIRoutingStrategy.buildPrompt()` already switches on `TriggerReason` and can add a third prompt variant.

---

### Part B: Deliberate Exclusions

| Excluded | Priority Reasoning |
|----------|-------------------|
| **Ops Interface (T-5)** | Deferred because the agentic loop and AI routing are correctness requirements — the system must detect and respond to agent failures automatically. The UI is a visibility enhancement that surfaces what the backend already does. Backend-first ensures the core value proposition works even without UI. |
| **Full Dispatch Board** | Deferred because the re-plan badge on pending suggestions is sufficient to demonstrate the agentic loop working. A full board showing all orders across all statuses is a sprint 3 feature (per roadmap) and would have consumed time better spent on resilience and streaming. |
| **SSE for Initial Suggest** | Implemented for `/suggestions/start` streaming flow but not retrofitted to legacy `/orders/{id}/suggest`. The streaming architecture is proven; applying it everywhere is incremental work, not architectural risk. |
| **Zone-Aware Routing** | Sprint 2 scope. Extension seams are in place (fields, interface), but implementing `ZoneAffinityStrategy` without zone data in seed orders would be untestable busywork. |

---

### Tradeoffs accepted
- Nullable placeholder fields add minor schema noise — but cost nothing at runtime and prevent sprint 2 migrations
- UI deferral means demo relies on API calls or minimal frontend — but the 5-minute demo video can show curl/Postman flows effectively
- Not all extension points are exercised in tests — but the seams are visible in code and can be validated during walkthrough

---
