(ns automotive.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean vehicle through
  intake -> type-approval requirements verification -> end-of-line-
  defect screening -> vehicle-dispatch proposal (always escalates) ->
  human approval -> commit, then through Certificate-of-Conformity
  proposal (always escalates) -> human approval -> commit, then shows
  five HARD holds (a jurisdiction with no spec-basis, an out-of-spec
  emissions deviation, an unresolved end-of-line defect screened
  directly via `:end-of-line-quality/screen` [never via an actuation
  op against an unscreened vehicle -- see this actor's own governor ns
  docstring / the lesson `parksafety`'s ADR-2607071922 Decision 5,
  `eldercare`'s, `museum`'s, `conservation`'s, `salon`'s,
  `entertainment`'s, `casework`'s, `hospital`'s, `facility`'s,
  `school`'s, `association`'s, `leasing`'s, `behavioral`'s,
  `secondary`'s, `card`'s, `water`'s, `telecom`'s, `turbine`'s and
  `steelworks`'s ADR-0001s already recorded], and a double vehicle-
  dispatch/certificate-issuance of an already-processed vehicle)
  that never reach a human at all, and prints the audit ledger + the
  draft vehicle-dispatch and Certificate-of-Conformity records."
  (:require [langgraph.graph :as g]
            [automotive.export :as export]
            [automotive.store :as store]
            [automotive.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :homologation-engineer :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== vehicle/intake vehicle-1 (JPN, clean; emissions within spec, no EOL defect) ==")
    (println (exec! actor "t1" {:op :vehicle/intake :subject "vehicle-1"
                                :patch {:id "vehicle-1" :vehicle-name "Sakura Compact Sedan CS-04"}} operator))

    (println "== type-approval-rules/verify vehicle-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :type-approval-rules/verify :subject "vehicle-1"} operator))
    (println (approve! actor "t2"))

    (println "== end-of-line-quality/screen vehicle-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :end-of-line-quality/screen :subject "vehicle-1"} operator))
    (println (approve! actor "t3"))

    (println "== robotics/simulate-assembly-line vehicle-1 (robot CAE/assembly mission; escalates -- human approves) ==")
    (println (exec! actor "t3b" {:op :robotics/simulate-assembly-line :subject "vehicle-1"} operator))
    (println (approve! actor "t3b"))

    (println "== actuation/dispatch-vehicle vehicle-1 (always escalates -- actuation/dispatch-vehicle) ==")
    (let [r (exec! actor "t4" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator)]
      (println r)
      (println "-- human homologation engineer approves --")
      (println (approve! actor "t4")))

    (println "== actuation/issue-conformity-certificate vehicle-1 (always escalates -- actuation/issue-conformity-certificate) ==")
    (let [r (exec! actor "t5" {:op :actuation/issue-conformity-certificate :subject "vehicle-1"} operator)]
      (println r)
      (println "-- human homologation engineer approves --")
      (println (approve! actor "t5")))

    (println "== type-approval-rules/verify vehicle-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :type-approval-rules/verify :subject "vehicle-2" :no-spec? true} operator))

    (println "== type-approval-rules/verify vehicle-3 (escalates -- human approves; sets up the out-of-spec test) ==")
    (println (exec! actor "t7" {:op :type-approval-rules/verify :subject "vehicle-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/dispatch-vehicle vehicle-3 before robotics simulation -> HARD hold (robotics-simulation-missing) ==")
    (println (exec! actor "t7b" {:op :actuation/dispatch-vehicle :subject "vehicle-3"} operator))

    (println "== robotics/simulate-assembly-line vehicle-3 (clean structural deviation; escalates -- human approves) ==")
    (println (exec! actor "t7c" {:op :robotics/simulate-assembly-line :subject "vehicle-3"} operator))
    (println (approve! actor "t7c"))

    (println "== actuation/dispatch-vehicle vehicle-3 (0.35 outside [-0.10,0.10] emissions tolerance -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/dispatch-vehicle :subject "vehicle-3"} operator))

    (println "== actuation/dispatch-vehicle vehicle-5 (robotics-sim on file, but structural deviation 0.30 outside [-0.05,0.05] tolerance on independent recheck -> HARD hold) ==")
    (println (exec! actor "t8b" {:op :type-approval-rules/verify :subject "vehicle-5"} operator))
    (println (approve! actor "t8b"))
    (println (exec! actor "t8c" {:op :actuation/dispatch-vehicle :subject "vehicle-5"} operator))

    (println "== end-of-line-quality/screen vehicle-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :end-of-line-quality/screen :subject "vehicle-4"} operator))

    (println "== actuation/dispatch-vehicle vehicle-1 AGAIN (double-dispatch -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/dispatch-vehicle :subject "vehicle-1"} operator))

    (println "== actuation/issue-conformity-certificate vehicle-1 AGAIN (double-issuance -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/issue-conformity-certificate :subject "vehicle-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft vehicle-dispatch records ==")
    (doseq [r (store/dispatch-history db)] (println r))

    (println "== draft Certificate-of-Conformity records ==")
    (doseq [r (store/evidence-history db)] (println r))

    (println "== social hand-off: audit package counts ==")
    (println (:counts (export/audit-package db)))
    (println "== social hand-off: CSV bundle keys ==")
    (println (keys (export/package->csv-bundle db)))))
