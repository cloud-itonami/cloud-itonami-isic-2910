(ns automotive.governor
  "Automotive Governor -- the independent compliance layer
  that earns the Automotive Advisor the right to commit. The LLM has no
  notion of type-approval/homologation law, whether a vehicle's own
  measured emissions deviation actually stays within its own
  recorded spec bounds, whether an end-of-line-detected defect against
  the vehicle has actually stayed unresolved, or when an act stops
  being a draft and becomes a real-world robot vehicle dispatch or
  Certificate-of-Conformity issuance, so this MUST be a separate
  system able to *reject* a proposal and fall back to HOLD -- the
  motor-vehicle-manufacturer analog of `cloud-itonami-isic-6512`'s
  CasualtyGovernor.

  Seven checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated type-approval spec-basis, incomplete evidence, a robot
  CAE/assembly-line simulation that never ran or that independently
  re-checks out-of-tolerance, an out-of-spec vehicle, an unresolved
  end-of-line defect, or a double dispatch/certificate-issuance). The
  confidence/actuation gate is SOFT: it asks a human to look (low
  confidence / actuation), and the human may approve -- but see
  `automotive.phase`: for `:stake :actuation/dispatch-vehicle`/
  `:actuation/issue-conformity-certificate` (a real safety-critical act)
  NO phase ever allows auto-commit either. Two independent layers agree
  that actuation is always a human call.

    1. Spec-basis                  -- did the requirements proposal cite
                                       an OFFICIAL source (`automotive.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/dispatch-
                                       vehicle`/`:actuation/issue-
                                       conformity-certificate`, has the
                                       vehicle actually been verified
                                       with a full CAE-simulation-
                                       report/emissions-test-report/
                                       end-of-line-quality-chain-of-
                                       custody-record/material-
                                       certification-record evidence
                                       checklist on file?
    3. Robot simulation missing or
       independently out-of-
       tolerance                    -- for `:actuation/dispatch-
                                       vehicle`, has the robot CAE/
                                       assembly-line verification
                                       mission (`automotive.robotics`)
                                       actually run and been recorded
                                       on the vehicle
                                       (`:robotics-sim-verified?`)? AND
                                       INDEPENDENTLY recompute whether
                                       the vehicle's own recorded REAL
                                       `physics-2d`-simulated crash
                                       telemetry (`:sim-decel-g`/
                                       `:sim-crush-distance-m`, from
                                       `kami-engine-vehicle-designer`'s
                                       `vdesign.simphysics`,
                                       ADR-2607151600) falls outside a
                                       real tolerance band derived from
                                       `vdesign.simverify`'s established
                                       reference values
                                       (`automotive.robotics/
                                       simulation-out-of-tolerance?`),
                                       ignoring whatever :passed?
                                       verdict the mission run itself
                                       stored -- the same 'ground
                                       truth, not self-report'
                                       discipline check 4 below uses
                                       for emissions.
    4. Vehicle emissions out of
       range                         -- for `:actuation/dispatch-
                                       vehicle`, INDEPENDENTLY
                                       recompute whether the
                                       vehicle's own measured
                                       emissions deviation falls
                                       outside its own recorded spec
                                       bounds (`automotive.registry/
                                       vehicle-emissions-out-of-
                                       range?`) -- needs no proposal
                                       inspection or stored-verdict
                                       lookup at all. One of this
                                       fleet's two-sided range check
                                       family (`testlab.governor/
                                       within-tolerance-violations`/
                                       `conservation.governor/body-
                                       condition-out-of-range-
                                       violations`/`water.governor/
                                       contaminant-level-out-of-range-
                                       violations`/`steelworks.
                                       governor`/`turbine.governor`
                                       established the priors;
                                       `automotive.robotics/
                                       structural-tolerance-out-of-
                                       range?` above is the fifth).
    5. End-of-line defect unresolved -- reported by THIS proposal itself
                                       (an `:end-of-line-quality/
                                       screen` that just found an
                                       unresolved defect), or
                                       already on file for the
                                       vehicle (`:end-of-line-quality/
                                       screen`/`:actuation/issue-
                                       conformity-certificate`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/...
                                       (prior siblings)... established --
                                       exercised in tests/demo via
                                       `:end-of-line-quality/screen`
                                       DIRECTLY, not via an actuation
                                       op against an unscreened
                                       vehicle -- see this ns's own
                                       test suite.
    6. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       dispatch-vehicle`/`:actuation/
                                       issue-conformity-certificate`
                                       (REAL safety-critical acts) ->
                                       escalate.

  Two more guards, double-dispatch/double-certificate-issuance
  prevention, are enforced but NOT listed as numbered HARD checks
  above because they need no upstream comparison at all --
  `already-dispatched-violations`/`already-certified-violations`
  refuse to dispatch a vehicle action/issue a Certificate of
  Conformity for the SAME vehicle twice, off dedicated
  `:vehicle-dispatched?`/`:conformity-certified?` facts (never a
  `:status` value) -- the SAME 'check a dedicated boolean, not
  status' discipline every prior sibling governor's guards
  establish, informed by `cloud-itonami-isic-6492`'s status-
  lifecycle bug (ADR-2607071320)."
  (:require [automotive.facts :as facts]
            [automotive.registry :as registry]
            [automotive.robotics :as robotics]
            [automotive.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Dispatching a real robot vehicle action on a safety-critical
  vehicle and issuing a real Certificate of Conformity are the two
  real-world actuation events this actor performs -- a two-member
  set, matching every prior dual-actuation sibling's shape."
  #{:actuation/dispatch-vehicle :actuation/issue-conformity-certificate})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:type-approval-rules/verify` (or actuation) proposal with no
  spec-basis citation is a HARD violation -- never invent a
  jurisdiction's type-approval requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:type-approval-rules/verify :actuation/dispatch-vehicle :actuation/issue-conformity-certificate} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は型式指定要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/dispatch-vehicle`/`:actuation/issue-conformity-
  certificate`, the jurisdiction's required CAE-simulation-report/
  emissions-test-report/end-of-line-quality-chain-of-custody-record/
  material-certification-record evidence must actually be satisfied
  -- do not trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/dispatch-vehicle :actuation/issue-conformity-certificate} op)
    (let [a (store/vehicle st subject)
          verification (store/requirements-verification-of st subject)]
      (when-not (and verification
                     (facts/required-evidence-satisfied?
                      (:jurisdiction a) (:checklist verification)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(CAEシミュレーション報告書/排出ガス試験報告書/完成検査連鎖記録/材料証明記録等)が充足していない状態での提案"}]))))

(defn- robotics-simulation-violations
  "For `:actuation/dispatch-vehicle`: HARD hold if the robot CAE/
  assembly-line verification mission (`automotive.robotics`) never ran
  and was recorded on the vehicle (`:robotics-sim-verified?`), OR if it
  did but an INDEPENDENT recompute of the vehicle's own REAL
  `vdesign.simphysics`-simulated crash telemetry (`:sim-decel-g`/
  `:sim-crush-distance-m`, ADR-2607151600 -- `automotive.robotics/
  simulation-out-of-tolerance?`) says out-of-tolerance right now --
  never trusts the mission's own stored :passed? verdict alone, the
  same discipline `vehicle-emissions-out-of-range-violations` below
  uses for emissions."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-vehicle)
    (let [a (store/vehicle st subject)]
      (cond
        (not (:robotics-sim-verified? a))
        [{:rule :robotics-simulation-missing
          :detail (str subject " のCAE/組立ロボット検証ミッションが未実行・未合格")}]

        (robotics/simulation-out-of-tolerance? a)
        [{:rule :robotics-simulation-out-of-tolerance
          :detail (str subject " の実測クラッシュ減速度(" (:sim-decel-g a)
                       "g)/クラッシュ侵入量(" (:sim-crush-distance-m a)
                       "m)が独立再検証で許容範囲(上限" robotics/decel-ceiling-g "g)を逸脱")}]))))

(defn- vehicle-emissions-out-of-range-violations
  "For `:actuation/dispatch-vehicle`, INDEPENDENTLY recompute whether
  the vehicle's own emissions deviation falls outside its own
  recorded spec bounds via `automotive.registry/vehicle-emissions-
  out-of-range?` -- needs no proposal inspection or stored-verdict
  lookup at all, since its inputs are permanent ground-truth fields
  already on the vehicle."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-vehicle)
    (let [a (store/vehicle st subject)]
      (when (registry/vehicle-emissions-out-of-range? a)
        [{:rule :vehicle-emissions-out-of-range
          :detail (str subject " の実測排出ガス偏差(" (:emissions-deviation-actual a)
                      ")が仕様範囲[" (:emissions-deviation-min a) "," (:emissions-deviation-max a) "]を逸脱")}]))))

(defn- end-of-line-defect-unresolved-violations
  "An unresolved end-of-line-detected defect -- reported by THIS
  proposal (e.g. an `:end-of-line-quality/screen` that itself just
  found one), or already on file in the store for the vehicle
  (`:end-of-line-quality/screen`/`:actuation/issue-conformity-
  certificate`) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY (not scoped to a specific op) so the screening op
  itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        vehicle-id (when (contains? #{:end-of-line-quality/screen :actuation/issue-conformity-certificate} op) subject)
        hit-on-file? (and vehicle-id (= :unresolved (:verdict (store/eol-screen-of st vehicle-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :end-of-line-defect-unresolved
        :detail "未解決の完成検査欠陥がある状態での適合証明書発行提案は進められない"}])))

(defn- already-dispatched-violations
  "For `:actuation/dispatch-vehicle`, refuses to dispatch a vehicle
  action for the SAME vehicle twice, off a dedicated `:vehicle-
  dispatched?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-vehicle)
    (when (store/vehicle-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既に完成車実行済み")}])))

(defn- already-certified-violations
  "For `:actuation/issue-conformity-certificate`, refuses to issue a
  Certificate of Conformity for the SAME vehicle twice, off a
  dedicated `:conformity-certified?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/issue-conformity-certificate)
    (when (store/vehicle-already-certified? st subject)
      [{:rule :already-certified
        :detail (str subject " は既に適合証明書発行済み")}])))

(defn check
  "Censors an Automotive Advisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (robotics-simulation-violations request st)
                           (vehicle-emissions-out-of-range-violations request st)
                           (end-of-line-defect-unresolved-violations request proposal st)
                           (already-dispatched-violations request st)
                           (already-certified-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
