# USSD route alias investigation — 2026-10-13

## Scope and revision

Fetched `origin/main` before editing. Both HEAD and remote main were `53b03f7ca2a55896448c23a063cdeaaa4a5985ff`. The implementation was prepared on `fix/ussd-route-alias`. At the user's request, publication targets `longest`; a fresh remote checkout confirmed that branch had the same baseline revision. No deployment or aggregator configuration change is included.

Reviewed recent routing changes `63805a7`, `72a9368`, `4966103`, the subscription/aggregator rewrite `4d7edd2`, payment gate `7f6dcc2`, and current aggregator update `53b03f7`. The October routing commits changed the exact legacy CBM suffix set from `2,20,27` to `1,2,7,27`; the source comment still mentions `20`, although current code intentionally routes it to TEMS. Comments referencing production logs are historical claims, not live records available in this investigation.

**Route 1:** Phase 1/2 implementation is ready for a live experiment; Phase 5/6 activation still requires the two-callback handset test. **TMF route 10:** `*7447*100*10#` now routes directly to the existing TMF application, with `*7447*10#` retained as a compatibility alias. TMF does not use the diagnostic menu. See the TMF update below for production limitations.

## 1. Current behavior

### Confirmed from latest code and local isolated HTTP tests

`/ussd` accepts GET and POST. Query/form parameters take precedence over JSON/raw form values, skipping blanks for canonical selection. Service aliases are `serviceCode`, `service_code`, `shortCode`, `shortcode`. Response aliases are `input`, `ussdString`, `ussd_string`, `message`. `text` is retained separately. Session aliases are `session_id`, `sessionId`; phone aliases include `phoneNumber`, `phone`, `msisdn`, `mobile`, `caller`, `subscriber`. `messageType`/`message_type` has an explicit configurable mapping, empty by default. No numeric message-type meaning is invented.

