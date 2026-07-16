(ns officeadminops.governor-test
  "Pure unit tests of `officeadminops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-test`'s
  full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [officeadminops.advisor :as adv]
            [officeadminops.governor :as gov]
            [officeadminops.store :as store]))

(def client-1 {:client-id "client-1" :name "Riverside Dental Practice" :registered? true :verified? true})
(def client-3 {:client-id "client-3" :name "Downtown Startup Studio" :registered? true :verified? false})
(def vendor-1 {:vendor-id "vendor-1" :name "Northgate Office Supply Co." :registered? true :verified? true})
(def vendor-2 {:vendor-id "vendor-2" :name "Unverified Equipment Broker Inc." :registered? true :verified? false})

(defn- clean-proposal [op client-id]
  {:op op :client-id client-id :summary "s" :rationale "routine office-administration coordination"
   :cites [client-id] :effect :propose :value {} :confidence 0.85})

(defn- clean-supply-order [client-id vendor-id cost]
  (assoc (clean-proposal :coordinate-supply-order client-id)
         :value {:client-id client-id :vendor-id vendor-id :estimated-cost cost}))

(deftest client-unregistered-is-hard
  (testing "no client record at all -> HARD hold"
    (let [s (store/mem-store {"client-1" client-1})
          verdict (gov/check {} nil (clean-proposal :log-task-record "unknown-client") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:client-contract-unverified} (map :rule (:violations verdict)))))))

