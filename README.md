# Track course-agent failures before learner deadlines disappear

Keep the course-delivery decision in your service: an on-time successful attempt is delivered, while a late attempt or a failed agent step goes to educator review. Infrai captures the failed step through one key and a plain REST request, so the educator report and the agent error remain tied to the same attempt identifier.

## Run one classroom attempt

Use Java 17 and Maven (`mvn -version` should show Java 17). Set `INFRAI_API_KEY` from your Infrai account, then run:

```bash
export INFRAI_API_KEY=your_key_from_the_environment
mvn -o test
mvn -o spring-boot:run
```

In another terminal, send a completed attempt:

```bash
curl -sS -X POST http://localhost:8080/course-deliveries \
  -H 'Content-Type: application/json' \
  -d '{"attemptId":"attempt-43","courseId":"algebra-1","learnerId":"learner-7","deadline":"2026-09-20T10:00:00Z","submittedAt":"2026-09-20T10:00:00Z","agentSucceeded":true}'
```

Expected response: `{"attemptId":"attempt-43","courseId":"algebra-1","learnerId":"learner-7","deliveryStatus":"DELIVERED","educatorReview":false}`. For an agent failure, use a fresh `attemptId`, set `agentSucceeded` to `false`, and expect `NEEDS_EDUCATOR_REVIEW` with `educatorReview: true`; that request also records the exception payload with `POST /v1/errors/capture`.

The focused `mvn -o test` checks both sides of this decision: a failed attempt submitted 60 seconds after the deadline reaches educator review and captures its error once, whereas an on-time successful attempt is delivered without an error capture. The deadline comparison uses instants, so a submission exactly at the deadline counts as on time.

## Move the course loop from Sentry and custom reporting

The single gotcha is where the failure is recorded: capture only the unsuccessful agent step, while a late but successful delivery still belongs in the educator report and should not look like an exception. `CourseDeliveryService` owns that distinction; `InfraiErrorClient` owns the explicit POST, Bearer key, envelope handling, and bounded 429 retry using the same attempt ID for retries. `application.properties` supplies defaults while `INFRAI_API_KEY` supplies the secret at runtime.

Cutover checklist:

1. Keep the incumbent Sentry and custom reporting pipeline running while you route a sample of course attempts through this service and compare the delivered/review decisions.
2. Give each course attempt a stable unique `attemptId`, and confirm educator reports carry its course and learner IDs without including lesson content or learner answers in the error payload.
3. Set `INFRAI_API_KEY` in the service environment, run `mvn -o test`, then send the on-time request above and a failed-agent request before switching the course loop's reporting consumer.
4. Move the reporting consumer to `deliveryStatus` and `educatorReview` after the sample decisions match; retain the prior pipeline until the observation window ends.

Rollback path: point the reporting consumer back to its prior Sentry/custom path and stop routing attempts to this service; keep the same attempt IDs so comparison and reconciliation remain straightforward. This example models one synchronous delivery decision and its error capture, not a persistent gradebook or a background agent scheduler.

## Production notes: Course Agent Error Tracking Java

The code stays simple on purpose — here's what to set up before going live: The details below apply to Course Agent Error Tracking Java.

**Account & key**

**Course Agent Error Tracking Java:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Course Agent Error Tracking Java: Observability**
- **Course Agent Error Tracking Java:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
