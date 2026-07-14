(ns automotive.robotics
  "Robot-executed CAE/assembly-line verification -- the concrete, actor-
  level realization of ADR-2607011000's robotics premise (every
  cloud-itonami vertical is designed on the premise that a robot
  performs the physical-domain work; an independent governor gates any
  action before it ever reaches hardware) for THIS actor's own
  `automotive.facts` requirement that a vehicle-dispatch proposal cite a
  CAE-simulation-report (衝突安全性CAEシミュレーション報告書) actually on
  file -- not merely a self-reported checklist string.

  ADR-2607151600 (superseding ADR-2607083500's 'zero dependency /
  opaque payload' wall FOR THE AUTOMOTIVE VERTICAL ONLY) rewires this
  ns onto a REAL engineering simulation instead of a synthetic,
  deterministic field comparison: `kami-engine-vehicle-designer`'s
  `vdesign.simphysics` (a genuine time-stepped rigid-body simulation of
  the frontal-crash end-of-line dispatch event, built on the real
  `kotoba-lang/physics-2d` impulse solver), `vdesign.scene` (bridges the
  tessellated packaging-envelope geometry + the simulated trajectory
  into `kami.webgpu.mesh`'s real renderable-scene input shape) and
  `vdesign.motionplan` (extends the real BOM/CAM 4D assembly order into
  an actual Cartesian waypoint list per assembly station) are actually
  called here -- this is a REAL dependency (see deps.edn), not an
  opaque EDN payload.

  A robot mission (`kotoba.robotics/mission`) walks the vehicle through
  three :sense/:actuate steps -- crash-simulation replay, chassis-weld
  torque-check, paint-thickness scan -- built with `kotoba.robotics/
  action` + `kotoba.robotics/telemetry-proof`, and reports an overall
  :passed? verdict now derived from the REAL simulated crash trajectory
  (`:sim-decel-g`/`:sim-crush-distance-m`, see `crash-telemetry-for`),
  not a hand-set structural-deviation field. `crash-simulation-out-of-
  tolerance?` independently re-derives that verdict from the vehicle's
  OWN recorded real-telemetry fields, never from the mission's self-
  reported result -- the SAME 'ground truth, not self-report' discipline
  `automotive.registry/vehicle-emissions-out-of-range?` established for
  emissions (the fifth instance of this fleet's two-sided range-check
  family; see that ns's docstring for the first four). `automotive.
  governor`'s `robotics-simulation-violations` calls this ns's
  independent recheck, never the stored :passed? value, before any
  `:actuation/dispatch-vehicle` proposal may commit.

  Honest scope (ADR-2607151600): the crash physics is a 2D projection
  (`physics-2d` has no 3D solver), the geometry is a packaging-envelope
  BREP box (not a styled body), and the motion plan is a straight-line
  waypoint list (not an inverse-kinematics solver) -- see each vdesign
  namespace's own docstring for the full, disclosed derivation. What IS
  real: an actual `physics-2d/world-step` tick-by-tick rigid-body
  trajectory, actual tessellated mesh + face-normal data, and an actual
  Cartesian waypoint list per real assembly-order station.

  Pure data + pure functions -- no real robot I/O, no network.
  `vdesign.simphysics`/`vdesign.scene`/`vdesign.motionplan` and
  `kotoba.robotics` are themselves pure data transforms (`physics-2d`'s
  own `world-step` is a pure fixed-timestep integrator, no wall-clock/
  IO), so this stays exactly as offline/deterministic as every other
  sibling namespace in this actor -- tests and the demo run without a
  network."
  (:require [kotoba.robotics :as robotics]
            [vdesign.simphysics :as simphysics]
            [vdesign.scene :as scene]
            [vdesign.motionplan :as motionplan]
            [vdesign.simverify :as simverify]))

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

;; ───────────────────── real design-record construction ─────────────────────

(def default-class :sedan)
(def default-powertrain :bev)

(def frontal-area-m2
  "Plausible per-class frontal area (m^2) -- a disclosed engineering
  prior (`vdesign.simverify/geom` itself has no frontal-area field),
  needed alongside `simverify/geom`'s real `:wheelbase` so
  `vdesign.cad/envelope-dims-mm` (`:wheelbase-m`/`:frontal-area-m2` ->
  length/width/height mm) can build a packaging envelope. Empirically
  verified (see this PR's own probe) NOT to move `:sim-decel-g`/
  `:sim-crush-distance-m` at all -- both are derived purely from the
  class's own `:crush-len` via `vdesign.simphysics`'s `dt` derivation,
  independent of AABB size -- so this is informational geometry for the
  scene/motion-plan bridge only, never a tolerance input."
  {:city 1.9 :sedan 2.2 :suv 2.5 :truck 2.9})

(defn- geometry-for
  "Package-envelope geometry inputs for `class` -- wheelbase from
  `vdesign.simverify/geom`'s real per-class priors (the SAME table
  `vdesign.simphysics` itself reads for `:crush-len`), frontal area from
  this ns's own disclosed prior above. Mirrors `vdesign.design/
  geometry-of`'s shape exactly (`vdesign.cad/envelope-dims-mm`'s real
  input contract)."
  [class]
  (let [gm (get simverify/geom class (:sedan simverify/geom))]
    {:wheelbase-m (:wheelbase gm)
     :frontal-area-m2 (get frontal-area-m2 class (:sedan frontal-area-m2))}))

(defn- mass-budget-for
  "A plausible glider/energy-store/propulsion mass split -- a disclosed
  engineering prior (not measured), NEVER persisted on the vehicle,
  just enough for `vdesign.process/plan`'s BOM (which `vdesign.
  motionplan/motion-plan-for` walks) to run for THIS vehicle's own real
  assembly-order station sequence. Deterministic from `curb-mass-kg`
  alone -- same discipline `vdesign.motionplan`'s own `station-pitch-m`/
  `working-height-m` constants use (a plausible, honestly-simplified
  prior, not a measured fact)."
  [curb-mass-kg]
  {:glider-kg       (* curb-mass-kg 0.65)
   :energy-store-kg (* curb-mass-kg 0.20)
   :propulsion-kg   (* curb-mass-kg 0.15)})

(defn- energy-for
  "Plausible energy-system split for `vdesign.process/plan`'s BOM
  branch (BEV battery-module count vs. FCEV tank/stack/buffer split),
  from the mass-budget's own energy-store-kg above -- same disclosed-
  prior discipline, never persisted."
  [powertrain energy-store-kg]
  (case powertrain
    :bev  {:nominal-kWh (* energy-store-kg 0.175)}
    :fcev (let [tank-mass-kg (* energy-store-kg 0.5)]
            {:h2-kg (* tank-mass-kg 0.055)
             :tank-mass-kg tank-mass-kg
             :stack-mass-kg (* energy-store-kg 0.4)
             :buffer-mass-kg (* energy-store-kg 0.1)})))

(defn design-for
  "Builds a `vdesign`-shaped design record (see `kami-engine-vehicle-
  designer`'s `vdesign.design/spec`) from a governed vehicle's own
  permanent, recorded `:class`/`:powertrain`/`:curb-mass-kg` fields --
  exactly the fields `vdesign.simphysics`/`vdesign.scene`/`vdesign.
  motionplan` actually read (see each namespace's own docstring).
  `:geometry`/`:mass-budget`/`:energy` are derived, ephemeral inputs
  (disclosed priors, see the private fns above), never persisted
  themselves. Pure: deterministic given the same three vehicle fields,
  no IO."
  [{:keys [class powertrain curb-mass-kg]}]
  (let [class (or class default-class)
        powertrain (or powertrain default-powertrain)
        mass-budget (mass-budget-for curb-mass-kg)]
    {:class class
     :powertrain powertrain
     :curb-mass-kg curb-mass-kg
     :geometry (geometry-for class)
     :mass-budget mass-budget
     :energy (energy-for powertrain (:energy-store-kg mass-budget))}))

;; ───────────────────── real tolerance band (ADR-2607151600) ─────────────────────

(def decel-ceiling-g
  "Real, non-fabricated absolute ceiling on `:sim-decel-g` (g) -- 2.2x
  `vdesign.simverify`'s own established '20 g frontal crash pulse'
  reference (`simverify/a-crash`, converted from m/s^2 back to g via
  `simverify/g`). The 2x factor is `vdesign.simphysics`'s own
  DOCUMENTED, exactly-derived kinematic identity (a single-tick 'boxcar'
  full-stop is ALWAYS exactly double the closed-form ramp deceleration
  for the same impact speed/crush length -- see that namespace's
  docstring); the extra 0.1x is this ns's own disclosed margin (matching
  the SPIRIT of `simphysics/crosscheck-ratio-high`'s own 'slack for
  discretization/tick-count effects' allowance, applied here to a fixed
  reference pulse rather than each design's own self-referential closed
  form, since the self-referential ratio is a mathematical identity
  that can never itself flag a design as out-of-tolerance). Verified
  empirically (see this PR's own probe over all four `simverify/geom`
  classes at the real 56 km/h test speed): only the shortest-crush-zone
  `:city` class (0.50 m) exceeds this ceiling (~49.3 g); `:sedan`
  (~41.1 g), `:suv` (~38.0 g) and `:truck` (~27.4 g) all clear it with
  real margin. `:sim-decel-g` is PROVABLY independent of `:curb-mass-kg`
  (colliding with an immovable barrier -- see `vdesign.simphysics`'s
  docstring/tests) -- the real discriminator here is the vehicle's own
  `:class` (crush-zone length), not its mass."
  (* 2.2 (/ simverify/a-crash simverify/g)))

(defn- crush-len-for
  "This class's real crush-zone length (m) -- `vdesign.simverify/geom`,
  the SAME per-class geometry prior `vdesign.simphysics` itself reads
  to derive `dt`. The real ceiling for `:sim-crush-distance-m`: the
  actual simulated penetration must not exceed the crush zone this
  vehicle's own class was designed with (a cabin-intrusion margin), not
  an invented number."
  [class]
  (:crush-len (get simverify/geom class (:sedan simverify/geom))))

(defn crash-telemetry-for
  "Runs the REAL `vdesign.simphysics` time-stepped `physics-2d`
  simulation for `vehicle`'s own recorded `:class`/`:curb-mass-kg`
  (`design-for` above) and returns the actual simulated trajectory
  telemetry: `{:sim-decel-g n :sim-crush-distance-m n :ticks n :dt n
  :impact-mps n}`. `:sim-decel-g`/`:sim-crush-distance-m` are the SAME
  fields `vdesign.simphysics/simulate`'s own docstring documents as
  derived from the actual simulated velocity/position trajectory, not
  invented. Pure, deterministic -- no IO; the same `curb-mass-kg`/
  `class` always reproduce the same telemetry."
  [vehicle]
  (let [design (design-for vehicle)
        sim (simphysics/simulate design)]
    (select-keys sim [:sim-decel-g :sim-crush-distance-m :ticks :dt :impact-mps])))

(defn crash-simulation-out-of-tolerance?
  "Ground-truth check: does `vehicle`'s own recorded `:sim-decel-g` /
  `:sim-crush-distance-m` (the REAL `vdesign.simphysics` trajectory
  telemetry already on file for this vehicle -- see `crash-telemetry-
  for`) fall outside the real tolerance band derived from `vdesign.
  simverify`'s established reference values -- `decel-ceiling-g` (the
  20g crash pulse, with disclosed margin) and this vehicle's own
  `:class`'s real crush-len (`vdesign.simverify/geom`'s per-class
  geometry)? Needs no mission run -- its inputs are permanent fields
  already on the vehicle, the same shape `automotive.registry/vehicle-
  emissions-out-of-range?` uses for emissions."
  [{:keys [sim-decel-g sim-crush-distance-m class]}]
  (and (number? sim-decel-g) (number? sim-crush-distance-m)
       (or (> sim-decel-g decel-ceiling-g)
           (> sim-crush-distance-m (crush-len-for class)))))

(defn simulate-assembly-line
  "Run the robot CAE/assembly-line verification mission for `vehicle-id`
  (`vehicle` is the full vehicle record, incl. `:class`/`:powertrain`/
  `:curb-mass-kg`). Actually runs the REAL engine:

    1. `crash-telemetry-for` -- the actual `physics-2d`-stepped crash
       trajectory (`:sim-decel-g`/`:sim-crush-distance-m`).
    2. `vdesign.scene/scene-for` -- the real tessellated-envelope +
       per-tick-trajectory scene bridge (evidence the process was
       genuinely simulated, not just numerically checked -- returned
       here as a shape summary, not the full vertex/frame arrays, to
       keep the mission record small; the full scene data is
       reproducible any time from the same vehicle fields via
       `vdesign.scene/scene-for` + `design-for`).
    3. `vdesign.motionplan/motion-plan-for` -- the real Cartesian
       assembly-station waypoint list.

  Returns {:mission .. :actions [{:action .. :proof ..} ..] :passed?
  bool :sim-decel-g n :sim-crush-distance-m n :scene {..} :motion-plan
  {..}}. Deterministic: :passed? is derived from the vehicle's OWN
  recorded :class/:curb-mass-kg via the REAL simulated trajectory
  (`crash-simulation-out-of-tolerance?`), never invented or randomized
  -- `kotoba.robotics` mandates no network/IO, and a repeatable
  simulation is what makes the governor's independent recheck
  meaningful."
  [vehicle-id vehicle]
  (let [telemetry (crash-telemetry-for vehicle)
        design (design-for vehicle)
        sc (scene/scene-for design)
        mp (motionplan/motion-plan-for design)
        out-of-range? (crash-simulation-out-of-tolerance? (merge vehicle telemetry))
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
     :passed? (not out-of-range?)
     :sim-decel-g (:sim-decel-g telemetry)
     :sim-crush-distance-m (:sim-crush-distance-m telemetry)
     :scene {:vertex-count (:vertex-count sc) :index-count (:index-count sc)
             :frame-count (count (:frames sc)) :dims (:dims sc)}
     :motion-plan {:waypoint-count (count mp) :stations (mapv :station mp)}}))

(defn simulation-out-of-tolerance?
  "Independent ground-truth recheck for the governor: does `vehicle`'s
  OWN current, on-file real simphysics telemetry (`:sim-decel-g`/
  `:sim-crush-distance-m`/`:class`) fall out of tolerance right now?
  Ignores whatever :passed? verdict a prior mission run stored --
  identical in spirit to `automotive.registry/vehicle-emissions-out-of-
  range?`'s refusal to trust a proposal's self-report."
  [vehicle]
  (crash-simulation-out-of-tolerance? vehicle))
