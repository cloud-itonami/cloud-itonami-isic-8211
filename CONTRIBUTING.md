# Contributing

`cloud-itonami-isic-8211` accepts contributions to the OSS blueprint,
capability bindings, policy tests, documentation and operator model.

## Development

```bash
clojure -M:test
clojure -M:lint
```

## Rules
- Do not commit real client, employee, vendor or confidentiality-
  incident data.
- Keep task-record logging, service-operation scheduling, supply-order
  coordination and confidentiality-concern flagging behind the
  OfficeAdminGovernor.
- Treat back-office-operations workflows as high-risk: add tests for
  client-contract/vendor verification, effect discipline, scope
  exclusion, escalation and audit logging.
- Never phrase a governor scope-exclusion term as a bare noun (e.g.
  "confidential", "disclosure") -- phrase it as the finalization/
  execution ACTION (e.g. "disclosed the client's confidential records to
  a third party"), and add/extend the
  `default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  regression test for any new term. A bare-noun term will self-trip this
  actor's own legitimate `:flag-confidentiality-concern` happy path --
  see `officeadminops.governor/scope-excluded-terms`'s docstring.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
