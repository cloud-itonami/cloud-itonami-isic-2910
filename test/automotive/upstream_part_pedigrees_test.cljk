(ns automotive.upstream-part-pedigrees-test
  "ADR-2607999960's cross-actor supply-chain-linkage check
  (`automotive.governor/upstream-part-pedigrees-claims-out-of-
  tolerance-violations`), exercised with HAND-BUILT `kotoba.pedigree`
  records (via the real `kotoba.pedigree/claim` constructor -- never a
  raw map literal that merely LOOKS like a pedigree). The genuine
  cross-repo proof -- actual calls into `cloud-itonami-isic-2930`'s
  `autoparts.export/pedigree-for-part-lot` (and, for the full 2-hop
  chain, `cloud-itonami-isic-2410`'s `steelworks.export/pedigree-for-
  heat`) -- lives in `test-cross-repo/automotive/pedigree_integration_
  test.clj` (a separate alias, see deps.edn); this file only proves
  the GOVERNOR check itself is correct in isolation, independent of
  which upstream actor produced the pedigree."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.pedigree :as pedigree]
            [langgraph.graph :as g]
            [automotive.governor :as governor]
            [automotive.store :as store]
            [automotive.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :homologation-engineer :phase 3})

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify! [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :type-approval-rules/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- simulate-robotics! [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-robotics") {:op :robotics/simulate-assembly-line :subject subject} operator)
  (approve! actor (str tid-prefix "-robotics")))

(defn- attach-pedigrees! [actor tid-prefix subject pedigrees]
  (exec-op actor (str tid-prefix "-pedigrees")
           {:op :vehicle/intake :subject subject
            :patch {:id subject :upstream-part-pedigrees pedigrees}}
           operator))

(defn- clean-pedigree
  ([] (clean-pedigree "PEDIGREE-lot-1" "lot-1"))
  ([id subject-lot-id]
   (pedigree/claim id subject-lot-id "cloud-itonami-isic-2930"
                    {:proof-load-force-n (+ governor/min-upstream-part-proof-load-n 100.0)}
                    :evidence-basis ["autoparts.robotics/run-pull-test"]
                    :issued-at "2026-07-15")))

(defn- weak-pedigree
  ([] (weak-pedigree "PEDIGREE-lot-2" "lot-2"))
  ([id subject-lot-id]
   (pedigree/claim id subject-lot-id "cloud-itonami-isic-2930"
                    {:proof-load-force-n (- governor/min-upstream-part-proof-load-n 100.0)}
                    :evidence-basis ["autoparts.robotics/run-pull-test"]
                    :issued-at "2026-07-15")))

(deftest absent-upstream-part-pedigrees-is-a-no-op
  (testing "a vehicle with no :upstream-part-pedigrees dispatches exactly as before this ADR -- no new violation"
    (let [[db actor] (fresh)
          _ (verify! actor "t1pre" "vehicle-1")
          _ (simulate-robotics! actor "t1pre2" "vehicle-1")
          res (exec-op actor "t1" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
      (is (nil? (:upstream-part-pedigrees (store/vehicle db "vehicle-1"))))
      (is (= :interrupted (:status res)) "still escalates for human approval, same as before -- no HARD hold introduced")
      (let [r2 (approve! actor "t1")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:vehicle-dispatched? (store/vehicle db "vehicle-1"))))))))

(deftest empty-upstream-part-pedigrees-is-also-a-no-op
  (testing "an explicitly empty :upstream-part-pedigrees vector is likewise a no-op, not a HARD hold"
    (let [[_db actor] (fresh)
          _ (verify! actor "t2pre" "vehicle-1")
          _ (simulate-robotics! actor "t2pre2" "vehicle-1")
          _ (attach-pedigrees! actor "t2pre3" "vehicle-1" [])
          res (exec-op actor "t2" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))))))

(deftest valid-in-tolerance-upstream-part-pedigrees-dispatch-normally
  (testing "shape-valid pedigrees whose claims clear the acceptance floor do not block dispatch"
    (let [[db actor] (fresh)
          _ (verify! actor "t3pre" "vehicle-1")
          _ (simulate-robotics! actor "t3pre2" "vehicle-1")
          _ (attach-pedigrees! actor "t3pre3" "vehicle-1" [(clean-pedigree)])
          res (exec-op actor "t3" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
      (is (= 1 (count (:upstream-part-pedigrees (store/vehicle db "vehicle-1")))))
      (is (= :interrupted (:status res)) "still escalates for human approval -- actuation is never auto")
      (let [r2 (approve! actor "t3")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:vehicle-dispatched? (store/vehicle db "vehicle-1"))))))))

(deftest multiple-valid-pedigrees-all-clearing-dispatch-normally
  (testing "a vehicle with SEVERAL upstream part pedigrees, all clearing the floor, dispatches normally -- a vehicle has many parts"
    (let [[db actor] (fresh)
          _ (verify! actor "t4pre" "vehicle-1")
          _ (simulate-robotics! actor "t4pre2" "vehicle-1")
          _ (attach-pedigrees! actor "t4pre3" "vehicle-1"
                                [(clean-pedigree "PEDIGREE-lot-1" "lot-1")
                                 (clean-pedigree "PEDIGREE-lot-3" "lot-3")])
          res (exec-op actor "t4" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
      (is (= 2 (count (:upstream-part-pedigrees (store/vehicle db "vehicle-1")))))
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t4")]
        (is (= :commit (get-in r2 [:state :disposition])))))))

(deftest upstream-part-pedigree-claims-out-of-tolerance-is-held
  (testing "a shape-valid pedigree whose claim falls below the acceptance floor -> HARD hold, independent of emissions/robotics/evidence being otherwise clean"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "vehicle-1")
          _ (simulate-robotics! actor "t5pre2" "vehicle-1")
          _ (attach-pedigrees! actor "t5pre3" "vehicle-1" [(weak-pedigree)])
          res (exec-op actor "t5" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:upstream-part-pedigree-claims-out-of-tolerance} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest one-weak-pedigree-among-many-still-holds
  (testing "ONE out-of-tolerance entry among several otherwise-clean upstream part pedigrees still HARD-holds -- every part must independently clear"
    (let [[db actor] (fresh)
          _ (verify! actor "t6pre" "vehicle-1")
          _ (simulate-robotics! actor "t6pre2" "vehicle-1")
          _ (attach-pedigrees! actor "t6pre3" "vehicle-1"
                                [(clean-pedigree "PEDIGREE-lot-1" "lot-1")
                                 (weak-pedigree "PEDIGREE-lot-2" "lot-2")])
          res (exec-op actor "t6" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:upstream-part-pedigree-claims-out-of-tolerance} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest upstream-part-pedigree-invalid-shape-is-held
  (testing "an attached map that fails kotoba.pedigree/valid? (e.g. a non-numeric claim, mimicking a self-reported string) -> HARD hold, never trusted at face value"
    (let [[db actor] (fresh)
          bad-pedigree (assoc (clean-pedigree) :pedigree/claims {:proof-load-force-n "plenty"})
          _ (verify! actor "t7pre" "vehicle-1")
          _ (simulate-robotics! actor "t7pre2" "vehicle-1")
          _ (attach-pedigrees! actor "t7pre3" "vehicle-1" [bad-pedigree])
          res (exec-op actor "t7" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
      (is (false? (pedigree/valid? bad-pedigree)) "sanity: the fixture really is shape-invalid")
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:upstream-part-pedigree-invalid-shape} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest upstream-part-pedigree-with-invalid-nested-upstream-is-held
  (testing "a shape-valid part pedigree embedding a SHAPE-INVALID nested :pedigree/upstream (e.g. a malformed steel-heat pedigree) -> HARD hold -- recursive re-verification catches a bad link at ANY hop of the chain, not just the immediate one"
    (let [[db actor] (fresh)
          bad-steel (pedigree/claim "PEDIGREE-heat-1" "heat-1" "cloud-itonami-isic-2410"
                                     {:tensile-test-load-n "plenty"}
                                     :evidence-basis ["steelworks.robotics/run-tensile-test"]
                                     :issued-at "2026-07-15")
          part-pedigree (pedigree/claim "PEDIGREE-lot-1" "lot-1" "cloud-itonami-isic-2930"
                                         {:proof-load-force-n (+ governor/min-upstream-part-proof-load-n 100.0)}
                                         :evidence-basis ["autoparts.robotics/run-pull-test"]
                                         :issued-at "2026-07-15"
                                         :upstream bad-steel)
          _ (verify! actor "t8pre" "vehicle-1")
          _ (simulate-robotics! actor "t8pre2" "vehicle-1")
          _ (attach-pedigrees! actor "t8pre3" "vehicle-1" [part-pedigree])
          res (exec-op actor "t8" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
      (is (false? (pedigree/valid? bad-steel)) "sanity: the embedded upstream really is shape-invalid on its own")
      (is (false? (pedigree/valid? part-pedigree)) "sanity: a poisoned nested upstream poisons the whole chain")
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:upstream-part-pedigree-invalid-shape} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest upstream-part-pedigrees-check-scoped-to-dispatch-vehicle-op
  (testing "the check only fires for :actuation/dispatch-vehicle -- an out-of-tolerance pedigree already on file does not block an unrelated op"
    (let [[_db actor] (fresh)
          _ (attach-pedigrees! actor "t9pre" "vehicle-1" [(weak-pedigree)])
          res (exec-op actor "t9" {:op :type-approval-rules/verify :subject "vehicle-1"} operator)]
      (is (= :interrupted (:status res)) "type-approval-rules/verify is unaffected by an out-of-tolerance upstream part pedigree")
      (let [r2 (approve! actor "t9")]
        (is (= :commit (get-in r2 [:state :disposition])))))))
