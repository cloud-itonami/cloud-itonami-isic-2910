(ns automotive.export-test
  "Audit-package export contract -- social/regulatory hand-off shape."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [langgraph.graph :as g]
            [automotive.export :as export]
            [automotive.operation :as op]
            [automotive.store :as store]))

(def operator {:actor-id "op-1" :actor-role :homologation-engineer :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn- seed-with-one-dispatch []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "v" {:op :type-approval-rules/verify :subject "vehicle-1"})
    (approve! actor "v")
    (exec! actor "r" {:op :robotics/simulate-assembly-line :subject "vehicle-1"})
    (approve! actor "r")
    (exec! actor "d" {:op :actuation/dispatch-vehicle :subject "vehicle-1"})
    (approve! actor "d")
    db))

(deftest audit-package-shape
  (let [db (seed-with-one-dispatch)
        pkg (export/audit-package db)]
    (is (= "2910" (:isic pkg)))
    (is (= "cloud-itonami-isic-2910" (:business-id pkg)))
    (is (= :edn-maps (:format pkg)))
    (is (pos? (get-in pkg [:counts :ledger])))
    (is (= 1 (get-in pkg [:counts :dispatches])))
    (is (some #(= "vehicle-1" (:id %)) (:vehicles pkg)))
    (is (true? (:vehicle-dispatched?
                (first (filter #(= "vehicle-1" (:id %)) (:vehicles pkg))))))))

(deftest csv-bundle-has-headers-and-rows
  (let [db (seed-with-one-dispatch)
        bundle (export/package->csv-bundle db)]
    (is (every? bundle ["vehicles.csv" "ledger.csv" "dispatches.csv" "conformity-certificates.csv"]))
    (is (str/starts-with? (get bundle "vehicles.csv") "id,vehicle-name,"))
    (is (re-find #"vehicle-1" (get bundle "vehicles.csv")))
    (is (re-find #"JPN-VHL-000000" (get bundle "dispatches.csv")))
    (is (re-find #":actuation/dispatch-vehicle" (get bundle "ledger.csv")))))

(deftest empty-store-export-is-usable
  (let [db (store/seed-db)
        pkg (export/audit-package db)
        bundle (export/package->csv-bundle db)]
    (is (= 0 (get-in pkg [:counts :dispatches])))
    (is (= 5 (get-in pkg [:counts :vehicles])))
    (is (str/includes? (get bundle "ledger.csv") "seq,t,op"))))
