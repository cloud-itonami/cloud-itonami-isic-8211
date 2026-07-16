(ns officeadminops.advisor
  "OfficeAdminAdvisor -- the *contained intelligence node* for the
  ISIC-8211 'Combined office administrative service activities'
  outsourced back-office operations-coordination actor.

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: administrative-task/document-processing completion logging
  (mail handling, reception, billing, records management, filing),
  staffing/task-assignment scheduling, office-supplies/equipment
  supply-order coordination, and confidentiality/data-handling-concern
  flagging. CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a committed
  record and NEVER a direct actuation -- every proposal's `:effect` is
  always `:propose`. Every output is censored downstream by
  `officeadminops.governor` before anything touches the SSoT.

  This advisor NEVER drafts a disclosure of a client's confidential
  records/data to a third party, NEVER drafts a legal or financial
  decision on a client's behalf, and never finalizes either -- those are
  permanently out of scope for this actor, not merely un-implemented.
  `officeadminops.governor`'s `scope-exclusion-violations` independently
  re-scans every proposal for exactly this failure mode (a compromised or
  confused advisor drifting into scope it must never touch) and
  HARD-holds it, regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  This actor coordinates ADMINISTRATIVE TASK SCHEDULING/LOGGING ONLY. It
  never makes a decision on behalf of the client business.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :client-id  str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-task-record
  "Draft an administrative-task/document-processing completion log entry
  (mail handling, reception, billing, records management, filing). Pure
  logging of observed task completion -- never a legal/financial
  decision on the client's behalf."
  [_db {:keys [client-id patch]}]
  {:op         :log-task-record
   :client-id  client-id
   :summary    (str client-id " の事務処理/書類処理の完了記録を記録: " (pr-str (keys patch)))
   :rationale  "郵便物処理・受付・請求処理・記録管理・ファイリング等の事務作業完了の観察記録のみ。クライアントに代わる法的・財務判断は含まない。"
   :cites      [client-id]
   :effect     :propose
   :value      (merge {:client-id client-id} patch)
   :confidence 0.93})

(defn- propose-service-operation
  "Draft a staffing/task-assignment scheduling proposal (a roster/
  calendar entry, never a direct legal/financial decision)."
  [_db {:keys [client-id patch]}]
  {:op         :schedule-service-operation
   :client-id  client-id
   :summary    (str client-id " の受付/郵便物処理/請求処理担当者の配置予定を提案: " (pr-str (keys patch)))
   :rationale  "受付・郵便物処理・請求処理・ファイリング等の人員配置/タスク割当調整提案のみ。最終的な人員配置は人間が確定する。"
   :cites      [client-id]
   :effect     :propose
   :value      (merge {:client-id client-id} patch)
   :confidence 0.88})

(defn- propose-supply-order
  "Draft an office-supplies/equipment procurement coordination request
  naming a registered vendor -- never a finalized purchase order; a
  human always confirms procurement."
  [_db {:keys [client-id patch]}]
  {:op         :coordinate-supply-order
   :client-id  client-id
   :summary    (str client-id " 向け事務用品/什器の発注調整を提案: " (pr-str (keys patch)))
   :rationale  "事務用品・複合機等の什器の仕入先発注調整提案のみ。確定発注は人間が行う。"
   :cites      [client-id]
   :effect     :propose
   :value      (merge {:client-id client-id} patch)
   :confidence 0.90})

(defn- propose-confidentiality-concern
  "Surface an observed data-handling/confidentiality-breach-risk concern
  (e.g. a document misrouted, a records-access anomaly, an ambiguous
  retention obligation) for HUMAN triage. This op ALWAYS escalates in
  `officeadminops.governor` -- never auto-committed at any phase --
  regardless of how confident the advisor is that the concern is real.
  Deliberately reports the OBSERVATION only, never a finalization/
  disclosure action, so the default rationale never trips the governor's
  `scope-excluded-terms` (see that var's docstring)."
  [_db {:keys [client-id patch]}]
  {:op         :flag-confidentiality-concern
   :client-id  client-id
   :summary    (str client-id " のデータ取扱/機密情報管理に関する懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "クライアントのデータ取扱・機密情報管理に関する懸念の観察事実の報告のみ。第三者への開示や法的・財務判断は行わず、常に人間の確認・対応が必要。"
   :cites      [client-id]
   :effect     :propose
   :value      (merge {:client-id client-id} patch)
   :confidence (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-task-record (propose-task-record _db request)
                   :schedule-service-operation (propose-service-operation _db request)
                   :coordinate-supply-order (propose-supply-order _db request)
                   :flag-confidentiality-concern (propose-confidentiality-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually disclosed the client's confidential records to a third party and made the financial decision on the client's behalf")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :client-id (:client-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
