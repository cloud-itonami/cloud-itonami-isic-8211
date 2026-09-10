# cloud-itonami-isic-8211

Open Business Blueprint for **ISIC Rev.5 8211**: combined office
administrative service activities -- outsourced back-office bundling
(mail handling, reception, billing, records management, filing) provided
as a combined service to client businesses.

This repository publishes an outsourced-office-administration
operations-COORDINATION actor -- administrative-task/document-processing
completion logging, staffing/task-assignment scheduling, office-
supplies/equipment supply-order coordination with registered vendors,
and confidentiality/data-handling-concern flagging -- as an OSS business
that any qualified operator can fork, deploy, run, improve and sell, so
an independent back-office-services provider never surrenders its
client-operations data to a closed SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, in-mem/Datomic checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **OfficeAdminAdvisor ⊣
OfficeAdminGovernor**. This blueprint's own
`:itonami.blueprint/governor` keyword, `:office-admin-governor`, is a
distinct, independent build.

> **Why an actor layer at all?** An LLM is great at drafting a task-
> completion log entry, a staffing proposal, or a supply-order request --
> but it has no license to actually disclose a client's confidential
> records to a third party, no way to independently confirm a client's
> service contract or a supply-order vendor is actually a registered/
> verified counterparty, and no notion of when a "flag this concern" op
> quietly turns into a claim to have already acted on it. Letting it act
> directly invites an unverified client's data entering the ledger, an
> unverified vendor receiving an equipment order, or -- worst of all -- a
> fabricated claim to have disclosed a client's confidential records or
> made a legal/financial decision on the client's behalf, exposing the
> outsourcing provider and its client to real liability. This project
> seals the OfficeAdminAdvisor into a single node and wraps it with an
> independent **OfficeAdminGovernor**, a human **approval workflow**, and
> an immutable **audit ledger**.

## Scope: coordination only, never a decision on the client's behalf

This actor coordinates ADMINISTRATIVE TASK SCHEDULING/LOGGING ONLY. It
never performs or authorizes:

- directly finalizing disclosure of a client's confidential records/data
  to a third party
- making a legal decision on the client's behalf
- making a financial decision on the client's behalf

The governor's `scope-exclusion-violations` check re-scans every
proposal for this failure mode independently of the advisor's own
framing, and treats it as a HARD, permanent block regardless of
confidence or how clean everything else is. Flagging a confidentiality/
data-handling concern for a human to triage is exactly this actor's job
-- `:flag-confidentiality-concern` is never excluded by this check, only
FINALIZING/disclosing/deciding on that concern is.

### Actuation

