(ns automotive.governor-contract-test
  "The governor contract as executable tests -- the motor-vehicle-
  manufacturer analog of `cloud-itonami-isic-6512`'s `casualty.
  governor-contract-test`. The single invariant under test:

    Automotive Advisor never dispatches a vehicle action or issues a
    Certificate of Conformity the Automotive Governor would
    reject, `:actuation/dispatch-vehicle`/`:actuation/issue-
    conformity-certificate` NEVER auto-commit at any phase,
    `:vehicle/intake` (no direct capital risk) MAY auto-commit when
    clean, and every decision (commit OR hold) leaves exactly one
    ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [automotive.store :as store]
            [automotive.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :homologation-engineer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving a requirements
  verification on file. Uses distinct thread-ids per call site by
  suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :type-approval-rules/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` through end-of-line-defect screening -> approve,
  leaving a screening on file. Only safe to call for a vehicle whose
  defect status has already resolved -- an unresolved defect
  HARD-holds the screen itself (see
  `end-of-line-defect-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :end-of-line-quality/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :vehicle/intake :subject "vehicle-1"
                   :patch {:id "vehicle-1" :vehicle-name "Sakura Compact Sedan CS-04"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Compact Sedan CS-04" (:vehicle-name (store/vehicle db "vehicle-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest requirements-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :type-approval-rules/verify :subject "vehicle-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/requirements-verification-of db "vehicle-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a type-approval-rules/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :type-approval-rules/verify :subject "vehicle-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/requirements-verification-of db "vehicle-1")) "no verification written"))))

(deftest dispatch-vehicle-without-verification-is-held
  (testing "actuation/dispatch-vehicle before any requirements verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest vehicle-emissions-out-of-range-is-held
  (testing "a vehicle whose own emissions deviation falls outside its own spec bounds -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "vehicle-3")
          res (exec-op actor "t5" {:op :actuation/dispatch-vehicle :subject "vehicle-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:vehicle-emissions-out-of-range} (-> (store/ledger db) last :basis)))
      (is (empty? (store/dispatch-history db))))))

(deftest end-of-line-defect-is-held-and-unoverridable
  (testing "an unresolved end-of-line defect on a vehicle -> HOLD, and never reaches request-approval -- exercised via :end-of-line-quality/screen DIRECTLY, not via the actuation op against an unscreened vehicle (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's, leasing's, behavioral's, secondary's, card's, water's, telecom's, turbine's and steelworks's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :end-of-line-quality/screen :subject "vehicle-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:end-of-line-defect-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/eol-screen-of db "vehicle-4")) "no clearance written"))))

(deftest dispatch-vehicle-always-escalates-then-human-decides
  (testing "a clean, fully-verified, in-spec vehicle still ALWAYS interrupts for human approval -- actuation/dispatch-vehicle is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "vehicle-1")
          r1 (exec-op actor "t7" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, dispatch record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:vehicle-dispatched? (store/vehicle db "vehicle-1"))))
          (is (= 1 (count (store/dispatch-history db))) "one draft dispatch record"))))))

(deftest issue-conformity-certificate-always-escalates-then-human-decides
  (testing "a clean, fully-verified, resolved-defect vehicle still ALWAYS interrupts for human approval -- actuation/issue-conformity-certificate is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "vehicle-1")
          _ (screen! actor "t8pre2" "vehicle-1")
          r1 (exec-op actor "t8" {:op :actuation/issue-conformity-certificate :subject "vehicle-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, certificate record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:conformity-certified? (store/vehicle db "vehicle-1"))))
          (is (= 1 (count (store/evidence-history db))) "one draft certificate record"))))))

(deftest dispatch-vehicle-double-dispatch-is-held
  (testing "dispatching the same vehicle's action twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "vehicle-1")
          _ (exec-op actor "t9a" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-dispatched} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/dispatch-history db))) "still only the one earlier dispatch"))))

(deftest issue-conformity-certificate-double-issuance-is-held
  (testing "issuing the same vehicle's Certificate of Conformity twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "vehicle-1")
          _ (screen! actor "t10pre2" "vehicle-1")
          _ (exec-op actor "t10a" {:op :actuation/issue-conformity-certificate :subject "vehicle-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/issue-conformity-certificate :subject "vehicle-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-certified} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/evidence-history db))) "still only the one earlier certificate issuance"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :vehicle/intake :subject "vehicle-1"
                          :patch {:id "vehicle-1" :vehicle-name "Sakura Compact Sedan CS-04"}} operator)
      (exec-op actor "b" {:op :type-approval-rules/verify :subject "vehicle-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
