(ns automotive.store
  "SSoT for the motor-vehicle-manufacturing actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  every prior `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/automotive/store_contract_test.clj), which is the whole point:
  the actor, the Automotive Governor and the audit ledger
  never know which SSoT they run on.

  Like `telecom.store`'s dual number-provisioning/billing-suppression
  history and every other dual-actuation sibling before it, this actor
  has TWO actuation events (dispatching a vehicle action, issuing a
  Certificate of Conformity) acting on the SAME entity (a vehicle),
  each with its OWN history collection, sequence counter and dedicated
  double-actuation-guard boolean (`:vehicle-dispatched?`/
  `:conformity-certified?`, never a `:status` value) -- the same
  discipline every prior sibling governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320).

  The ledger stays append-only on every backend: 'which vehicle was
  screened for an unresolved end-of-line defect, which vehicle action
  was dispatched, which Certificate of Conformity was issued, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a community trusting a motor-
  vehicle manufacturer needs, and the evidence a manufacturer needs if
  a dispatch or conformity-certificate decision is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [automotive.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (vehicle [s id])
  (all-vehicles [s])
  (eol-screen-of [s vehicle-id] "committed end-of-line-defect screening verdict for a vehicle, or nil")
  (requirements-verification-of [s vehicle-id] "committed type-approval requirements verification, or nil")
  (ledger [s])
  (dispatch-history [s] "the append-only vehicle-dispatch history (automotive.registry drafts)")
  (evidence-history [s] "the append-only Certificate-of-Conformity history (automotive.registry drafts)")
  (next-dispatch-sequence [s jurisdiction] "next dispatch-number sequence for a jurisdiction")
  (next-evidence-sequence [s jurisdiction] "next evidence-number sequence for a jurisdiction")
  (vehicle-already-dispatched? [s vehicle-id] "has this vehicle's action already been dispatched?")
  (vehicle-already-certified? [s vehicle-id] "has this vehicle's Certificate of Conformity already been issued?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-vehicles [s vehicles] "replace/seed the vehicle directory (map id->vehicle)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained vehicle set covering both actuation
  lifecycles (dispatching a vehicle action, issuing a Certificate of
  Conformity) so the actor + tests run offline."
  []
  {:vehicles
   {"vehicle-1" {:id "vehicle-1" :vehicle-name "Sakura Compact Sedan CS-04"
                  :emissions-deviation-actual 0.05 :emissions-deviation-min -0.10 :emissions-deviation-max 0.10
                  :structural-deviation-actual 0.02 :structural-deviation-min -0.05 :structural-deviation-max 0.05
                  :eol-defect-unresolved? false
                  :robotics-sim-verified? false :robotics-sim-record nil
                  :vehicle-dispatched? false :conformity-certified? false
                  :jurisdiction "JPN" :status :intake}
    "vehicle-2" {:id "vehicle-2" :vehicle-name "Atlantis Crossover CX-12"
                  :emissions-deviation-actual 0.05 :emissions-deviation-min -0.10 :emissions-deviation-max 0.10
                  :structural-deviation-actual 0.02 :structural-deviation-min -0.05 :structural-deviation-max 0.05
                  :eol-defect-unresolved? false
                  :robotics-sim-verified? false :robotics-sim-record nil
                  :vehicle-dispatched? false :conformity-certified? false
                  :jurisdiction "ATL" :status :intake}
    "vehicle-3" {:id "vehicle-3" :vehicle-name "鈴木コンパクトカー SC-07"
                  :emissions-deviation-actual 0.35 :emissions-deviation-min -0.10 :emissions-deviation-max 0.10
                  :structural-deviation-actual 0.02 :structural-deviation-min -0.05 :structural-deviation-max 0.05
                  :eol-defect-unresolved? false
                  :robotics-sim-verified? false :robotics-sim-record nil
                  :vehicle-dispatched? false :conformity-certified? false
                  :jurisdiction "JPN" :status :intake}
    "vehicle-4" {:id "vehicle-4" :vehicle-name "田中SUVモデル SV-03"
                  :emissions-deviation-actual 0.05 :emissions-deviation-min -0.10 :emissions-deviation-max 0.10
                  :structural-deviation-actual 0.02 :structural-deviation-min -0.05 :structural-deviation-max 0.05
                  :eol-defect-unresolved? true
                  :robotics-sim-verified? false :robotics-sim-record nil
                  :vehicle-dispatched? false :conformity-certified? false
                  :jurisdiction "JPN" :status :intake}
    "vehicle-5" {:id "vehicle-5" :vehicle-name "佐藤ピックアップ PU-09"
                  :emissions-deviation-actual 0.05 :emissions-deviation-min -0.10 :emissions-deviation-max 0.10
                  :structural-deviation-actual 0.30 :structural-deviation-min -0.05 :structural-deviation-max 0.05
                  :eol-defect-unresolved? false
                  :robotics-sim-verified? true :robotics-sim-record nil
                  :vehicle-dispatched? false :conformity-certified? false
                  :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- dispatch-vehicle!
  "Backend-agnostic `:vehicle/mark-dispatched` -- looks up the
  vehicle via the protocol and drafts the vehicle-dispatch record,
  and returns {:result .. :vehicle-patch ..} for the caller to
  persist."
  [s vehicle-id]
  (let [a (vehicle s vehicle-id)
        seq-n (next-dispatch-sequence s (:jurisdiction a))
        result (registry/register-vehicle-dispatch vehicle-id (:jurisdiction a) seq-n)]
    {:result result
     :vehicle-patch {:vehicle-dispatched? true
                      :dispatch-number (get result "dispatch_number")}}))

(defn- issue-conformity-certificate!
  "Backend-agnostic `:vehicle/mark-certified` -- looks up the
  vehicle via the protocol and drafts the Certificate-of-Conformity
  record, and returns {:result .. :vehicle-patch ..} for the caller
  to persist."
  [s vehicle-id]
  (let [a (vehicle s vehicle-id)
        seq-n (next-evidence-sequence s (:jurisdiction a))
        result (registry/register-conformity-certificate vehicle-id (:jurisdiction a) seq-n)]
    {:result result
     :vehicle-patch {:conformity-certified? true
                      :evidence-number (get result "evidence_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (vehicle [_ id] (get-in @a [:vehicles id]))
  (all-vehicles [_] (sort-by :id (vals (:vehicles @a))))
  (eol-screen-of [_ id] (get-in @a [:eol-screens id]))
  (requirements-verification-of [_ vehicle-id] (get-in @a [:verifications vehicle-id]))
  (ledger [_] (:ledger @a))
  (dispatch-history [_] (:dispatches @a))
  (evidence-history [_] (:evidences @a))
  (next-dispatch-sequence [_ jurisdiction] (get-in @a [:dispatch-sequences jurisdiction] 0))
  (next-evidence-sequence [_ jurisdiction] (get-in @a [:evidence-sequences jurisdiction] 0))
  (vehicle-already-dispatched? [_ vehicle-id] (boolean (get-in @a [:vehicles vehicle-id :vehicle-dispatched?])))
  (vehicle-already-certified? [_ vehicle-id] (boolean (get-in @a [:vehicles vehicle-id :conformity-certified?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :vehicle/upsert
      (swap! a update-in [:vehicles (:id value)] merge value)

      :verification/set
      (swap! a assoc-in [:verifications (first path)] payload)

      :eol-screen/set
      (swap! a assoc-in [:eol-screens (first path)] payload)

      :vehicle/mark-dispatched
      (let [vehicle-id (first path)
            {:keys [result vehicle-patch]} (dispatch-vehicle! s vehicle-id)
            jurisdiction (:jurisdiction (vehicle s vehicle-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:dispatch-sequences jurisdiction] (fnil inc 0))
                       (update-in [:vehicles vehicle-id] merge vehicle-patch)
                       (update :dispatches registry/append result))))
        result)

      :vehicle/mark-certified
      (let [vehicle-id (first path)
            {:keys [result vehicle-patch]} (issue-conformity-certificate! s vehicle-id)
            jurisdiction (:jurisdiction (vehicle s vehicle-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:evidence-sequences jurisdiction] (fnil inc 0))
                       (update-in [:vehicles vehicle-id] merge vehicle-patch)
                       (update :evidences registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-vehicles [s vehicles] (when (seq vehicles) (swap! a assoc :vehicles vehicles)) s))

(defn seed-db
  "A MemStore seeded with the demo vehicle set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :verifications {} :eol-screens {} :ledger [] :dispatch-sequences {}
                           :dispatches [] :evidence-sequences {} :evidences []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (verification/eol-screen payloads, ledger facts,
  dispatch/evidence records) are stored as EDN strings so `langchain.
  db` doesn't expand them into sub-entities -- the same convention
  every sibling actor's store uses."
  {:vehicle/id                    {:db/unique :db.unique/identity}
   :verification/vehicle-id       {:db/unique :db.unique/identity}
   :eol-screen/vehicle-id         {:db/unique :db.unique/identity}
   :ledger/seq                        {:db/unique :db.unique/identity}
   :dispatch/seq                      {:db/unique :db.unique/identity}
   :evidence/seq                      {:db/unique :db.unique/identity}
   :dispatch-sequence/jurisdiction    {:db/unique :db.unique/identity}
   :evidence-sequence/jurisdiction    {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- vehicle->tx [{:keys [id vehicle-name emissions-deviation-actual emissions-deviation-min emissions-deviation-max
                             structural-deviation-actual structural-deviation-min structural-deviation-max
                             eol-defect-unresolved? robotics-sim-verified? robotics-sim-record
                             vehicle-dispatched? conformity-certified?
                             jurisdiction status dispatch-number evidence-number]}]
  (cond-> {:vehicle/id id}
    vehicle-name                                (assoc :vehicle/vehicle-name vehicle-name)
    emissions-deviation-actual                  (assoc :vehicle/emissions-deviation-actual emissions-deviation-actual)
    emissions-deviation-min                     (assoc :vehicle/emissions-deviation-min emissions-deviation-min)
    emissions-deviation-max                     (assoc :vehicle/emissions-deviation-max emissions-deviation-max)
    structural-deviation-actual                 (assoc :vehicle/structural-deviation-actual structural-deviation-actual)
    structural-deviation-min                    (assoc :vehicle/structural-deviation-min structural-deviation-min)
    structural-deviation-max                    (assoc :vehicle/structural-deviation-max structural-deviation-max)
    (some? eol-defect-unresolved?)              (assoc :vehicle/eol-defect-unresolved? eol-defect-unresolved?)
    (some? robotics-sim-verified?)               (assoc :vehicle/robotics-sim-verified? robotics-sim-verified?)
    (some? robotics-sim-record)                  (assoc :vehicle/robotics-sim-record (enc robotics-sim-record))
    (some? vehicle-dispatched?)                 (assoc :vehicle/vehicle-dispatched? vehicle-dispatched?)
    (some? conformity-certified?)               (assoc :vehicle/conformity-certified? conformity-certified?)
    jurisdiction                                (assoc :vehicle/jurisdiction jurisdiction)
    status                                      (assoc :vehicle/status status)
    dispatch-number                             (assoc :vehicle/dispatch-number dispatch-number)
    evidence-number                             (assoc :vehicle/evidence-number evidence-number)))

(def ^:private vehicle-pull
  [:vehicle/id :vehicle/vehicle-name :vehicle/emissions-deviation-actual
   :vehicle/emissions-deviation-min :vehicle/emissions-deviation-max
   :vehicle/structural-deviation-actual :vehicle/structural-deviation-min :vehicle/structural-deviation-max
   :vehicle/eol-defect-unresolved? :vehicle/robotics-sim-verified? :vehicle/robotics-sim-record
   :vehicle/vehicle-dispatched? :vehicle/conformity-certified?
   :vehicle/jurisdiction :vehicle/status :vehicle/dispatch-number :vehicle/evidence-number])

(defn- pull->vehicle [m]
  (when (:vehicle/id m)
    {:id (:vehicle/id m) :vehicle-name (:vehicle/vehicle-name m)
     :emissions-deviation-actual (:vehicle/emissions-deviation-actual m)
     :emissions-deviation-min (:vehicle/emissions-deviation-min m)
     :emissions-deviation-max (:vehicle/emissions-deviation-max m)
     :structural-deviation-actual (:vehicle/structural-deviation-actual m)
     :structural-deviation-min (:vehicle/structural-deviation-min m)
     :structural-deviation-max (:vehicle/structural-deviation-max m)
     :eol-defect-unresolved? (boolean (:vehicle/eol-defect-unresolved? m))
     :robotics-sim-verified? (boolean (:vehicle/robotics-sim-verified? m))
     :robotics-sim-record (dec* (:vehicle/robotics-sim-record m))
     :vehicle-dispatched? (boolean (:vehicle/vehicle-dispatched? m))
     :conformity-certified? (boolean (:vehicle/conformity-certified? m))
     :jurisdiction (:vehicle/jurisdiction m) :status (:vehicle/status m)
     :dispatch-number (:vehicle/dispatch-number m) :evidence-number (:vehicle/evidence-number m)}))

(defrecord DatomicStore [conn]
  Store
  (vehicle [_ id]
    (pull->vehicle (d/pull (d/db conn) vehicle-pull [:vehicle/id id])))
  (all-vehicles [_]
    (->> (d/q '[:find [?id ...] :where [?e :vehicle/id ?id]] (d/db conn))
         (map #(pull->vehicle (d/pull (d/db conn) vehicle-pull [:vehicle/id %])))
         (sort-by :id)))
  (eol-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?k :eol-screen/vehicle-id ?aid] [?k :eol-screen/payload ?p]]
              (d/db conn) id)))
  (requirements-verification-of [_ vehicle-id]
    (dec* (d/q '[:find ?p . :in $ ?aid
                :where [?a :verification/vehicle-id ?aid] [?a :verification/payload ?p]]
              (d/db conn) vehicle-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (dispatch-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :dispatch/seq ?s] [?e :dispatch/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (evidence-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :evidence/seq ?s] [?e :evidence/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-dispatch-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :dispatch-sequence/jurisdiction ?j] [?e :dispatch-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-evidence-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :evidence-sequence/jurisdiction ?j] [?e :evidence-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (vehicle-already-dispatched? [s vehicle-id]
    (boolean (:vehicle-dispatched? (vehicle s vehicle-id))))
  (vehicle-already-certified? [s vehicle-id]
    (boolean (:conformity-certified? (vehicle s vehicle-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :vehicle/upsert
      (d/transact! conn [(vehicle->tx value)])

      :verification/set
      (d/transact! conn [{:verification/vehicle-id (first path) :verification/payload (enc payload)}])

      :eol-screen/set
      (d/transact! conn [{:eol-screen/vehicle-id (first path) :eol-screen/payload (enc payload)}])

      :vehicle/mark-dispatched
      (let [vehicle-id (first path)
            {:keys [result vehicle-patch]} (dispatch-vehicle! s vehicle-id)
            jurisdiction (:jurisdiction (vehicle s vehicle-id))
            next-n (inc (next-dispatch-sequence s jurisdiction))]
        (d/transact! conn
                     [(vehicle->tx (assoc vehicle-patch :id vehicle-id))
                      {:dispatch-sequence/jurisdiction jurisdiction :dispatch-sequence/next next-n}
                      {:dispatch/seq (count (dispatch-history s)) :dispatch/record (enc (get result "record"))}])
        result)

      :vehicle/mark-certified
      (let [vehicle-id (first path)
            {:keys [result vehicle-patch]} (issue-conformity-certificate! s vehicle-id)
            jurisdiction (:jurisdiction (vehicle s vehicle-id))
            next-n (inc (next-evidence-sequence s jurisdiction))]
        (d/transact! conn
                     [(vehicle->tx (assoc vehicle-patch :id vehicle-id))
                      {:evidence-sequence/jurisdiction jurisdiction :evidence-sequence/next next-n}
                      {:evidence/seq (count (evidence-history s)) :evidence/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-vehicles [s vehicles]
    (when (seq vehicles) (d/transact! conn (mapv vehicle->tx (vals vehicles)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:vehicles ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [vehicles]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-vehicles s vehicles))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo vehicle set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
