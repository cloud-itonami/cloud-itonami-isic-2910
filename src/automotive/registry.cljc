(ns automotive.registry
  "Pure-function vehicle-dispatch + Certificate-of-Conformity record
  construction -- an append-only motor-vehicle-manufacturer book-of-
  record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a vehicle-dispatch or
  Certificate-of-Conformity reference number -- every manufacturer/
  jurisdiction assigns its own reference format. This namespace does
  NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest, non-
  fabricating discipline `automotive.facts` uses.

  `vehicle-emissions-out-of-range?` is the FOURTH instance of this
  fleet's two-sided range check family (`testlab.registry/within-
  tolerance?` established the first, `conservation.registry/body-
  condition-out-of-range?` the second, `water.registry/contaminant-
  level-out-of-range?` the third, `steelworks.registry/heat-
  chemistry-out-of-range?`/`turbine.registry/unit-tolerance-out-of-
  range?` further siblings), applying the SAME lo/hi bounds-
  comparison shape to a vehicle's own measured emissions deviation
  against the vehicle's own recorded type-approval spec bounds.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real plant/assembly-line control system. It builds the
  RECORD a manufacturer would keep, not the act of dispatching the
  robot vehicle action or issuing the Certificate of Conformity
  itself (that is `automotive.operation`'s `:actuation/dispatch-
  vehicle`/`:actuation/issue-conformity-certificate`, always human-
  gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  manufacturer's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn vehicle-emissions-out-of-range?
  "Does `vehicle`'s own `:emissions-deviation-actual` fall outside
  its own `[:emissions-deviation-min :emissions-deviation-max]`
  recorded type-approval spec-bounds? A pure ground-truth check
  against the vehicle's own permanent fields -- no upstream
  comparison needed. One of this fleet's two-sided range check
  family (see ns docstring)."
  [{:keys [emissions-deviation-actual emissions-deviation-min emissions-deviation-max]}]
  (and (number? emissions-deviation-actual) (number? emissions-deviation-min) (number? emissions-deviation-max)
       (or (< emissions-deviation-actual emissions-deviation-min)
           (> emissions-deviation-actual emissions-deviation-max))))

(defn register-vehicle-dispatch
  "Validate + construct the VEHICLE-DISPATCH registration DRAFT --
  the manufacturer's own act of dispatching a real robot assembly/
  finishing action to complete a motor vehicle. Pure function --
  does not touch any real plant/assembly-line control system; it
  builds the RECORD a manufacturer would keep. `automotive.governor`
  independently re-verifies the vehicle's own emissions-deviation
  sufficiency against its own spec bounds, and a double-
  dispatch for the same vehicle, before this is ever allowed to
  commit."
  [vehicle-id jurisdiction sequence]
  (when-not (and vehicle-id (not= vehicle-id ""))
    (throw (ex-info "vehicle-dispatch: vehicle_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "vehicle-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "vehicle-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper-case jurisdiction) "-VHL-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "vehicle-dispatch-draft"
                "vehicle_id" vehicle-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "VehicleDispatch" dispatch-number dispatch-number)}))

(defn register-conformity-certificate
  "Validate + construct the CERTIFICATE-OF-CONFORMITY registration
  DRAFT -- the manufacturer's own act of issuing a real Certificate
  of Conformity (COC) certifying a vehicle as type-approval-worthy.
  Pure function -- does not touch any real plant/assembly-line
  control system; it builds the RECORD a manufacturer would keep.
  `automotive.governor` independently re-verifies the vehicle's own
  end-of-line defect resolution status, and a double-issuance for
  the same vehicle, before this is ever allowed to commit."
  [vehicle-id jurisdiction sequence]
  (when-not (and vehicle-id (not= vehicle-id ""))
    (throw (ex-info "conformity-certificate: vehicle_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "conformity-certificate: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "conformity-certificate: sequence must be >= 0" {})))
  (let [evidence-number (str (str/upper-case jurisdiction) "-COC-" (zero-pad sequence 6))
        record {"record_id" evidence-number
                "kind" "conformity-certificate-draft"
                "vehicle_id" vehicle-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "evidence_number" evidence-number
     "certificate" (unsigned-certificate "ConformityCertificate" evidence-number evidence-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
