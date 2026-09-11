(ns automotive.full-supply-chain-integration-test
  "ADR-2607999970's headline proof: the GENUINE full 3-hop, 4-actor
  supply-chain-pedigree chain --

    cloud-itonami-isic-0710 (iron-ore mining)
      -> cloud-itonami-isic-2410 (basic iron and steel)
      -> cloud-itonami-isic-2930 (auto parts)
      -> cloud-itonami-isic-2910 (THIS actor, motor-vehicle assembly)

  -- built end to end from REAL cross-repo calls into every upstream
  actor's OWN real export/store/robotics functions (never a
  hand-written EDN literal that merely mimics what those functions
  would produce), fed into THIS actor's UNMODIFIED governor
  (`automotive.governor`'s `upstream-part-pedigrees-claims-out-of-
  tolerance-violations`, landed by ADR-2607999960, is NOT touched by
  ADR-2607999970 -- this file exists to VERIFY, not assume, that its
  existing recursive `kotoba.pedigree/valid?` shape check and its
  existing top-level `:proof-load-force-n` claim check correctly
  handle a part pedigree whose `:pedigree/upstream` chain is now
  genuinely TWO levels deep (steel embeds ore) rather than one).

  This repo's `:cross-repo-test` alias (see deps.edn) declares THREE
  `:local/root` siblings (`cloud-itonami-isic-2930`, `cloud-itonami-
  isic-2410`, `cloud-itonami-isic-0710`) directly -- practical here
  because tools.deps does not compose another `:local/root`
  dependency's own alias-scoped deps (isic-2930's OWN dependency on
  isic-2410, and isic-2410's OWN dependency on isic-0710, are each
  invisible from here), the SAME mechanism `automotive.pedigree-
  integration-test` already established for the first two siblings
  one link earlier -- this file only adds the third.

  Run with `clojure -M:dev:cross-repo-test`. Still no live network
  call between actors at runtime: this is a build-time classpath
  dependency exercised by tests, same category as every other
  `:local/root` dependency in this fleet."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.pedigree :as pedigree]
            [langgraph.graph :as g]
            [ironops.export :as ore-export]
            [ironops.store :as ore-store]
            [steelworks.export :as steel-export]
            [steelworks.robotics :as steel-robotics]
            [autoparts.export :as parts-export]
            [autoparts.robotics :as parts-robotics]
            [automotive.governor :as governor]
            [automotive.store :as store]
            [automotive.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :homologation-engineer :phase 3})

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

(defn- real-ore-pedigree
  "Hop 1, THE genuine cross-repo call into cloud-itonami-isic-0710:
  writes a real production record into a real `ironops.store`
  MemStore, reads it back out via that repo's OWN store protocol, and
  packages it via that repo's OWN `ironops.export/pedigree-for-
  production-record` -- never a hand-typed EDN literal."
  [record-id grade-actual quantity-tonnes issued-at]
  (let [st (ore-store/mem-store)
        st' (ore-store/add-production-record st record-id
                                              {:site-id "iron-site-001"
                                               :grade-actual grade-actual
                                               :grade-min 0.0 :grade-max 100.0
                                               :quantity-tonnes quantity-tonnes})
        rec (ore-store/production-record st' record-id)]
    (ore-export/pedigree-for-production-record rec issued-at)))

(defn- real-steel-pedigree
  "Hop 2, THE genuine cross-repo call into cloud-itonami-isic-2410:
  builds a real steel-heat record, runs that repo's OWN real
  `physics-2d` tensile-test simulation, and packages the result via
  that repo's OWN `steelworks.export/pedigree-for-heat` -- optionally
  embedding an upstream ore pedigree first, exactly as isic-2410's
  own governor requires before a real heat may dispatch."
  ([heat-id coupon-mass-kg issued-at]
   (real-steel-pedigree heat-id coupon-mass-kg issued-at nil))
  ([heat-id coupon-mass-kg issued-at upstream-ore-pedigree]
   (let [base (merge {:id heat-id :coupon-mass-kg coupon-mass-kg}
                      (steel-robotics/tensile-test-telemetry-for {:coupon-mass-kg coupon-mass-kg}))
         heat (cond-> base
                (some? upstream-ore-pedigree) (assoc :upstream-ore-pedigree upstream-ore-pedigree))]
     (steel-export/pedigree-for-heat heat issued-at))))

(defn- real-part-pedigree
  "Hop 3, THE genuine cross-repo call into cloud-itonami-isic-2930:
  builds a real part-lot record, runs that repo's OWN real `physics-
  2d` pull-test simulation, and packages the result via that repo's
  OWN `autoparts.export/pedigree-for-part-lot` -- optionally embedding
  an upstream steel pedigree first."
  ([part-lot-id joint-mass-kg issued-at]
   (real-part-pedigree part-lot-id joint-mass-kg issued-at nil))
  ([part-lot-id joint-mass-kg issued-at upstream-steel-pedigree]
   (let [base (merge {:id part-lot-id :joint-mass-kg joint-mass-kg}
                      (parts-robotics/pull-test-telemetry-for {:joint-mass-kg joint-mass-kg}))
         part-lot (cond-> base
                    (some? upstream-steel-pedigree) (assoc :upstream-pedigree upstream-steel-pedigree))]
     (parts-export/pedigree-for-part-lot part-lot issued-at))))

(deftest genuine-3-hop-4-actor-chain-is-shape-valid-end-to-end
  (testing "ore -> steel -> part, all three hops genuine cross-repo calls, is shape-valid end-to-end and genuinely 2 levels deep from the part pedigree's own vantage point"
    (let [ore (real-ore-pedigree "prod-strong" 65.0 6000.0 "2026-07-16")
          steel (real-steel-pedigree "heat-strong" 5.0 "2026-07-16" ore)
          part (real-part-pedigree "lot-strong" 2.5 "2026-07-16" steel)]
      (is (true? (pedigree/valid? ore)))
      (is (true? (pedigree/valid? steel)))
      (is (true? (pedigree/valid? part)))
      (is (= steel (:pedigree/upstream part))
          "the REAL isic-2410 pedigree is embedded verbatim at depth 1")
      (is (= ore (get-in part [:pedigree/upstream :pedigree/upstream]))
          "the REAL isic-0710 pedigree is embedded verbatim at depth 2 -- a genuine 3-hop chain, never flattened/summarized")
      (testing "each hop's own claim stays independently readable at its own depth"
        (is (= 65.0 (pedigree/claim-value (get-in part [:pedigree/upstream :pedigree/upstream]) :grade-actual)))
        (is (= 8000.0 (pedigree/claim-value (:pedigree/upstream part) :tensile-test-load-n)))
        (is (= 5000.0 (pedigree/claim-value part :proof-load-force-n)))))))

(deftest real-4-actor-chain-genuinely-clears-automotive-governor-with-no-automotive-side-code-change
  (testing "a genuinely 2-level-deep upstream chain (steel embeds ore) clears automotive.governor's EXISTING, UNMODIFIED (since ADR-2607999960) independent acceptance check end-to-end, and a real vehicle dispatches -- proving kotoba.pedigree/valid?'s recursive verification, not any new automotive-side code, is what makes the deeper chain work"
    (let [ore (real-ore-pedigree "prod-strong" 65.0 6000.0 "2026-07-16")
          steel (real-steel-pedigree "heat-strong" 5.0 "2026-07-16" ore)
          part-pedigree (real-part-pedigree "lot-strong" 2.5 "2026-07-16" steel)
          _ (is (>= (pedigree/claim-value part-pedigree :proof-load-force-n) governor/min-upstream-part-proof-load-n)
                "sanity: this part-lot's REAL simulated proof load actually clears automotive's own disclosed floor")
          db (store/seed-db)
          actor (op/build db)]
      (verify! actor "e1pre" "vehicle-1")
      (simulate-robotics! actor "e1pre2" "vehicle-1")
      (attach-pedigrees! actor "e1pre3" "vehicle-1" [part-pedigree])
      (is (= [part-pedigree] (:upstream-part-pedigrees (store/vehicle db "vehicle-1")))
          "the REAL 3-hop cross-repo pedigree landed on the vehicle record unmodified")
      (let [res (exec-op actor "e1" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
        (is (= :interrupted (:status res))
            "governor's independent re-verification found no violation from the real 3-hop pedigree -- escalates for human approval, same as any clean dispatch")
        (let [r2 (approve! actor "e1")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:vehicle-dispatched? (store/vehicle db "vehicle-1")))))))))

(deftest real-4-actor-chain-with-shape-invalid-ore-poisons-the-whole-chain-and-is-caught-by-automotives-existing-check
  (testing "a shape-invalid ore pedigree TWO LEVELS DEEP (never a top-level defect) still makes the top-level part pedigree fail kotoba.pedigree/valid?, and automotive.governor's EXISTING, UNMODIFIED :upstream-part-pedigree-invalid-shape check -- which only ever calls pedigree/valid? on the TOP-level entry -- genuinely catches it via that recursive check, with zero new automotive-side code"
    (let [ore (real-ore-pedigree "prod-corrupt" 65.0 6000.0 "2026-07-16")
          bad-ore (assoc ore :pedigree/claims {:grade-actual "high"})
          steel (real-steel-pedigree "heat-strong2" 5.0 "2026-07-16" bad-ore)
          part-pedigree (real-part-pedigree "lot-strong2" 2.5 "2026-07-16" steel)
          db (store/seed-db)
          actor (op/build db)]
      (is (false? (pedigree/valid? bad-ore)) "sanity: the corrupted ore fixture really is shape-invalid on its own")
      (is (false? (pedigree/valid? steel)) "the steel pedigree embedding it is ALSO invalid -- valid? poisons the whole chain")
      (is (false? (pedigree/valid? part-pedigree)) "the part pedigree, 2 levels away from the actual defect, is ALSO invalid")
      (verify! actor "e2pre" "vehicle-1")
      (simulate-robotics! actor "e2pre2" "vehicle-1")
      (attach-pedigrees! actor "e2pre3" "vehicle-1" [part-pedigree])
      (let [res (exec-op actor "e2" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:upstream-part-pedigree-invalid-shape} (-> (store/ledger db) last :basis)))
        (is (empty? (store/dispatch-history db)))))))

(deftest real-4-actor-chain-part-lots-own-floor-still-gates-regardless-of-upstream-strength
  (testing "automotive's acceptance check is scoped to the PART pedigree's OWN top-level claim, never a deeper embedded claim -- a too-light part-lot still HARD-holds even when its embedded steel/ore hops are individually strong, exactly as ADR-2607999960 already established one link earlier (no regression, no new behavior introduced by the deeper chain)"
    (let [ore (real-ore-pedigree "prod-strong3" 65.0 6000.0 "2026-07-16")
          steel (real-steel-pedigree "heat-strong3" 5.0 "2026-07-16" ore)
          part-pedigree (real-part-pedigree "lot-weak" 0.6 "2026-07-16" steel)
          _ (is (< (pedigree/claim-value part-pedigree :proof-load-force-n) governor/min-upstream-part-proof-load-n)
                "sanity: this part-lot's REAL simulated proof load falls short of automotive's own disclosed floor, despite a strong upstream chain")
          db (store/seed-db)
          actor (op/build db)]
      (verify! actor "e3pre" "vehicle-1")
      (simulate-robotics! actor "e3pre2" "vehicle-1")
      (attach-pedigrees! actor "e3pre3" "vehicle-1" [part-pedigree])
      (let [res (exec-op actor "e3" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:upstream-part-pedigree-claims-out-of-tolerance} (-> (store/ledger db) last :basis)))
        (is (empty? (store/dispatch-history db)))))))
