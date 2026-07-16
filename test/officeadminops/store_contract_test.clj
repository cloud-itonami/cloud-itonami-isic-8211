(ns officeadminops.store-contract-test
  "Contract tests for `officeadminops.store/Store` protocol."
  (:require [clojure.test :refer [deftest is testing]]
            [officeadminops.store :as store]))

(deftest mem-store-client-lookup
  (testing "MemStore can store and retrieve clients by ID (string keys)"
    (let [clients {"c1" {:client-id "c1" :name "Alice's Dental Practice" :registered? true :verified? true}}
          s (store/mem-store clients)]
      (is (some? (store/client-record s "c1")))
      (is (nil? (store/client-record s "c99"))))))

(deftest mem-store-all-client-records
  (testing "MemStore returns all clients in sorted order"
    (let [clients {"c2" {:client-id "c2" :name "Bob's Law Office"}
                   "c1" {:client-id "c1" :name "Alice's Dental Practice"}
                   "c3" {:client-id "c3" :name "Carol's Consulting Studio"}}
          s (store/mem-store clients)
          all-c (store/all-client-records s)]
      (is (= 3 (count all-c)))
      (is (= "c1" (:client-id (first all-c))))
      (is (= "c3" (:client-id (last all-c)))))))

(deftest mem-store-vendor-lookup
  (testing "MemStore can store and retrieve vendors by ID (string keys)"
    (let [vendors {"v1" {:vendor-id "v1" :name "Acme Office Supply" :registered? true :verified? true}}
          s (store/mem-store {} vendors)]
      (is (some? (store/vendor-record s "v1")))
      (is (nil? (store/vendor-record s "v99"))))))

(deftest mem-store-all-vendor-records
  (testing "MemStore returns all vendors in sorted order"
    (let [vendors {"v2" {:vendor-id "v2" :name "Beta Office Supply"}
                   "v1" {:vendor-id "v1" :name "Acme Office Supply"}}
          s (store/mem-store {} vendors)
          all-v (store/all-vendor-records s)]
      (is (= 2 (count all-v)))
      (is (= "v1" (:vendor-id (first all-v)))))))

(deftest mem-store-ledger-append
  (testing "MemStore append-ledger! adds facts to immutable log"
    (let [s (store/mem-store {})
          fact1 {:t :test :data "fact1"}
          fact2 {:t :test :data "fact2"}]
      (is (= 0 (count (store/ledger s))))
      (store/append-ledger! s fact1)
      (is (= 1 (count (store/ledger s))))
      (store/append-ledger! s fact2)
      (is (= 2 (count (store/ledger s)))))))

(deftest mem-store-coordination-log
  (testing "MemStore commit-record! appends to coordination-log"
    (let [s (store/mem-store {})
          record {:op :log-task-record :client-id "c1" :value {:task "mail-processed"}}]
      (is (= 0 (count (store/coordination-log s))))
      (store/commit-record! s record)
      (is (= 1 (count (store/coordination-log s))))
      (is (= record (first (store/coordination-log s)))))))

(deftest mem-store-with-client-records
  (testing "MemStore with-client-records replaces the client directory"
    (let [s (store/mem-store {})
          new-clients {"c1" {:client-id "c1" :name "Alice's Dental Practice"}}]
      (is (= 0 (count (store/all-client-records s))))
      (store/with-client-records s new-clients)
      (is (= 1 (count (store/all-client-records s)))))))

(deftest mem-store-with-vendor-records
  (testing "MemStore with-vendor-records replaces the vendor directory"
    (let [s (store/mem-store {})
          new-vendors {"v1" {:vendor-id "v1" :name "Acme Office Supply"}}]
      (is (= 0 (count (store/all-vendor-records s))))
      (store/with-vendor-records s new-vendors)
      (is (= 1 (count (store/all-vendor-records s)))))))

(deftest seed-db-has-demo-data
  (testing "seed-db creates a populated MemStore with demo clients and vendors"
    (let [s (store/seed-db)]
      (is (> (count (store/all-client-records s)) 0))
      (is (some? (store/client-record s "client-1")))
      (is (some? (store/client-record s "client-2")))
      (is (some? (store/client-record s "client-3")))
      (is (> (count (store/all-vendor-records s)) 0))
      (is (some? (store/vendor-record s "vendor-1")))
      (is (some? (store/vendor-record s "vendor-2"))))))

(deftest demo-data-string-key-consistency
  (testing "demo-data uses string keys, not keywords, for client-id/vendor-id"
    (let [demo (store/demo-data)
          clients (:clients demo)
          vendors (:vendors demo)]
      (doseq [[k v] clients]
        (is (string? k) "client keys must be strings")
        (is (string? (:client-id v)) "client-id must be string")
        (is (= k (:client-id v)) "key must match client-id"))
      (doseq [[k v] vendors]
        (is (string? k) "vendor keys must be strings")
        (is (string? (:vendor-id v)) "vendor-id must be string")
        (is (= k (:vendor-id v)) "key must match vendor-id")))))

(deftest store-is-append-only
  (testing "appended facts are immutable and never removed"
    (let [s (store/seed-db)
          fact1 {:t :event1 :data "a"}
          fact2 {:t :event2 :data "b"}]
      (store/append-ledger! s fact1)
      (let [ledger-after-1 (store/ledger s)]
        (store/append-ledger! s fact2)
        (let [ledger-after-2 (store/ledger s)]
          (is (= (count ledger-after-1) (dec (count ledger-after-2))))
          (is (every? #(some (fn [x] (= x %)) ledger-after-2) ledger-after-1)
              "all prior facts must still be present"))))))
