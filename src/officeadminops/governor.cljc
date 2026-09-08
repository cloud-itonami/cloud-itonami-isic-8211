(ns officeadminops.governor
  "OfficeAdminGovernor -- the independent compliance layer that earns
  the OfficeAdminAdvisor the right to commit. The advisor has no notion
  of whether a client's service contract is actually registered and
  verified, whether a named office-supplies/equipment vendor is itself a
  registered/verified counterparty, whether its own proposed `:effect`
  secretly claims a direct actuation instead of a mere proposal, or
  whether it has silently drifted into a permanently out-of-scope
  decision area, so this MUST be a separate system able to *reject* a
  proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- ADMINISTRATIVE TASK
  SCHEDULING/LOGGING ONLY (administrative-task/document-processing
  completion logging, staffing/task-assignment scheduling, office-
  supplies/equipment supply-order coordination, confidentiality/data-
  handling-concern flagging). It NEVER performs or authorizes a decision
  on behalf of the client business. In particular it NEVER performs or
  authorizes:
    - directly finalizing disclosure of a client's confidential
      records/data to a third party
    - making a legal decision on the client's behalf
    - making a financial decision on the client's behalf

  Four HARD checks, ALL permanent, un-overridable by any human approval:

    1. Client contract unverified  -- the target client's service-
                                       contract record must exist AND be
                                       independently confirmed
                                       `:registered?`/`:verified?` in the
                                       store before ANY proposal for it
                                       may commit or even escalate. Never
                                       trusts a proposal's own claim
                                       about the client -- re-derived
                                       from the store's own record, the
                                       same 'ground truth, not
                                       self-report' discipline every
                                       sibling actor's governor uses.
    2. Vendor unverified           -- for `:coordinate-supply-order`
                                       ONLY, the proposal's own drafted
                                       `:value` must name a `:vendor-id`
                                       that resolves to an independently
                                       `:registered?`/`:verified?` vendor
                                       record. A missing vendor-id, or
                                       one that resolves to an
                                       unregistered or unverified vendor,
                                       is a HARD block.
    3. Effect not :propose         -- every proposal's `:effect` MUST be
                                       `:propose`. Any other effect value
                                       is, by construction, a claim to
                                       directly actuate/commit outside
                                       governance -- HARD block, not
                                       merely low-confidence.
    4. Scope exclusion             -- ANY proposal (regardless of op)
                                       whose op, summary, rationale,
                                       cites or draft value touches
                                       directly finalizing disclosure of
                                       a client's confidential records/
                                       data to a third party, or making a
                                       legal or financial decision on the
                                       client's behalf, is a HARD,
                                       PERMANENT block -- this actor's
                                       charter excludes that territory
                                       structurally, not as a rollout
                                       milestone. Evaluated
                                       UNCONDITIONALLY on every proposal.
                                       An op outside the closed four-op
                                       allowlist is the SAME failure mode
                                       (an advisor proposing something it
                                       was never authorized to propose)
                                       and is folded into this same
                                       check. `:flag-confidentiality-
                                       concern` itself is never excluded
                                       by this check -- surfacing a data-
                                       handling/confidentiality concern
                                       for a human is exactly this
                                       actor's job; only FINALIZING/
                                       disclosing/deciding on that
                                       concern is excluded (see
                                       `scope-excluded-terms` below --
                                       phrased as the finalization/
                                       execution ACTION, never a bare
                                       noun like 'confidential' or
                                       'disclosure', so the default mock
                                       advisor's own
                                       `:flag-confidentiality-concern`
                                       rationale never self-trips this
                                       check).

  Two ESCALATE (SOFT) gates, either forces human sign-off:
    - LLM confidence below the floor.
    - The op is `:flag-confidentiality-concern` -- ALWAYS escalates to a
      human, regardless of confidence, regardless of how clean the
      proposal otherwise is. `officeadminops.phase` independently agrees:
      `:flag-confidentiality-concern` is never a member of any phase's
      `:auto` set either -- two layers, not one.
    - A `:coordinate-supply-order` whose drafted `:value` names an
      `:estimated-cost` above `supply-cost-threshold` -- a large-value
      office-supplies/equipment procurement proposal always needs a
      human sign-off, even when the governor and phase would otherwise
      allow auto-commit."
  (:require [kotoba.lang.text :as str]
            [officeadminops.store :as store]))

(def confidence-floor 0.6)

