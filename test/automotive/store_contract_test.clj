(ns automotive.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [automotive.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Compact Sedan CS-04" (:vehicle-name (store/vehicle s "vehicle-1"))))
      (is (= "JPN" (:jurisdiction (store/vehicle s "vehicle-1"))))
      (is (= 0.05 (:emissions-deviation-actual (store/vehicle s "vehicle-1"))))
      (is (= -0.10 (:emissions-deviation-min (store/vehicle s "vehicle-1"))))
      (is (= 0.10 (:emissions-deviation-max (store/vehicle s "vehicle-1"))))
      (is (false? (:eol-defect-unresolved? (store/vehicle s "vehicle-1"))))
      (is (= 0.35 (:emissions-deviation-actual (store/vehicle s "vehicle-3"))))
      (is (true? (:eol-defect-unresolved? (store/vehicle s "vehicle-4"))))
      (is (false? (:vehicle-dispatched? (store/vehicle s "vehicle-1"))))
      (is (false? (:conformity-certified? (store/vehicle s "vehicle-1"))))
      (is (= ["vehicle-1" "vehicle-2" "vehicle-3" "vehicle-4"]
             (mapv :id (store/all-vehicles s))))
      (is (nil? (store/eol-screen-of s "vehicle-1")))
      (is (nil? (store/requirements-verification-of s "vehicle-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/dispatch-history s)))
      (is (= [] (store/evidence-history s)))
      (is (zero? (store/next-dispatch-sequence s "JPN")))
      (is (zero? (store/next-evidence-sequence s "JPN")))
      (is (false? (store/vehicle-already-dispatched? s "vehicle-1")))
      (is (false? (store/vehicle-already-certified? s "vehicle-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :vehicle/upsert
                                 :value {:id "vehicle-1" :vehicle-name "Sakura Compact Sedan CS-04"}})
        (is (= "Sakura Compact Sedan CS-04" (:vehicle-name (store/vehicle s "vehicle-1"))))
        (is (= 0.05 (:emissions-deviation-actual (store/vehicle s "vehicle-1"))) "unrelated field preserved"))
      (testing "verification / EOL-screen payloads commit and read back"
        (store/commit-record! s {:effect :verification/set :path ["vehicle-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/requirements-verification-of s "vehicle-1")))
        (store/commit-record! s {:effect :eol-screen/set :path ["vehicle-1"]
                                 :payload {:vehicle-id "vehicle-1" :verdict :resolved}})
        (is (= {:vehicle-id "vehicle-1" :verdict :resolved} (store/eol-screen-of s "vehicle-1"))))
      (testing "vehicle dispatch drafts a record and advances the sequence"
        (store/commit-record! s {:effect :vehicle/mark-dispatched :path ["vehicle-1"]})
        (is (= "JPN-VHL-000000" (get (first (store/dispatch-history s)) "record_id")))
        (is (= "vehicle-dispatch-draft" (get (first (store/dispatch-history s)) "kind")))
        (is (true? (:vehicle-dispatched? (store/vehicle s "vehicle-1"))))
        (is (= 1 (count (store/dispatch-history s))))
        (is (= 1 (store/next-dispatch-sequence s "JPN")))
        (is (true? (store/vehicle-already-dispatched? s "vehicle-1")))
        (is (false? (store/vehicle-already-dispatched? s "vehicle-2"))))
      (testing "Certificate of Conformity drafts a record and advances the sequence"
        (store/commit-record! s {:effect :vehicle/mark-certified :path ["vehicle-1"]})
        (is (= "JPN-COC-000000" (get (first (store/evidence-history s)) "record_id")))
        (is (= "conformity-certificate-draft" (get (first (store/evidence-history s)) "kind")))
        (is (true? (:conformity-certified? (store/vehicle s "vehicle-1"))))
        (is (= 1 (count (store/evidence-history s))))
        (is (= 1 (store/next-evidence-sequence s "JPN")))
        (is (true? (store/vehicle-already-certified? s "vehicle-1")))
        (is (false? (store/vehicle-already-certified? s "vehicle-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/vehicle s "nope")))
    (is (= [] (store/all-vehicles s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/dispatch-history s)))
    (is (= [] (store/evidence-history s)))
    (is (zero? (store/next-dispatch-sequence s "JPN")))
    (is (zero? (store/next-evidence-sequence s "JPN")))
    (store/with-vehicles s {"x" {:id "x" :vehicle-name "n" :emissions-deviation-actual 0.05
                                   :emissions-deviation-min -0.10 :emissions-deviation-max 0.10
                                   :eol-defect-unresolved? false
                                   :vehicle-dispatched? false :conformity-certified? false
                                   :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:vehicle-name (store/vehicle s "x"))))))
