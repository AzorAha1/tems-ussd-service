# USSD gateway integration and verification

The `/ussd` endpoint supports GET and POST. It does not require a database for the isolated regression suite. Running the complete application still requires its normal runtime services. Nothing in this change provisions an MNO/gateway prefix or deploys the service.

## Configuration

| Property / environment variable | Default | Meaning |
|---|---|---|
| `ussd.gateway.input-mode` / `USSD_INPUT_MODE` | `SINGLE` | `SINGLE` preserves an answer intact; `CUMULATIVE` takes the final asterisk-separated answer on continuation callbacks. |
| `ussd.gateway.response-mode` / `USSD_RESPONSE_MODE` | `LEGACY_ACCEPT` | `LEGACY_ACCEPT` preserves prior behavior: explicit Accept containing text/plain selects text, otherwise JSON. `PLAIN` always emits CON/END text; `JSON` always emits the existing continue/message object. |
| `ussd.gateway.message-types` | Empty map | Maps the actual provider's messageType values to BEGIN, CONTINUE, END. Unmapped values mean UNKNOWN. No provider is inferred from request encoding. |

Only configure message types after confirming the provider contract. For a provider whose contract explicitly defines the strings BEGIN, CONTINUE and END, this example is appropriate:

```properties
ussd.gateway.message-types.BEGIN=BEGIN
ussd.gateway.message-types.CONTINUE=CONTINUE
ussd.gateway.message-types.END=END
```

Numeric types can be configured with map keys, e.g. `ussd.gateway.message-types[0]=BEGIN`, **only if the actual provider confirms that mapping**. This is configurable mapping, not a claim that the production provider uses 0 for BEGIN. Provide mappings through Spring configuration; `.env` files are not automatically loaded by this application.

One callback endpoint currently uses one gateway contract. Multiple providers requiring incompatible input/event/response conventions should use separate authenticated adapters rather than guessing per request.

## Normalization and routing rules

Servlet parameters (query or parsed form) take precedence over raw-body values. Within each source, aliases are checked in this order, skipping blanks:

| Canonical field | Aliases |
|---|---|
| Phone | phoneNumber, phone, msisdn, mobile, caller, subscriber |
| Session | session_id, sessionId |
| Service | serviceCode, service_code, shortCode, shortcode |
| User input | input, ussdString, ussd_string, message |
| Cumulative text | text |
| Event | messageType, message_type |

Duplicate servlet parameters use the first value; duplicate raw form keys use the first value. Prefer one canonical value per field. JSON objects/arrays in field values and malformed JSON/form escapes are rejected with a controlled END response. Diagnostics do not echo the rejected data.

SINGLE mode prefers nonblank user input, falling back to text. CUMULATIVE mode prefers nonblank text, falling back to user input. An explicit extended service code is authoritative for initial routing, independently of either input selection. Therefore serviceCode=*7447*10#, input=7447, text=10 enters TMF for an allowed caller.

Supported initial routes are *7447# (TEMS), *7447*10# (TMF), and *7447*27# / *7447*1# (CBM). The first extension identifies the route, never an arbitrary or last segment. Unknown initial routes return END Unknown USSD route.

Compatibility forms retained: an optional leading `*` and trailing `#`; exact flattened `744710` and `744727`; and bare `10`/`27` when no active state exists. Bare `1` requires BEGIN or a new nonempty session ID. No arbitrary "starts with 7447 and ends with 10" match remains. Parent-plus-suffix entry is resolved only at a session boundary; choice 1 in an active TEMS session remains a menu answer.

## Session behavior

The existing phone-based Redis storage remains, with a new `ussdRoute` marker. BEGIN or a changed nonempty session ID resets the prior phone state before initial routing. Subsequent callbacks retain that route and advance existing flow state, even when they repeat the original full serviceCode. Session/route markers participate in existing expiry extension. END responses clear the session. Explicit CONTINUE without matching local state returns a session-expired message without erasing a newer active session.

Without a session ID or BEGIN, active state wins: an incoming number is treated as an answer, not a fresh substring. Once state expires, bare 10/27 may start their legacy routes; bare 1 remains ambiguous and is rejected. Re-dialing during retained state without any start indicator cannot be distinguished reliably from a reply. The provider must send a stable session ID or explicit event for deterministic behavior. Concurrent sessions from the same phone remain unsupported by the existing phone-based engine.

The TMF allowlist is unchanged. The reported production failure was already tested with an allowed caller. Denied callers retain the existing root-menu fallback, including its existing CBM special-caller behavior.

## Isolated tests

With Java 17 and network access for Maven dependencies:

```sh
bash ./mvnw --batch-mode test
```

The default Maven test run excludes tests tagged `integration`. Router tests and standalone MockMvc controller tests use in-memory mock Redis behavior and mocked relay/SMS services. They do not load application.properties, access `.env`, start Spring's production application context, or connect to PostgreSQL, Redis, the gateway, or CBM.

The existing Spring context-load test is tagged `integration`. Run it separately only in an explicitly configured integration environment using `-Dtest.excludedGroups= -Dtest=TemsApplicationTests`. Do not point that environment at production services.

GitHub Actions runs the default isolated suite on pushes and pull requests. Railway's existing build configuration is unchanged; require the CI job before deploying.

## Production verification after a separately authorized deployment

1. Record the deployed revision and configured input, response, and event modes. Confirm them with the gateway team before changing production defaults.
2. Using the allowed TMF phone, dial the base code and then *7447*10#. Also compare both CBM entry codes. Record network, timestamps, gateway session records, and handset results.
3. Correlate gateway delivery with `ussd_ingress`, `ussd_route`, and `ussd_response` logs by time and the generated request correlation identifier. These logs contain field-presence flags, event, destination, access result, response format/outcome, and elapsed milliseconds. They exclude raw bodies, credentials, phone numbers, replies, and PINs. The correlation identifier is application-generated, not the provider's raw session ID.
4. If there is no application ingress, inspect the gateway's destination URL, service/prefix mapping, delivery status, and network reachability. No Java matcher can process an undelivered dial.
5. If ingress exists, inspect the gateway's actual HTTP status, content type, latency and response. Compare its expected format with configured PLAIN/JSON behavior. Use secured, redacted gateway records when confirming field semantics; do not restore full payload logging.
6. Verify initial TMF entry and option 1 (PIN prompt), and CBM options 1 (mock tests; live relay in deployment) and 2 (About). Confirm that continuation callbacks retain their route and do not restart menus.
7. Check explicit BEGIN, changed session IDs, and unknown extensions. Confirm that the handset receives the expected controlled result.

Passing local tests establishes the corrected application behavior. It does not establish gateway delivery, upstream prefix provisioning, a successful live CBM relay, or resolution of the production "system error."
