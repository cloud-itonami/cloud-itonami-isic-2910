(ns automotive.pedigree-integration-test
  "ADR-2607999960's critical end-to-end proof: GENUINE cross-repo
  calls into `cloud-itonami-isic-2930`'s OWN `autoparts.export`/
  `autoparts.robotics` (a real part-lot -> a real `physics-2d`
  pull-test simulation -> a real `kotoba.pedigree` record) -- and, for
  the FULL 2-hop chain, ALSO into `cloud-itonami-isic-2410`'s OWN
  `steelworks.export`/`steelworks.robotics` (a real steel heat -> a
  real `physics-2d` tensile-test simulation -> a real `kotoba.
  pedigree` record), attached to the part-lot as its own
  `:upstream-pedigree` BEFORE calling isic-2930's `pedigree-for-part-
  lot`, so the resulting part pedigree genuinely EMBEDS the steel
  pedigree (`:pedigree/upstream`) -- never a hand-written EDN literal
  that merely mimics what those functions would produce.

  Genuine 2-hop, not just 1-hop: this repo's `:cross-repo-test` alias
  (see deps.edn) declares BOTH `cloud-itonami-isic-2930` AND
  `cloud-itonami-isic-2410` as `:local/root` siblings directly --
  practical here because both are ordinary sibling checkouts one
  level up, the SAME mechanism `cloud-itonami-isic-2930`'s own
  `:cross-repo-test` alias already established for its isic-2410
  dependency (tools.deps does not compose another :local/root
  dependency's own alias-scoped deps, so isic-2930's OWN alias
  dependency on isic-2410 is invisible from here -- this alias
  declares isic-2410 itself, one level further back).

  Run with `clojure -M:dev:cross-repo-test` -- kept OUT of the default
  `:test` alias (this file lives in `test-cross-repo/`, a separate
  source root) because it requires same-org/cross-org sibling
  checkouts a casual fork of just THIS repo would not have. Still no
  live network call between actors at runtime: this is a build-time
  classpath dependency exercised by tests, same category as every
  other `:local/root` dependency in this fleet."
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.pedigree :as pedigree]
            [langgraph.graph :as g]
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

(defn- real-steel-pedigree
  "Hop 1, THE genuine cross-repo call into cloud-itonami-isic-2410:
  builds a real steel-heat record, runs that repo's OWN real
  `physics-2d` tensile-test simulation (`steelworks.robotics/tensile-
  test-telemetry-for`), and packages the result via that repo's OWN
  `steelworks.export/pedigree-for-heat` -- never a hand-typed EDN
  literal."
  [heat-id coupon-mass-kg issued-at]
  (let [heat (merge {:id heat-id :coupon-mass-kg coupon-mass-kg}
                     (steel-robotics/tensile-test-telemetry-for {:coupon-mass-kg coupon-mass-kg}))]
    (steel-export/pedigree-for-heat heat issued-at)))

(defn- real-part-pedigree
  "Hop 2, THE genuine cross-repo call into cloud-itonami-isic-2930:
  builds a real part-lot record, runs that repo's OWN real `physics-
  2d` pull-test simulation (`autoparts.robotics/pull-test-telemetry-
  for`), and packages the result via that repo's OWN `autoparts.
  export/pedigree-for-part-lot` -- never a hand-typed EDN literal.
  When `upstream-steel-pedigree` is given, attaches it to the part-lot
  as `:upstream-pedigree` FIRST (mirroring exactly how isic-2930's own
  governor independently re-verifies an :upstream-pedigree before a
  part-lot may ship, ADR-2607999950), so the resulting part pedigree
  genuinely EMBEDS it -- a real 2-hop chain, not a hand-assembled one."
  ([part-lot-id joint-mass-kg issued-at]
   (real-part-pedigree part-lot-id joint-mass-kg issued-at nil))
  ([part-lot-id joint-mass-kg issued-at upstream-steel-pedigree]
   (let [base (merge {:id part-lot-id :joint-mass-kg joint-mass-kg}
                      (parts-robotics/pull-test-telemetry-for {:joint-mass-kg joint-mass-kg}))
         part-lot (cond-> base
                    (some? upstream-steel-pedigree) (assoc :upstream-pedigree upstream-steel-pedigree))]
     (parts-export/pedigree-for-part-lot part-lot issued-at))))

(deftest real-cross-repo-part-pedigree-is-shape-valid
  (testing "a pedigree built from a REAL cloud-itonami-isic-2930 simulation passes kotoba.pedigree/valid?"
    (let [p (real-part-pedigree "lot-strong" 2.5 "2026-07-15")]
      (is (some? p))
      (is (true? (pedigree/valid? p)))
      (is (= "cloud-itonami-isic-2930" (:pedigree/issuing-actor p)))
      (testing "the claim is the REAL simulated reading, not invented -- independently recomputing the same simulation yields the identical number"
        (is (= (pedigree/claim-value p :proof-load-force-n)
               (:sim-proof-load-force (parts-robotics/pull-test-telemetry-for {:joint-mass-kg 2.5}))))
        (is (= 5000.0 (pedigree/claim-value p :proof-load-force-n))
            "documents the actual real-simulation value at joint-mass-kg=2.5, for a human reader's sanity")))))

(deftest genuine-2-hop-chain-is-shape-valid-end-to-end
  (testing "a part pedigree built with a REAL embedded upstream steel pedigree (both hops genuine cross-repo calls) is shape-valid end-to-end, and the chain is genuinely 2 hops deep"
    (let [steel (real-steel-pedigree "heat-strong" 5.0 "2026-07-15")
          part (real-part-pedigree "lot-strong" 2.5 "2026-07-15" steel)]
      (is (true? (pedigree/valid? steel)))
      (is (true? (pedigree/valid? part)))
      (is (= steel (:pedigree/upstream part))
          "the REAL isic-2410 pedigree is embedded verbatim, not summarized/flattened")
      (testing "each hop's own claim stays independently readable"
        (is (= 8000.0 (pedigree/claim-value (:pedigree/upstream part) :tensile-test-load-n)))
        (is (= 5000.0 (pedigree/claim-value part :proof-load-force-n)))))))

(deftest real-cross-repo-part-pedigree-genuinely-clears-automotive-governor
  (testing "a heavy-enough real part-lot's pedigree (embedding a real upstream steel pedigree, the full 2-hop chain) genuinely clears automotive.governor's independent acceptance check end-to-end, and a real vehicle dispatches"
    (let [steel (real-steel-pedigree "heat-strong" 5.0 "2026-07-15")
          part-pedigree (real-part-pedigree "lot-strong" 2.5 "2026-07-15" steel)
          _ (is (>= (pedigree/claim-value part-pedigree :proof-load-force-n) governor/min-upstream-part-proof-load-n)
                "sanity: this part-lot's REAL simulated proof load actually clears automotive's own disclosed floor")
          db (store/seed-db)
          actor (op/build db)]
      (verify! actor "e1pre" "vehicle-1")
      (simulate-robotics! actor "e1pre2" "vehicle-1")
      (attach-pedigrees! actor "e1pre3" "vehicle-1" [part-pedigree])
      (is (= [part-pedigree] (:upstream-part-pedigrees (store/vehicle db "vehicle-1")))
          "the REAL cross-repo pedigree (with its REAL embedded upstream steel pedigree) landed on the vehicle record unmodified")
      (let [res (exec-op actor "e1" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
        (is (= :interrupted (:status res))
            "governor's independent re-verification found no violation from the real 2-hop pedigree -- escalates for human approval, same as any clean dispatch")
        (let [r2 (approve! actor "e1")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:vehicle-dispatched? (store/vehicle db "vehicle-1")))))))))

(deftest real-cross-repo-part-pedigree-genuinely-fails-automotive-governor
  (testing "a too-light real part-lot's pedigree genuinely fails automotive.governor's independent acceptance check end-to-end -- HARD hold, derived from a REAL cloud-itonami-isic-2930 simulation output, never a hand-crafted failing fixture"
    (let [part-pedigree (real-part-pedigree "lot-weak" 0.6 "2026-07-15")
          _ (is (< (pedigree/claim-value part-pedigree :proof-load-force-n) governor/min-upstream-part-proof-load-n)
                "sanity: this part-lot's REAL simulated proof load actually falls short of automotive's own disclosed floor")
          db (store/seed-db)
          actor (op/build db)]
      (verify! actor "e2pre" "vehicle-1")
      (simulate-robotics! actor "e2pre2" "vehicle-1")
      (attach-pedigrees! actor "e2pre3" "vehicle-1" [part-pedigree])
      (let [res (exec-op actor "e2" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:upstream-part-pedigree-claims-out-of-tolerance} (-> (store/ledger db) last :basis)))
        (is (empty? (store/dispatch-history db)))))))
