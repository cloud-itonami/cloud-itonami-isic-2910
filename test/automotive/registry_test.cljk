(ns automotive.registry-test
  (:require [clojure.test :refer [deftest is]]
            [automotive.registry :as r]))

;; ----------------------------- vehicle-emissions-out-of-range? -----------------------------

(deftest not-out-of-range-when-within-bounds
  (is (not (r/vehicle-emissions-out-of-range? {:emissions-deviation-actual 0.05 :emissions-deviation-min -0.10 :emissions-deviation-max 0.10})))
  (is (not (r/vehicle-emissions-out-of-range? {:emissions-deviation-actual -0.10 :emissions-deviation-min -0.10 :emissions-deviation-max 0.10})))
  (is (not (r/vehicle-emissions-out-of-range? {:emissions-deviation-actual 0.10 :emissions-deviation-min -0.10 :emissions-deviation-max 0.10}))))

(deftest out-of-range-when-below-minimum-or-above-maximum
  (is (r/vehicle-emissions-out-of-range? {:emissions-deviation-actual -0.35 :emissions-deviation-min -0.10 :emissions-deviation-max 0.10}))
  (is (r/vehicle-emissions-out-of-range? {:emissions-deviation-actual 0.35 :emissions-deviation-min -0.10 :emissions-deviation-max 0.10})))

(deftest out-of-range-is-false-on-missing-fields
  (is (not (r/vehicle-emissions-out-of-range? {})))
  (is (not (r/vehicle-emissions-out-of-range? {:emissions-deviation-actual 0.35}))))

;; ----------------------------- register-vehicle-dispatch -----------------------------

(deftest dispatch-is-a-draft-not-a-real-dispatch
  (let [result (r/register-vehicle-dispatch "vehicle-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest dispatch-assigns-dispatch-number
  (let [result (r/register-vehicle-dispatch "vehicle-1" "JPN" 7)]
    (is (= (get result "dispatch_number") "JPN-VHL-000007"))
    (is (= (get-in result ["record" "vehicle_id"]) "vehicle-1"))
    (is (= (get-in result ["record" "kind"]) "vehicle-dispatch-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest dispatch-validation-rules
  (is (thrown? Exception (r/register-vehicle-dispatch "" "JPN" 0)))
  (is (thrown? Exception (r/register-vehicle-dispatch "vehicle-1" "" 0)))
  (is (thrown? Exception (r/register-vehicle-dispatch "vehicle-1" "JPN" -1))))

;; ----------------------------- register-conformity-certificate -----------------------------

(deftest certificate-is-a-draft-not-real-certification
  (let [result (r/register-conformity-certificate "vehicle-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest certificate-assigns-evidence-number
  (let [result (r/register-conformity-certificate "vehicle-1" "JPN" 3)]
    (is (= (get result "evidence_number") "JPN-COC-000003"))
    (is (= (get-in result ["record" "vehicle_id"]) "vehicle-1"))
    (is (= (get-in result ["record" "kind"]) "conformity-certificate-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest certificate-validation-rules
  (is (thrown? Exception (r/register-conformity-certificate "" "JPN" 0)))
  (is (thrown? Exception (r/register-conformity-certificate "vehicle-1" "" 0)))
  (is (thrown? Exception (r/register-conformity-certificate "vehicle-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-vehicle-dispatch "vehicle-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-vehicle-dispatch "vehicle-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-VHL-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-VHL-000001" (get-in hist2 [1 "record_id"])))))