**Every proposal this actor generates is `:effect :propose`, never a
direct actuation.** Two independent layers enforce this
(`officeadminops.governor`'s `effect-not-propose-violations` HARD check
and `officeadminops.phase`'s phase table, which never puts
`:flag-confidentiality-concern` in any phase's `:auto` set). A human
office-administration coordinator is always the one who actually acts on
a flagged concern or confirms a high-cost supply order.

## The core contract

```
client-contract/vendor registration + operations-coordination request
        |
        v
   ┌───────────────────────┐   proposal      ┌────────────────────────────┐
   │ OfficeAdminAdvisor    │ ─────────────▶ │ OfficeAdminGovernor          │  (independent system)
   │ (sealed)              │  + citations    │ client-contract-unverified · │
   └───────────────────────┘                 │ vendor-unverified ·          │
          │                 commit ◀┼ effect-not-propose ·                │
          │                         │ scope-excluded (client-confidential- │
    record + ledger        escalate ┼ disclosure / decide-on-behalf-of-    │
          │              (ALWAYS for│ client) · op-not-allowed             │
          │       :flag-             │                                      │
          │       confidentiality-   └────────────────────────────┘
          │       concern/high-cost
          │       supply-order)
          ▼
      human approval
```

**The OfficeAdminAdvisor never commits a proposal the OfficeAdminGovernor
would reject, and a confidentiality-concern flag or a high-cost supply
order never commits without a human sign-off.** Hard violations (an
unregistered/unverified client contract; an unregistered/unverified
supply-order vendor; a non-`:propose` effect; content touching client-
confidential-records disclosure or a legal/financial decision on the
client's behalf; an op outside the closed allowlist) force **hold** and
*cannot* be approved past.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
may perform physical domain work** (here: document scanning/sorting,
mail handling, filing) under human/robot office operations gated by
provider policy. This actor itself does not dispatch robot/hardware
actions -- it is strictly the operations-coordination layer (task-record
logging, staffing scheduling, supply-order coordination,
confidentiality-concern flagging) any physical-dispatch layer could
eventually feed proposals into, always gated the same way by the
independent OfficeAdminGovernor.

## Features

- **Closed proposal-op allowlist**: `log-task-record`,
  `schedule-service-operation`, `coordinate-supply-order`,
  `flag-confidentiality-concern` (all `:effect :propose`).
- **Four HARD governor checks** (permanent, un-overridable):
  1. **Client contract unverified** -- the target client's service
     contract must exist AND be independently registered/verified in
     the store.
  2. **Vendor unverified** -- for `:coordinate-supply-order` only, the
     named vendor must exist AND be independently registered/verified.
  3. **Effect is :propose** -- any other `:effect` value is rejected.
  4. **Scope exclusion** -- directly finalizing disclosure of a client's
     confidential records/data to a third party, making a legal or
     financial decision on the client's behalf, and an op outside the
     closed allowlist are all permanently blocked.
- **Two ESCALATE (SOFT) gates**, either forces human sign-off:
  - `:flag-confidentiality-concern` -- ALWAYS escalates, regardless of
    confidence or phase. A "flag a concern" op is never auto-commit
    eligible and never finalizes a disclosure/decision itself -- it only
    surfaces the concern for a human.
  - `:coordinate-supply-order` above a cost threshold -- a large-value
    procurement proposal always needs a human sign-off.
  - (LLM confidence below the floor also escalates, as with every
    sibling actor.)
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: task-record logging only (approval-gated)
  - Phase 2: + service-operation scheduling, supply-order proposals
    (approval-gated)
  - Phase 3: auto-commits clean, high-confidence, low-cost proposals
    (confidentiality concerns and high-cost supply orders always
    escalate)
- **Append-only audit ledger** -- every decision is an immutable log
  entry.
- **langgraph-clj StateGraph** -- one request = one supervised run;
  human-in-the-loop via `interrupt-before`.

### Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

### Test suite

- `test/officeadminops/governor_test.kotoba` -- unit tests of governor hard
  checks, scope exclusion, and the self-trip regression test
- `test/officeadminops/advisor_test.kotoba` -- advisor proposal shape and
  consistency
- `test/officeadminops/phase_test.kotoba` -- rollout phase logic
- `test/officeadminops/governor_contract_test.kotoba` -- full graph
  integration, audit trail
- `test/officeadminops/store_contract_test.kotoba` -- Store protocol and
  MemStore implementation

### Modules

- `officeadminops.store` -- SSoT (MemStore, String-keyed client/vendor
  directories, append-only ledger)
- `officeadminops.advisor` -- contained intelligence node (mock +
  real-LLM seam)
- `officeadminops.governor` -- independent compliance layer
- `officeadminops.phase` -- staged rollout (0→3)
- `officeadminops.operation` -- langgraph-clj StateGraph
- `officeadminops.sim` -- demo driver

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`8211`).

## Business-process coverage (honest)

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Administrative-task/document-processing completion logging -- mail handling, reception, billing, records management, filing (`:log-task-record`) | Real document-management/POS-billing-system integration |
| Staffing/task-assignment scheduling coordination (`:schedule-service-operation`) | Direct staff time-clock/payroll integration |
| Office-supplies/equipment supply-order coordination with a registered, verified vendor, HARD-gated on vendor verification and a double-actuation-free single-proposal shape (`:coordinate-supply-order`) | Real supplier-ordering-system integration |
| Confidentiality/data-handling-concern flagging, ALWAYS human-gated (`:flag-confidentiality-concern`) | Directly finalizing disclosure of a client's confidential records/data to a third party, or making a legal/financial decision on the client's behalf -- permanently out of scope, not a gap |
| Immutable audit ledger for every log/schedule/order/flag decision | Daily reconciliation/cash-up -- a follow-up slice, not in this R0 |

Extending coverage is additive: add the next op (e.g. a document-
retention-schedule proposal or a records-disposal-hold check) as its own
governed op with its own HARD checks and tests, following the SAME "an
independent governor re-verifies against the actor's own records before
any real-world act" pattern this repo's flagship checks already
establish.

## Maturity

`:implemented` -- `OfficeAdminAdvisor` + `OfficeAdminGovernor` run as
real, tested code (see `Development` above), following the SAME
governed-actor architecture as every prior actor across this fleet, with
its own distinct, independently-named governor.

## License

Code and implementation templates are AGPL-3.0-or-later.