The earlier empty-input/service-code precedence bug was already fixed before this branch: an extended service code is authoritative at entry. `UssdInboundRequest` preserves service, reply, cumulative text, session/event and telco independently. Application properties select CUMULATIVE by default (the Java properties object's standalone default is SINGLE). CUMULATIVE prefers nonblank `text`, falling back to user input; SINGLE does the reverse. Neither convention is proof of the actual live callback contract.

Conflicting nonblank input aliases still use the normalizer's explicit precedence. For example, parent-only service plus `input=7447` and `ussdString=1` selects `input`, not the suffix. Diagnostics retain both individual fields and the selected normalized input; changing their precedence requires the actual provider contract, rather than treating every field as interchangeable.

For a fresh session with an ordinary caller and an unblocked payment gate:

| Callback representation | Existing production outcome |
|---|---|
| `serviceCode=*7447*1#`, empty input/text | CBM `CON` main menu |
| `serviceCode=*7447*100*1#`, empty input/text | The identical CBM `CON` main menu |
| `serviceCode=*7447#`, `input=7447*1` | CBM main menu |
| `serviceCode=*7447#`, `ussdString=1`, new nonempty session ID or mapped BEGIN | CBM main menu |
| `serviceCode=*7447*100#`, `ussdString=1` | TEMS main menu: a reproduced production parsing gap |
| Active CBM session, same ID, reply `2` | CBM About screen, rather than a fresh route |

The existing resolver's `1 -> CBM` entry and `100*1 -> CBM` entry already converge on `showCBMMenu` and `handleCBMFlow`. There is no literal redial, HTTP redirect, duplicated tenant menu, or automatic insertion of `100` in the callback identity.

Initial application requests are inferred from mapped BEGIN, changed nonempty session ID, lack of `ussdRoute`/`menuShown`, or an input matching `7447*<digits>`. That last rule assumes a full dial representation means restart even with retained state and a repeated session ID; it is not supported by live evidence here. Explicit CONTINUE with missing/mismatched local state returns an expired-session END without clearing a newer application's session. Unmapped events cannot offer that protection reliably.

### Live observations and limits

The user reports that `*7447*100*1#` works, and that `*7447*1#` previously reached TEMS before something involving `*7447*100` appeared and the session closed. No corresponding callback bodies, HTTP responses, deployed SHA, gateway logs or handset recording have been supplied. The exact live fields, session ID continuity, timing and closure point cannot be reconstructed from those observations. No real network dial has been performed by this branch.

## 2. Root cause

**The live session-closure root cause is not established.** The following boundaries are confirmed:

- Java interprets an initial `100` segment as a legacy routing prefix. It does not require it for the full public `*7447*1#` entry. The separate-fields `*7447*100#` + `1` representation exposes a real Java parsing defect, but no supplied live record establishes that this is the failing callback shape.
- `AggregatorService` initiates an HML product subscription with `product_id`, normalized phone, telco and `channel=USSD`. It does not send, construct or modify a `serviceCode`, dial string or USSD session ID. The configured product ID is distinct from the route segment `100`.
- `AggregatorWebhookController` validates product identity and records successful payment, insufficient balance or charging errors in `SubscriptionService`. It does not handle interactive callbacks or rewrite routes.
- `PaymentGate` can deliberately produce an END before the initial application menu. Repository defaults are payment OFF and subscription initiation disabled; deployed overrides are unknown. Its charge request can take connect/read time plus a polling wait, which must be compared with the gateway's callback deadline if enabled. Its enforcement is unchanged.
- CBM direct entry is not subject to the TMF test-phone allowlist. TMF denial intentionally falls back to TEMS, and CBM special-phone behavior exists on root-menu entry. The diagnostic probe has a separate configuration allowlist. The user-approved number ending 9119 is its application configuration default; numbers are never printed in diagnostic logs.
- Nothing here proves whether `100` also identifies an upstream product, charging channel or MNO service. That requires correlated aggregator/MNO session records. Java cannot change that upstream identity by relabeling an inbound field.

Thus the application role of `100` is proven; the claimed aggregator/paywall/network role and the cause of the live failure remain unproven. No charging enforcement has been removed.

The user-requested phone ending **9119** was checked against this revision: it is present in `TMF_TEST_PHONE_NUMBERS`, absent from `CBM_SPECIAL_NUMBERS`. The local `.env` does not configure the new alias-test list or payment test/exemption lists. The later TMF update adds this number as the default alias-test number in application configuration. Deployed environment overrides are unknown.

## 3. Alias feasibility

**No A/B/C live-network classification is justified yet.** Selecting A, B or C without the requested two-step experiment would manufacture evidence. Local tests prove application convergence and transport-probe behavior, not handset continuity. There is no evidence supporting a conclusion that aggregator changes are required.

The next decision is explicit:

| Observed live result | Conclusion/action |
|---|---|
| Short dial shows the test CON menu; choice 1 produces a second callback with the same session ID; handset receives the success END | A: server-side application routing is feasible for that tested network/provider; connect the parser to a route registry/resolver and retain payment enforcement |
| Correct CON returned, first menu visible, but no continuation reaches Java | Investigate aggregator/MNO session and callback-delivery records; classify B only when upstream termination is evidenced, rather than a dropped callback or malformed response |
| Aggregator/MNO confirms the channel/service cannot continue under the public entry identity without a mapping change | C for that provider/network; stop Java alias work and document the required upstream change |
| CON never appears, wrong wire format, HTTP error, Redis error or timeout | Fix or establish that problem first; this does not prove a network prefix requirement |

Even an initial menu visible on the handset is insufficient evidence of A. Gateway retransmissions are not a human-entered second step.

## 4. Code changes

| File | Purpose |
|---|---|
| `src/main/java/com/example/tems/Tems/ussd/UssdInboundRequest.java` | Adds immutable selected raw routing fields while retaining existing constructors and safe `toString`; original network service and session identity remain separate |
| `src/main/java/com/example/tems/Tems/ussd/UssdRequestNormalizer.java` | Preserves individual routing aliases for diagnostics, including blank values; excludes phone fields, credentials, arbitrary headers and full bodies from this metadata map |
| `src/main/java/com/example/tems/Tems/ussd/UssdGatewayProperties.java` | Adds diagnostics and test switches, both false by default, and a separate configured approved-phone set |
| `src/main/java/com/example/tems/Tems/ussd/UssdRouteParser.java` | Parses base code, transport prefix, exact public route and remaining segments; classifies full/parent-plus-suffix forms; also supports the production TMF prefixed-route registry |
| `src/main/java/com/example/tems/Tems/ussd/UssdRouteProbe.java` | Implements the restricted two-callback test using separate Redis keys derived from phone plus actual network session ID and a three-minute TTL |
| `src/main/java/com/example/tems/Tems/ussd/UssdDiagnosticTrace.java` | Emits opt-in structured safe evidence: stable hashed session identity, masked phone, routing aliases, method/content/Accept, session classification, key-presence flags, handler/access, status/content type, CON/END and duration |
| `src/main/java/com/example/tems/Tems/controller/ussdcontroller.java` | Runs the probe before any application/payment dependencies, supplies diagnostic context and formats probe responses through the existing formatter; removes unsafe raw `ussd_dial` logging |
| `src/main/resources/application.properties` | Defines explicit environment placeholders for the disabled diagnostic/test switches and user-approved default test number, with environment override |
| `src/test/java/com/example/tems/Tems/ussd/UssdRouteParserTest.java` | Adds requested parsing shapes, exact multi-digit routes, malformed input and metadata/privacy tests |
| `src/test/java/com/example/tems/Tems/ussd/UssdGatewayPropertiesTest.java` | Verifies disabled defaults and explicit deployment placeholder binding without production services |
| `src/test/java/com/example/tems/Tems/ussd/UssdControllerTest.java` | Adds two-step probe, callback encodings, session separation, stale state, expiry/restart/exit, access restrictions, privacy, response protocol and legacy-convergence coverage |
| `USSD_GATEWAY.md` | Links the temporary experiment and documents its configuration boundaries |
| `USSD_ROUTE_ALIAS_INVESTIGATION.md` | This evidence report and operator procedure |

The `UssdRouteResolver` now registers the prefixed TMF application target `100*10`. Its legacy route `10` continues to select the same TMF handler; the TMF help screen now advertises `*7447*100*10#`. Downstream services, payment policies, access restrictions, security rules, deployment configuration and `.env` remain unchanged. No new route-1 alias registry is activated pending proof. The parser provides the focused abstraction for a later config/table-backed mapping rather than more organization-specific matchers.

## 5. Tests

The isolated `mvn test` run passed: **89 tests, 0 failures, 0 errors, 0 skipped within the selected suite**. The clean package run also passed with all 89 isolated tests. Default Maven tests exclude the existing `integration`-tagged Spring application context test. Tests use mocked Redis, payment, CBM relay and SMS, without reading `.env` or contacting production Redis, PostgreSQL, SMS, subscription or CBM APIs.

Coverage includes:

- Five service/input representations from the requested matrix; JSON, form and query callbacks; independent service/input/text precedence and individual aliases.
- Root, public `1`, `10`, `222`, `1001`, legacy `100*1`, unwrapped form, malformed and unknown routes. Later segment `10` is never mistaken for the first route.
- Static initial CON, same-session `1` success END, `0` exit, mapped END, distinct sessions for one phone, missing session ID, Redis expiry and explicit restart, repeated service code, cumulative replies, duplicate initial delivery, and stale application Redis untouched by the probe.
- Probe off, empty allowlist, approved national/international number, denied caller fallback with diagnostic distinction; existing TMF allowed/denied behavior.
- Both full production entry forms reach the same CBM handler, and a continuation advances it. The existing split-prefix production gap is explicitly reproduced while the new parser classifies it correctly.
- No payment/SMS/CBM interactions during the static test. Safe logging excludes full phones, arbitrary dialogue, PINs, session contents and credentials.

Manual network/handset tests: **not performed**. Automated HTTP fixtures are not live runtime evidence.

## 6. Runtime evidence and capture procedure

No live callback examples are available. These are **synthetic fixtures**, not an assertion about the live gateway:

```json
{"serviceCode":"*7447*1#","text":"","sessionId":"test-public","phoneNumber":"*******0000"}
{"serviceCode":"*7447*100*1#","text":"","sessionId":"test-legacy","phoneNumber":"*******0000"}
```

The normalizer retains the original service field. The parser reports route `1` for both; transport prefix is empty for the first and `100` for the second. Both production fixtures select CBM. The probe only intercepts the public form for approved callers when explicitly enabled.

To run the live experiment on an authorized deployment of this branch:

1. Record deployed SHA, input mode, response mode, message-type mappings and current payment settings. Configure in the deployment environment (the app does not automatically load `.env`):

   | Environment variable | Value |
   |---|---|
   | `USSD_DIAGNOSTICS_ENABLED` | `true` |
   | `USSD_ALIAS_TEST_ENABLED` | `true` |
   | `USSD_ALIAS_TEST_NUMBERS` | Only the explicitly approved test phone, or approved comma-separated phones; never place the list in shared logs |

2. Preserve the working callback response contract. Do not guess PLAIN/JSON from request body encoding or change the gateway mapping. Compare a legacy dial's actual HTTP status, response Content-Type, Accept and body with the gateway's interpretation. If the provider supplies message types, configure only its confirmed BEGIN/CONTINUE/END meanings.
3. Dial `*7447*100*1#` and capture the sanitized baseline callback/response. The approved public probe does not intercept this legacy entry. That application baseline can invoke its normal payment/CBM behavior; the shorter static test cannot.
4. Dial `*7447*1#`. The approved initial request with a nonempty network session ID must show:

   ```text
   CON TEMS ROUTE ALIAS TEST

   1. Continue
   0. Exit
   ```

5. Enter `1` on the handset. Require a second callback with the same hashed session ID, diagnostic `FOLLOW_UP`, handler `route-alias-test`, and:

   ```text
   END Route alias session test successful.
   ```

6. Repeat with `0`. Test each relevant MNO separately, using timestamps to distinguish dial/retry/continuation. If the ID changes or disappears, the probe fails closed instead of claiming proof from a phone-only match.
7. If the first CON closes or errors, capture Java diagnostic plus the gateway's actual received status, Content-Type, body, latency, delivery status, session ID and termination reason. Probe diagnostics include its fixed application response and formatted wire body (plain or JSON); application responses expose only type/fingerprint to avoid personal data. Application completion logging does not prove receipt by the gateway or handset.
8. Disable both temporary switches after collecting evidence. Existing phone-based application resets cannot delete the separate `ussd:route-probe:<hash>` namespace; probe markers expire after three minutes.

Diagnostic raw fields are deliberately constrained: recognized entry codes and exact route values are preserved, unknown strings/extra dialogue segments are redacted. PINs, names, credentials and arbitrary cumulative dialogue cannot be safely logged verbatim. Session IDs are stable SHA-256 references, not original values; compare those references across callbacks. The record includes original service metadata separately from route classification. Existing application routing key names are logged as presence booleans, never their values or phone-containing Redis keys. The static probe logs its own isolated state and does not read application state, so stale application flows cannot influence the test.

An unmapped event with exactly the same effective input as the initial callback repeats the test menu and logs `duplicate-or-ambiguous-entry`, rather than claiming success from a retransmission. In particular, a provider sending parent service plus bare `1` for both entry and single-choice continuation needs a confirmed CONTINUE event mapping, or distinct cumulative input, to complete this experiment reliably. This is an ambiguity in available metadata, not evidence of a network prefix requirement.

## 7. Recommended production route

Retain `*7447*100*1#` while testing. Do not advertise the shorter route as a proven working alias yet. Existing Java already accepts the full public entry, but its live interactive continuity is unverified. No extra rewrite of `serviceCode` is needed or appropriate.

After a successful live test, wire the parser into the existing resolver with a small registry such as public `1 -> canonical application 100*1 -> CBM`, preserving the existing handler, actual incoming service/session metadata and payment policy. Fix the reproduced split-fields case at that point, and bind canonical destination only at a confirmed initial session boundary. Do not add another CBM workflow or overwrite the upstream service code.

## 8. Remaining risks

- **Network/provider differences:** an experiment establishes behavior only for its actual MNO/provider/configuration. Other networks may transform suffixes or use different service identities.
- **Aggregator behavior:** pre-menu charging screens, callback transformations, delivery loss/retries and upstream session termination need live evidence. A Java-produced CON does not prove the network accepted it.
- **Application sessions:** production still uses phone-based Redis keys, permits only one application session per phone and has full-input restart heuristics. Unmapped/missing events and changed IDs may cause a restart or an expired response. The probe uses separate phone/session keys, requires an ID, and must not be presented as a full engine/session migration.
- **Stale Redis:** application markers have roughly ten-minute initial TTL, fifteen-minute extensions; some flow/field keys have separate lifetimes and swallowed extension failures. Diagnostic key-presence snapshots help find interference. The isolated probe avoids these keys entirely.
- **Charging:** existing PaymentGate behavior and exceptions remain; live mode and gateway-side charges are unknown. Approved diagnostic users skip only local business/payment calls during the experiment; the upstream network may still charge for a dial.
- **Response format:** legacy Accept behavior gives JSON when Accept is absent or `*/*`, and plain CON/END when explicitly selected; configured PLAIN/JSON overrides this. The live working route's wire contract is not yet verified. No response-mode default was changed. Unsupported Accept types can also be rejected by Spring before the controller, so gateway-side HTTP records remain necessary.
- **Diagnostics:** these are application-side status and duration measurements. A proxy, gateway or disconnected client can behave differently. Controlled probe strings are logged exactly; arbitrary business dialogue is redacted.
- **Security:** existing `SecurityConfig` permits all requests; this investigation does not weaken or expand it or introduce administrative endpoints. Source credential properties are externalized, `.env` is untracked and was not modified or printed. No confirmed embedded source credentials were found in the inspected configuration/classes; this is not a complete historical secret audit.

## Verification results

- `mvn test`: **BUILD SUCCESS — 89 tests, 0 failures, 0 errors** (31 controller, 18 existing routing, 16 parser, 2 configuration, 8 webhook, 14 payment tests).
- Java 17.0.20.1 and Maven 3.9.10 were installed temporarily under `/tmp/tems-ussd-toolchain`; Maven used a temporary local dependency cache and the Maven Central Google mirror after slow primary downloads. No repository toolchain/configuration files were changed.
- `mvn clean package`: **BUILD SUCCESS — 89 tests, 0 failures, 0 errors**, verified in the isolated alias checkout; the executable Spring Boot JAR was produced.
- `git diff --check`: passed.
- Integration Spring context/production services and live handset/network sessions: not tested. A/B/C classification remains pending live proof.
- Spring property-binding implementation was verified against Spring Boot 3.5 [external configuration documentation](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html), fetched using the required Context7 CLI.


## TMF prefixed-route update — 2026-10-13

At the user's request, TMF's advertised entry is now `*7447*100*10#`. `UssdRouteResolver` uses an exact prefixed application registry (`100*10 -> TMF`) and the existing parser. It handles the full code in service metadata, full input, parent service plus `100*10` suffix, and `serviceCode=*7447*100#` plus initial suffix `10`. The old `*7447*10#` entry remains an application alias to the same handler. Network metadata is preserved, and continuation choices still use the stored TMF flow rather than resolving a route again.

This routing is active without enabling the diagnostic test switch. No new TMF tests or live diagnostic gate were added. The requested verification is a compile/package build with test execution skipped; the earlier 89-test result describes the previous investigation commit, not this TMF update.

The user explicitly requested retaining the current demo implementation. The existing TMF access allowlist, shared demo PIN, fixed beneficiary/voucher balances and simulated redemption therefore remain. This change publishes the TMF entry routing into that existing demo flow; it does not add a real beneficiary, authentication or redemption backend. No live network success or deployment is claimed. The number ending 9119 is now the default approved number for the separate route-alias test via `ussd.gateway.alias-test-numbers`; `USSD_ALIAS_TEST_NUMBERS` can override it. The alias-test switch remains false by default.

Changed files for this update: `UssdRouteResolver.java` (prefixed target), `UssdRouteParser.java` (transport-only suffix), `ussdcontroller.java` (advertised restart code), `application.properties` (approved alias-test number), `USSD_GATEWAY.md` and this report (current behavior and limitations).

Build result for the TMF update: **`mvn clean package -DskipTests` — BUILD SUCCESS**. Test execution was skipped as requested; no live TMF or network test was performed.
