(ns automotive.phase
  "Phase 0->3 staged rollout -- the motor-vehicle-manufacturer analog of
  `cloud-itonami-isic-6512`'s `casualty.phase`.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- vehicle intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-verify  -- adds type-approval requirements
                                 verification + end-of-line quality
                                 screening + robot CAE/assembly-line
                                 simulation writes, still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:vehicle/intake` (no capital risk
                                 yet) may auto-commit. `:actuation/
                                 dispatch-vehicle`/`:actuation/issue-
                                 conformity-certificate` NEVER auto-
                                 commit, at any phase.

  `:actuation/dispatch-vehicle`/`:actuation/issue-conformity-
  certificate` are deliberately ABSENT from every phase's `:auto` set,
  including phase 3 -- a permanent structural fact, not a rollout
  milestone still to come. Dispatching a real robot assembly/finishing
  action on a safety-critical vehicle and issuing a real Certificate
  of Conformity are the two real-world legal acts this actor
  performs; both are always a human homologation engineer's call.
  `automotive.governor`'s `:actuation/dispatch-vehicle`/`:actuation/
  issue-conformity-certificate` high-stakes gate enforces the same
  invariant independently -- two layers, not one, agree on this.
  `:end-of-line-quality/screen`/`:robotics/simulate-assembly-line` are
  likewise never auto-eligible, at any phase -- the same posture every
  sibling's screening/verification op has.
  Phase 3's `:auto` set here has only ONE member (`:vehicle/intake`)
  -- this domain has no separate no-capital-risk 'file' lifecycle
  distinct from the vehicle record itself.")

(def read-ops  #{})
(def write-ops #{:vehicle/intake :type-approval-rules/verify :end-of-line-quality/screen
                 :robotics/simulate-assembly-line
                 :actuation/dispatch-vehicle :actuation/issue-conformity-certificate})

;; NOTE the invariant: `:actuation/dispatch-vehicle`/`:actuation/
;; issue-conformity-certificate` are members of `write-ops` (governor-
;; gated like any write) but are NEVER members of any phase's `:auto`
;; set below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"        :writes #{}                                                          :auto #{}}
   1 {:label "assisted-intake"  :writes #{:vehicle/intake}                                          :auto #{}}
   2 {:label "assisted-verify"  :writes #{:vehicle/intake :type-approval-rules/verify :end-of-line-quality/screen
                                          :robotics/simulate-assembly-line}          :auto #{}}
   3 {:label "supervised-auto"  :writes write-ops
      :auto #{:vehicle/intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:actuation/dispatch-vehicle`/`:actuation/issue-conformity-
    certificate` are never auto-eligible at any phase, so they always
    escalate once the governor clears them (or hold if the governor
    doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map an Automotive Governor verdict to a base
  disposition before the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