(def supply-cost-threshold
  "Example single-client office-supplies/equipment procurement threshold
  (USD-equivalent units, domain-illustrative -- not a universal
  cross-domain constant). A `:coordinate-supply-order` proposal citing an
  `:estimated-cost` above this value ALWAYS escalates to human sign-off,
  regardless of confidence or rollout phase."
  1000.0)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a scope
  violation by construction (see `scope-exclusion-violations`)."
  #{:log-task-record :schedule-service-operation
    :coordinate-supply-order :flag-confidentiality-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-confidentiality-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- directly finalizing
  disclosure of a client's confidential records/data to a third party,
  or making a legal or financial decision on the client's behalf.
  Scanned across the proposal's op/summary/rationale/cites/value, never
  trusting the advisor's own framing of its intent.

  CRITICAL: every term here is phrased as the finalization/execution
  ACTION (e.g. 'disclosed the client's confidential records to a third
  party', 'made the legal decision on the client's behalf'), never a
  bare noun like 'confidential', 'disclosure', 'data' or 'legal' -- a
  bare noun would accidentally match inside this actor's own legitimate
  `:flag-confidentiality-concern` default proposal text (whose whole job
  is to talk about data-handling/confidentiality concerns, and whose own
  printed `:op` keyword literally contains the substring
  'confidentiality') and self-block the happy path. See
  `officeadminops.governor-test/default-mock-advisor-proposals-never-self-trip-scope-exclusion`
  for the regression test."
  ["finalize disclosure of the client's confidential records"
   "finalized disclosure of the client's confidential records"
   "finalizes disclosure of the client's confidential records"
   "disclose the client's confidential records to a third party"
   "disclosed the client's confidential records to a third party"
   "disclosing the client's confidential records to a third party"
   "release the client's confidential file to a third party"
   "released the client's confidential file to a third party"
   "releasing the client's confidential file to a third party"
   "share the client's confidential data with a third party"
   "shared the client's confidential data with a third party"
   "sharing the client's confidential data with a third party"
   "send the client's confidential records to an external party"
   "sent the client's confidential records to an external party"
   "sending the client's confidential records to an external party"
   "transmit the client's confidential records externally"
   "transmitted the client's confidential records externally"
   "transmitting the client's confidential records externally"
   "make the legal decision on the client's behalf"
   "made the legal decision on the client's behalf"
   "making the legal decision on the client's behalf"
   "make the financial decision on the client's behalf"
   "made the financial decision on the client's behalf"
   "making the financial decision on the client's behalf"
   "sign the contract on the client's behalf"
   "signed the contract on the client's behalf"
   "signing the contract on the client's behalf"
   "authorize the settlement on the client's behalf"
   "authorized the settlement on the client's behalf"
   "authorizing the settlement on the client's behalf"
   "finalize the legal decision for the client"
   "finalized the legal decision for the client"
   "finalize the financial decision for the client"
   "finalized the financial decision for the client"
   "クライアントの機密記録を第三者に開示した"
   "クライアントの機密情報を第三者に開示した"
   "顧客の機密記録を第三者に開示した"
   "顧客の機密情報を第三者に開示した"
   "クライアントに代わって法的決定を下した"
   "クライアントに代わって財務決定を下した"
   "クライアントに代わって契約に署名した"
   "クライアントに代わって和解を承認した"
   "第三者への開示を確定した"
   "法的決定を確定した"
   "財務決定を確定した"])

;; ----------------------------- checks -----------------------------

(defn- client-contract-unverified-violations
  "The target client's service-contract record must exist AND be
  independently `:registered?`/`:verified?` in the store -- never trust
  the proposal's own `:client-id` claim without a store lookup."
  [{:keys [client-id]} st]
  (let [c (store/client-record st client-id)]
    (when-not (and c (:registered? c) (:verified? c))
      [{:rule :client-contract-unverified
        :detail (str client-id " は未登録または未検証のクライアント契約 -- いかなる提案も進められない")}])))

(defn- vendor-unverified-violations
  "For `:coordinate-supply-order` ONLY, the proposal's own drafted
  `:value` must name a `:vendor-id` that resolves to an independently
  `:registered?`/`:verified?` vendor record. A missing vendor-id, or one
  that resolves to an unregistered/unverified vendor, is a HARD block --
  never trust the proposal's own vendor claim without a store lookup, the
  SAME 'ground truth, not self-report' discipline as
  `client-contract-unverified-violations`, reapplied to the office-
  supplies/equipment counterparty."
  [proposal st]
  (when (= :coordinate-supply-order (:op proposal))
    (let [vendor-id (get-in proposal [:value :vendor-id])
          v (and vendor-id (store/vendor-record st vendor-id))]
      (when-not (and v (:registered? v) (:verified? v))
        [{:rule :vendor-unverified
          :detail (str (or vendor-id "(vendor-id missing)")
                        " は未登録または未検証の仕入先 -- 発注調整提案を進められない")}]))))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim to
  directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one lower-cased
  blob the scope-exclusion scan checks."
  [proposal]
  (str/lower (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist, or
  one whose content touches directly finalizing disclosure of a client's
  confidential records/data to a third party, or making a legal or
  financial decision on the client's behalf, regardless of confidence or
  how clean every other check is. Evaluated UNCONDITIONALLY on every
  proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "クライアントの機密記録/データの第三者開示や、クライアントに代わる法的・財務判断の確定に触れる提案は永久に禁止"}])))

(defn- high-cost-supply-order?
  "A `:coordinate-supply-order` proposal citing an `:estimated-cost` above
  `supply-cost-threshold` -- always needs human sign-off (SOFT escalate,
  not a hard block: the order itself is in scope, only its size requires
  a human)."
  [proposal]
  (and (= :coordinate-supply-order (:op proposal))
       (some-> proposal :value :estimated-cost (> supply-cost-threshold))))

(defn check
  "Censors an OfficeAdminAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [client-id (or (:client-id proposal) (:client-id request))
        hard (into []
                   (concat (client-contract-unverified-violations {:client-id client-id} store)
                           (vendor-unverified-violations proposal store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (or (always-escalate-ops (:op proposal))
                              (high-cost-supply-order? proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :client-id  (:client-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
