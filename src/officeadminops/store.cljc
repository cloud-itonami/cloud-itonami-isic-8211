(ns officeadminops.store
  "SSoT for the ISIC-8211 'Combined office administrative service
  activities' outsourced back-office OPERATIONS-COORDINATION actor,
  behind a `Store` protocol so the backend is a swap, not a rewrite --
  the same seam every `cloud-itonami-isic-*` actor in this fleet uses.

  This actor coordinates the outsourced back-office operations a
  combined office-administrative-services provider performs FOR its
  client businesses: administrative-task/document-processing completion
  logging (mail handling, reception, billing, records management,
  filing), staffing/task-assignment scheduling, office-supplies/
  equipment supply-order coordination with registered vendors, and
  confidentiality/data-handling-concern flagging. It never discloses a
  client's confidential records to a third party, never makes a legal
  or financial decision on a client's behalf, and never itself finalizes
  any such disclosure or decision -- see `officeadminops.governor`'s
  `scope-exclusion-violations`, a HARD, permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `clients` directory keyed by `:client-id` STRING and a
  `vendors` directory keyed by `:vendor-id` STRING (never keywords --
  consistent keying from the start, avoiding the silent-miss bug that has
  plagued earlier sibling actors).

  A registered/verified client service-contract record must exist before
  ANY proposal targeting that client may ever commit or escalate --
  `officeadminops.governor`'s `client-contract-unverified-violations`
  re-derives this from the client's own `:registered?`/`:verified?`
  fields, never from proposal self-report. A `:coordinate-supply-order`
  proposal additionally names a registered vendor via its own
  `:vendor-id`; the SAME 'ground truth, not self-report' discipline
  applies via `vendor-unverified-violations`.

  The ledger stays append-only: which client a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by whom
  is always a query over an immutable log.")

(defprotocol Store
  (client-record [s client-id] "Registered client-contract record, or nil.
    Client map: {:client-id .. :name .. :registered? bool :verified? bool}.")
  (all-client-records [s])
  (vendor-record [s vendor-id] "Registered vendor record, or nil.
    Vendor map: {:vendor-id .. :name .. :registered? bool :verified? bool}.")
  (all-vendor-records [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-client-records [s clients] "replace/seed the client directory (map client-id->client)")
  (with-vendor-records [s vendors] "replace/seed the vendor directory (map vendor-id->vendor)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained client/vendor directory covering both the
  happy path and the governor's own hard checks, so the actor + tests
  run offline."
  []
  {:clients
   {"client-1" {:client-id "client-1" :name "Riverside Dental Practice"
                :registered? true :verified? true}
    "client-2" {:client-id "client-2" :name "Sunset Boulevard Law Office"
                :registered? true :verified? true}
    "client-3" {:client-id "client-3" :name "Downtown Startup Studio (contract in intake)"
                :registered? true :verified? false}}
   :vendors
   {"vendor-1" {:vendor-id "vendor-1" :name "Northgate Office Supply Co."
                :registered? true :verified? true}
    "vendor-2" {:vendor-id "vendor-2" :name "Unverified Equipment Broker Inc."
                :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (client-record [_ client-id] (get-in @a [:clients client-id]))
  (all-client-records [_] (sort-by :client-id (vals (:clients @a))))
  (vendor-record [_ vendor-id] (get-in @a [:vendors vendor-id]))
  (all-vendor-records [_] (sort-by :vendor-id (vals (:vendors @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-client-records [s clients] (when (seq clients) (swap! a assoc :clients clients)) s)
  (with-vendor-records [s vendors] (when (seq vendors) (swap! a assoc :vendors vendors)) s))

(defn seed-db
  "A MemStore seeded with the demo client/vendor directory. The
  deterministic default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with explicit `clients`/`vendors` maps (client-id/
  vendor-id string -> record map) -- the primary test/dev entry point.
  Either may be empty (an unregistered-everywhere client)."
  ([clients] (mem-store clients {}))
  ([clients vendors]
   (->MemStore (atom {:clients (or clients {}) :vendors (or vendors {})
                       :ledger [] :coordination-log []}))))
