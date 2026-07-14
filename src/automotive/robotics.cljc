(ns automotive.robotics
  "Robot-executed CAE/assembly-line verification -- the concrete, actor-
  level realization of ADR-2607011000's robotics premise (every
  cloud-itonami vertical is designed on the premise that a robot
  performs the physical-domain work; an independent governor gates any
  action before it ever reaches hardware) for THIS actor's own
  `automotive.facts` requirement that a vehicle-dispatch proposal cite a
  CAE-simulation-report (衝突安全性CAEシミュレーション報告書) actually on
  file -- not merely a self-reported checklist string.

  A robot mission (`kotoba.robotics/mission`) walks the vehicle through
  three :sense/:actuate steps -- crash-simulation replay, chassis-weld
  torque-check, paint-thickness scan -- built with `kotoba.robotics/
  action` + `kotoba.robotics/telemetry-proof`, and reports an overall
  :passed? verdict. `simulation-out-of-tolerance?` independently re-
  derives that verdict from the vehicle's OWN recorded structural-
  deviation fields, never from the mission's self-reported result --
  the SAME 'ground truth, not self-report' discipline `automotive.
  registry/vehicle-emissions-out-of-range?` established for emissions
  (the fifth instance of this fleet's two-sided range-check family; see
  that ns's docstring for the first four). `automotive.governor`'s
  `robotics-simulation-violations` calls this ns's independent recheck,
  never the stored :passed? value, before any `:actuation/dispatch-
  vehicle` proposal may commit.

  Pure data + pure functions -- no real robot I/O, no network.
  `kotoba.robotics` is itself \"policy, not control\"; this namespace
  simulates what a real robot cell would report, deterministically,
  from the vehicle's own recorded fields, so tests and the demo run
  offline exactly like every other sibling namespace in this actor."
  (:require [kotoba.robotics :as robotics]))

(def mission-actions
  "The three-step CAE/assembly-line verification mission every vehicle
  walks through before `:actuation/dispatch-vehicle` is proposable. All
  :sense/:actuate at :none/:low safety -- verification/QA sensing on a
  stationary vehicle, not the moving-vehicle actuation that is
  `:actuation/dispatch-vehicle` itself (always :safety-critical -- see
  `automotive.governor`)."
  [{:step :crash-simulation-replay   :kind :sense   :safety :none}
   {:step :chassis-weld-torque-check :kind :actuate :safety :low}
   {:step :paint-thickness-scan      :kind :sense   :safety :none}])

(defn structural-tolerance-out-of-range?
  "Ground-truth check: does `vehicle`'s own recorded
  :structural-deviation-actual fall outside its own recorded
  [:structural-deviation-min :structural-deviation-max] bounds? Needs no
  mission run or proposal inspection -- its inputs are permanent fields
  already on the vehicle, the same shape `automotive.registry/vehicle-
  emissions-out-of-range?` uses for emissions."
  [{:keys [structural-deviation-actual structural-deviation-min structural-deviation-max]}]
  (and (number? structural-deviation-actual) (number? structural-deviation-min) (number? structural-deviation-max)
       (or (< structural-deviation-actual structural-deviation-min)
           (> structural-deviation-actual structural-deviation-max))))

(defn simulate-assembly-line
  "Run the robot CAE/assembly-line verification mission for `vehicle-id`
  (`vehicle` is the full vehicle record, incl. structural-deviation-*
  fields). Returns {:mission .. :actions [{:action .. :proof ..} ..]
  :passed? bool}. Deterministic: :passed? is derived from the vehicle's
  OWN recorded structural-deviation fields via `structural-tolerance-
  out-of-range?`, never invented or randomized -- `kotoba.robotics`
  mandates no network/IO, and a repeatable simulation is what makes the
  governor's independent recheck (`simulation-out-of-tolerance?`)
  meaningful."
  [vehicle-id vehicle]
  (let [out-of-range? (structural-tolerance-out-of-range? vehicle)
        reading (if out-of-range? :out-of-tolerance :nominal)
        mission (robotics/mission (str "mission-" vehicle-id "-cae-verify")
                                   :robot/assembly-line-cell-1
                                   :cae-assembly-verification
                                   :boundaries {:station "end-of-line-cae-cell"}
                                   :max-steps (count mission-actions))
        actions (mapv (fn [{:keys [step kind safety]}]
                        (let [a (robotics/action (str (:mission/id mission) "-" (name step))
                                                  (:mission/id mission) kind safety
                                                  :params {:step step :vehicle-id vehicle-id})]
                          {:action a
                           :proof (robotics/telemetry-proof (:mission/id mission) step reading
                                                             :provenance :simulated)}))
                      mission-actions)]
    {:mission mission
     :actions actions
     :passed? (not out-of-range?)}))

(defn simulation-out-of-tolerance?
  "Independent ground-truth recheck for the governor: does `vehicle`'s
  OWN current structural-deviation fields fall out of range right now?
  Ignores whatever :passed? verdict a prior mission run stored --
  identical in spirit to `automotive.registry/vehicle-emissions-out-of-
  range?`'s refusal to trust a proposal's self-report."
  [vehicle]
  (structural-tolerance-out-of-range? vehicle))
