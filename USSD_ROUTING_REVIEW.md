# Implementation update — 2026-09-30

The corrections are now implemented locally; the original source review below describes the pre-change code and its line numbers are historical.

- Added `UssdInboundRequest`, `UssdRequestNormalizer`, injectable `UssdRouteResolver`, and `UssdGatewayProperties`.
- JSON/form/query aliases share normalization with explicit source precedence and blank fallback. Extended serviceCode is authoritative at entry.
- Initial routing supports TEMS, TMF 10, and CBM 27/1. Arbitrary-segment and last-segment substring matching and duplicate entry branches were removed.
- Session entry is separated from dialogue dispatch. Stored destination controls continuation, cumulative replies are configurable, and new-session state is cleared before entry. Redis read/write/reset failures produce controlled responses rather than pretending session operations succeeded.
- The TMF allowlist is unchanged. The user confirmed that production failures already occur on an allowed number; this implementation does not attribute those failures to authorization.
- Response mode is configurable, preserving the existing Accept-based default. Provider event mappings are explicit and empty by default.
- Raw ingress, dialogue, Redis-value, and downstream CBM response logging was removed. Structured request correlation, route/access, response outcome/format, and duration logs replace the ingress capture.
- Added isolated unit and MockMvc dialogue tests, plus a GitHub Actions job. The existing context-load test is explicitly tagged integration and excluded from default tests.
- No database or `.env` changes, route registry, deployment, or gateway configuration changes were made.

See [USSD_GATEWAY.md](USSD_GATEWAY.md) for configuration, compatibility rules, remaining session limitations, test commands, and the production verification procedure. Validation: Java 17 / Maven 3.9.10 `clean test` completed successfully on 2026-09-30: **33 tests, 0 failures, 0 errors** (18 normalizer/resolver cases and 15 controller tests). `git diff --check` also passed. The Java/Maven toolchain and dependency cache were placed under `/tmp`; no database, live Redis, gateway, or external CBM/SMS service was contacted. The integration context test was excluded, not counted among the 33 passing tests.

Production cause remains unconfirmed: compare base and substring gateway delivery, application ingress, HTTP response and handset result. Application fixes cannot establish upstream prefix provisioning.

---

# USSD substring investigation

Reviewed local `main` at `a0b8e9f` on 2026-09-30 against all three supplied notes. This is a source review, not a production trace. The notes reference `2648fe5`; this checkout contains subsequent CBM changes. Remote/deployed revision and live gateway configuration were not verified. Application behavior has not been changed.

## Confirmed application issues

Line references below are in `src/main/java/com/example/tems/Tems/controller/ussdcontroller.java`.

1. **A valid TMF service code can be ignored** (414, 1617–1653, 1686). `input` wins over `text` even when empty. Nonempty input then wins over `serviceCode`. For an allowed caller, `serviceCode="*7447*10#", input="7447", text="10"` selects `7447`, misses TMF, and enters the root-menu path. The later TMF fallback cannot repair this because the root path already returned. CBM has a separate service-code fallback; TMF does not have the equivalent initial-session check.

2. **TMF is deliberately restricted** (966, 1049, 1619, 1757). Only six normalized phone numbers pass its allowlist. A correctly recognized route for another number resets the session and calls `HandleLevel1`. Usually that displays TEMS; CBM special callers can get CBM because `HandleLevel1` has its own override (1823). This is an access policy, not a failed substring match. Public enablement requires an explicit product decision; removing this restriction is not a parsing fix.

3. **Cumulative input can repeatedly restart TMF or hijack another flow** (993–1020, 1619). The matcher accepts any asterisk-separated segment equal to `10`, without checking active-session state for that branch. `10*1`, `10*1*1234`, and even `9999*10` match. Each match resets Redis and returns the TMF main menu for an allowed caller. The same problem can redirect a non-allowed caller away from their current flow. The phone/session protection applies only to bare `10`/`*10`. Full-code repetition also bypasses it. `handleTextMeFoodMainMenu` expects a single choice such as `1` (1130), so cumulative input needs deliberate normalization, not a wider substring matcher.

4. **CBM determines the route from the last segment** (273–325). Full forms for routes `27` and `1` now exist, but `7447*27*1` is treated as a new CBM entry, resetting the flow. `7447*27*2` does not match CBM. These are inconsistent outcomes if the provider sends accumulated input. The first extension should identify the route; later segments are dialogue input. Provider-specific pre-menu conventions must be confirmed before interpreting an extra `1`.

5. **Suffix-only CBM route `1` is unsupported** (277, 321). `serviceCode="*7447#", input="1"` does not resolve to CBM for an ordinary caller. Bare `1` was deliberately excluded to protect normal menu option 1. Supporting it requires reliable BEGIN/new-session information; treating every `1` as a route would break navigation.

