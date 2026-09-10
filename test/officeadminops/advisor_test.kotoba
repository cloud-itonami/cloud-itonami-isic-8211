(ns officeadminops.advisor-test
  "Unit tests of `officeadminops.advisor` proposal generation."
  (:require [clojure.test :refer [deftest is testing]]
            [officeadminops.advisor :as adv]
            [officeadminops.store :as store]))

(def db (store/seed-db))

(deftest propose-task-record-shape
  (testing "task-record proposal has correct shape and fields"
    (let [p (adv/infer db {:op :log-task-record
                           :client-id "client-1"
                           :patch {:task "mail-processed" :count 12}})]
      (is (= :log-task-record (:op p)))
      (is (= "client-1" (:client-id p)))
      (is (= :propose (:effect p)))
      (is (<= 0 (:confidence p) 1))
      (is (map? (:value p)))
      (is (contains? (:value p) :client-id)))))

(deftest propose-service-operation-shape
  (testing "service-operation proposal has correct shape"
    (let [p (adv/infer db {:op :schedule-service-operation
                           :client-id "client-2"
                           :patch {:shift "front-desk" :date "2026-07-20"}})]
      (is (= :schedule-service-operation (:op p)))
      (is (= "client-2" (:client-id p)))
      (is (= :propose (:effect p))))))

(deftest propose-supply-order-shape
  (testing "supply-order proposal has correct shape"
    (let [p (adv/infer db {:op :coordinate-supply-order
                           :client-id "client-1"
                           :patch {:item "office-supplies restock" :quantity 50 :estimated-cost 260.0
                                   :vendor-id "vendor-1"}})]
      (is (= :coordinate-supply-order (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p)))
      (is (= "vendor-1" (get-in p [:value :vendor-id]))))))

(deftest propose-confidentiality-concern-shape
  (testing "confidentiality-concern proposal always escalates"
    (let [p (adv/infer db {:op :flag-confidentiality-concern
                           :client-id "client-1"
                           :patch {:concern "misrouted invoice"}})]
      (is (= :flag-confidentiality-concern (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest all-proposals-effect-is-always-propose
  (testing "every proposal type has :effect :propose, never direct actuation"
    (doseq [op [:log-task-record :schedule-service-operation :coordinate-supply-order
                :flag-confidentiality-concern]]
      (let [p (adv/infer db {:op op :client-id "client-1" :patch {}})]
        (is (= :propose (:effect p))
            (str "op " op " must have :effect :propose"))))))

(deftest rationale-string-is-present
  (testing "every proposal has a rationale explaining the advisor's thinking"
    (doseq [op [:log-task-record :schedule-service-operation :coordinate-supply-order
                :flag-confidentiality-concern]]
      (let [p (adv/infer db {:op op :client-id "client-1" :patch {}})]
        (is (string? (:rationale p))
            (str "op " op " must have a :rationale string"))))))