(deftest client-unverified-is-hard
  (testing "client registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"client-3" client-3})
          verdict (gov/check {} nil (clean-proposal :log-task-record "client-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:client-contract-unverified} (map :rule (:violations verdict)))))))

(deftest vendor-missing-on-supply-order-is-hard
  (testing "supply-order proposal with no :vendor-id at all -> HARD hold"
    (let [s (store/mem-store {"client-1" client-1} {"vendor-1" vendor-1})
          verdict (gov/check {} nil (clean-supply-order "client-1" nil 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:vendor-unverified} (map :rule (:violations verdict)))))))

(deftest vendor-unregistered-on-supply-order-is-hard
  (testing "supply-order proposal naming an unknown vendor -> HARD hold"
    (let [s (store/mem-store {"client-1" client-1} {"vendor-1" vendor-1})
          verdict (gov/check {} nil (clean-supply-order "client-1" "unknown-vendor" 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:vendor-unverified} (map :rule (:violations verdict)))))))

(deftest vendor-unverified-on-supply-order-is-hard
  (testing "supply-order proposal naming a registered-but-unverified vendor -> HARD hold"
    (let [s (store/mem-store {"client-1" client-1} {"vendor-1" vendor-1 "vendor-2" vendor-2})
          verdict (gov/check {} nil (clean-supply-order "client-1" "vendor-2" 100.0) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:vendor-unverified} (map :rule (:violations verdict)))))))

(deftest vendor-verified-on-supply-order-is-not-hard-on-vendor-check
  (testing "supply-order proposal naming a verified vendor never trips :vendor-unverified"
    (let [s (store/mem-store {"client-1" client-1} {"vendor-1" vendor-1})
          verdict (gov/check {} nil (clean-supply-order "client-1" "vendor-1" 100.0) s)]
      (is (empty? (filter #(= :vendor-unverified (:rule %)) (:violations verdict)))))))

(deftest vendor-check-is-scoped-to-supply-order-only
  (testing "non-supply-order ops never trip :vendor-unverified, even with no vendors registered at all"
    (let [s (store/mem-store {"client-1" client-1})]
      (doseq [op [:log-task-record :schedule-service-operation :flag-confidentiality-concern]]
        (let [verdict (gov/check {} nil (clean-proposal op "client-1") s)]
          (is (empty? (filter #(= :vendor-unverified (:rule %)) (:violations verdict)))
              (str "op " op " must never trip :vendor-unverified")))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"client-1" client-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-service-operation "client-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed four-op allowlist is a scope violation"
    (let [s (store/mem-store {"client-1" client-1})
          verdict (gov/check {} nil (clean-proposal :finalize-client-disclosure "client-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest disclosure-finalization-content-is-hard-and-permanent
  (testing "a proposal whose rationale touches directly disclosing the client's confidential records is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"client-1" client-1})
          poisoned (assoc (clean-proposal :log-task-record "client-1")
                          :rationale "disclosed the client's confidential records to a third party after the request came in"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest share-confidential-data-content-is-hard
  (testing "a proposal touching sharing the client's confidential data with a third party is HARD-blocked, same as disclosure"
    (let [s (store/mem-store {"client-1" client-1})
          poisoned (assoc (clean-proposal :log-task-record "client-1")
                          :rationale "shared the client's confidential data with a third party before the audit"
                          :confidence 0.90)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legal-decision-on-behalf-content-is-hard
  (testing "a proposal touching making the legal decision on the client's behalf is HARD-blocked"
    (let [s (store/mem-store {"client-1" client-1})
          poisoned (assoc (clean-proposal :schedule-service-operation "client-1")
                          :summary "office coordinator made the legal decision on the client's behalf")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest financial-decision-on-behalf-content-is-hard
  (testing "a proposal touching making the financial decision on the client's behalf is HARD-blocked"
    (let [s (store/mem-store {"client-1" client-1} {"vendor-1" vendor-1})
          poisoned (assoc (clean-supply-order "client-1" "vendor-1" 100.0)
                          :summary "made the financial decision on the client's behalf regarding the invoice")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-confidentiality-concern-is-not-scope-excluded
  (testing "flagging an observed data-handling/confidentiality concern (not a disclosure/decision finalization) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"client-1" client-1})
          concern (assoc (clean-proposal :flag-confidentiality-concern "client-1")
                         :value {:concern "misrouted invoice containing client billing detail sent to the wrong recipient mailbox"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (data-handling/confidentiality risk) is exactly what this op exists to surface"))))

(deftest confidentiality-concern-always-escalates-clean
  (testing ":flag-confidentiality-concern is always high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"client-1" client-1})
          verdict (gov/check {} nil (assoc (clean-proposal :flag-confidentiality-concern "client-1") :confidence 0.99) s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest high-cost-supply-order-always-escalates
  (testing "a :coordinate-supply-order above the cost threshold is high-stakes/escalate, even when otherwise clean and high confidence"
    (let [s (store/mem-store {"client-1" client-1} {"vendor-1" vendor-1})
          expensive (assoc (clean-supply-order "client-1" "vendor-1" 5000.0) :confidence 0.97)
          verdict (gov/check {} nil expensive s)]
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (true? (:escalate? verdict))))))

(deftest low-cost-supply-order-does-not-force-escalate
  (testing "a :coordinate-supply-order at or below the cost threshold does not trip the high-cost escalate gate"
    (let [s (store/mem-store {"client-1" client-1} {"vendor-1" vendor-1})
          cheap (assoc (clean-supply-order "client-1" "vendor-1" 260.0) :confidence 0.9)
          verdict (gov/check {} nil cheap s)]
      (is (false? (:hard? verdict)))
      (is (false? (:high-stakes? verdict)))
      (is (false? (:escalate? verdict))))))

;; ----------------------------- self-trip regression -----------------------------
;;
;; A known bug class in this actor fleet: the governor's own
;; scope-exclusion term list is sometimes phrased as a bare noun (e.g.
;; "confidential" or "disclosure"), which then accidentally matches
;; inside the mock advisor's own DEFAULT rationale/disclaimer text for a
;; legitimate, allowed proposal -- causing the actor to self-block its
;; own happy path. This is a dedicated regression test: every op the
;; default mock advisor can generate, with default (non-`out-of-scope?`)
;; request patches, must NEVER trip `:scope-excluded` or
;; `:op-not-allowed`.
(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "the default mock advisor's own proposals for every allowed op never trip the governor's scope-exclusion check"
    (let [s (store/mem-store {"client-1" client-1} {"vendor-1" vendor-1})]
      (doseq [op [:log-task-record :schedule-service-operation :coordinate-supply-order
                  :flag-confidentiality-concern]]
        (let [patch (if (= op :coordinate-supply-order)
                      {:item "office-supplies restock" :estimated-cost 260.0 :vendor-id "vendor-1"}
                      {})
              proposal (adv/infer nil {:op op :client-id "client-1" :patch patch})
              verdict (gov/check {:client-id "client-1"} nil proposal s)]
          (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
              (str "default advisor proposal for " op " must never self-trip :scope-excluded -- rationale/summary: "
                   (pr-str (select-keys proposal [:summary :rationale]))))
          (is (empty? (filter #(= :op-not-allowed (:rule %)) (:violations verdict)))
              (str "default advisor proposal for " op " must always be inside the closed op allowlist")))))))

(deftest out-of-scope-test-hook-does-trip-scope-exclusion
  (testing "the advisor's own out-of-scope? test hook genuinely trips :scope-excluded -- a sanity check that scope-excluded-terms is not vacuously non-matching"
    (let [s (store/mem-store {"client-1" client-1})
          proposal (adv/infer nil {:op :log-task-record :client-id "client-1" :out-of-scope? true :patch {}})
          verdict (gov/check {:client-id "client-1"} nil proposal s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))