6. **Aliases work in bodies but not uniformly in URL parameters** (346–424, 501–545). GET parameters named `msisdn`, `ussdString`, or `shortCode` are not bound by the endpoint. Alias lookup reads only the parsed raw body, not `request.getParameterMap()`. For example, GET with `msisdn`, `ussdString=27`, and `shortCode=7447` reaches the missing-phone response. Form alias handling additionally depends on whether the raw form body remains available after servlet parameter parsing. Blank primary values also block fallback values.

7. **Session beginnings are inferred rather than normalized** (1023–1036). `messageType` is never used. When `menuShown=true`, a missing incoming or stored session ID counts as an active follow-up, blocking bare `10` or `27`. State uses phone-based Redis keys (4171), with 15-minute extension (4208), rather than a provider/session namespace. A changed session ID helps the bare-code predicate, but there is no general new-session reset before all dialogue dispatch. Old flow state can therefore influence new callbacks.

8. **Response format is based only on Accept** (490–498). Missing Accept or `*/*` produces JSON `{continue, message}`; explicit `text/plain` produces `CON`/`END` text. Whether this breaks the actual gateway is unverified. Confirm its contract before changing the default; request encoding alone does not establish response requirements.

## What is missing from the proposed architecture

Repository-wide inspection of source/configuration, entities, repositories, SQL, tests, and deployment files found no route registry entity/table/repository, tenant route resolver, route-management endpoint, provider adapter, or generic dedicated-code mapping. `Organization` stores descriptive details, not dial routes. Numeric organization IDs are not automatically USSD extensions.

The implemented special routes are TMF `10` and CBM `27`/`1`, with different recognition and access rules. Routes such as `222`, `305`, and arbitrary future suffixes in the notes remain design proposals. Unknown extensions have no explicit route-not-found policy and can fall into the root/menu logic.

`AggregatorService`/`ProductsService` handle subscription/product operations; `AggregatorWebhookController` handles subscription notifications. They do not implement incoming `/ussd` route resolution or prove parent-prefix provisioning. CBM's relay is downstream of local entry routing, so relay credentials cannot explain a callback that never arrives or an initial suffix rejected by the local router.

The only existing test is the context-load test. `railway.json` builds with `-DskipTests`. There is no callback, route, access-policy, cumulative-input, or session-transition regression coverage. A Java runtime and Maven executable are unavailable on this machine, so no Java tests were run.

The latest diagnostic block (362–377) logs raw query strings, bodies, and every header. A failed callback would be useful evidence, but shared logs must redact phone numbers, PINs, and authorization values. The current capture is not a safe permanent diagnostic design.

## What the notes get right, and what cannot be concluded

The notes correctly identify field precedence, the TMF allowlist, stale-session ambiguity, response-contract uncertainty, and the absence of generic routes. They are outdated about CBM support and omit the cumulative-input reset, last-segment CBM routing, and query-alias gaps.

Their ranking of network provisioning as the leading cause is a hypothesis, not a finding established by this checkout. There is no failed live callback/session record or MNO provisioning evidence here. The supplied citation placeholders are not independently verifiable source links. This review does not establish the external commercial/regulatory claims in the notes.

## Evidence needed to identify the live failure

For one failed dial, correlate exact shortcode, time/network, gateway session ID, whether `/ussd` was reached, deployed revision, masked original payload, request headers, and actual HTTP response:

| Observation | Next investigation |
|---|---|
| No TEMS callback | Gateway/MNO session and callback-delivery logs; URL/reachability and service mapping |
| Callback has only parent code and no suffix anywhere | Upstream suffix preservation; Java cannot reconstruct the missing route |
| Correct TMF route but TEMS/CBM menu appears | Allowlist and field precedence |
| TMF/CBM first menu repeats after a choice | Cumulative/full-code matcher resets |
| Missing-phone response for GET aliases | Query-parameter normalization |
| Bare route fails after earlier session | Session ID/BEGIN handling and stale Redis state |
| Correct response generated but handset shows error | Gateway response contract and delivery/timeout logs |

## Corrective implementation order

1. Establish the actual callback contract and capture one failing request plus its continuation. Keep service code, user reply, cumulative text, session ID, and message type separate.
2. Normalize JSON, form, and query inputs consistently, with explicit blank/conflict rules. Resolve the initial route from the authoritative dial information before root-menu handling.
3. Resolve a route only at session entry; retain it in session state. Parse the first extension, not an arbitrary or final segment. Normalize dialogue replies according to the provider contract. Define safe behavior when BEGIN/session identifiers are missing.
4. Preserve the TMF restriction unless public access is intended; make its decision visible in masked diagnostics. Configure response format for the verified provider.
5. Add isolated controller/router regression tests for the cases above, including allowed/denied TMF callers, CBM 1/27, new/stale sessions, and multi-request dialogues. Run them without production Redis/PostgreSQL or external relay calls.
6. Implement the database registry and gateway prefix/provisioning integration as the broader multi-tenant feature. That work is separate from repairing the existing special routes.
