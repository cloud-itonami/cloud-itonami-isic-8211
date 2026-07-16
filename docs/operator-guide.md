# Operator Guide

## First Deployment
1. Register operator, clients and vendors; independently confirm each
   client's service contract and each vendor's registration before
   seeding `officeadminops.store`.
2. Import existing task-completion, staffing and supply-order history.
3. Run read-only task-record-logging and service-operation dry-runs
   (Phase 0-1).
4. Configure the rollout phase and the `coordinate-supply-order`
   cost-escalation threshold for human sign-off paths.
5. Publish a dry-run confidentiality-concern flag and audit export.

## Minimum Production Controls
- client-contract-registration/verification check before ANY proposal
  for that client
- vendor-registration/verification check before ANY `:coordinate-
  supply-order` proposal
- governor gate on every proposal before commit
- human sign-off for `:flag-confidentiality-concern` (always) and
  high-cost `:coordinate-supply-order` proposals
- audit export for every commit, hold and approval
- backup manual back-office process

## Certification
Certified operators must prove client-contract/vendor-verification
discipline, governor-bypass resistance, evidence-backed
confidentiality-concern reporting and human review for every
escalation-gated action.
