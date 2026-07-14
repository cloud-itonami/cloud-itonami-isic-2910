(ns automotive.automotiveadvisor
  "Automotive Advisor client -- the *contained intelligence node* for
  the motor-vehicle-manufacturing actor.

  It normalizes vehicle-intake, drafts a per-jurisdiction
  type-approval/homologation evidence checklist, screens vehicles
  for an unresolved end-of-line-detected defect, drafts the
  vehicle-dispatch action, and drafts the Certificate-of-Conformity-
  issuance action. CRITICAL: it is a smart-but-untrusted advisor. It
  returns a *proposal* (with a rationale + the fields it cited),
  never a committed record or a real robot dispatch/Certificate-of-
  Conformity issuance. Every output is censored downstream by
  `automotive.governor` before anything touches the SSoT, and
  `:actuation/dispatch-vehicle`/`:actuation/issue-conformity-
  certificate` proposals NEVER auto-commit at any phase -- see
  README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/dispatch-vehicle | :actuation/issue-conformity-certificate | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [automotive.facts :as facts]
            [automotive.registry :as registry]
            [automotive.robotics :as robotics]
            [automotive.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the vehicle, emissions-deviation figures or
  jurisdiction. High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "車両記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :vehicle/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-requirements
  "Per-jurisdiction type-approval/homologation evidence checklist
  draft. `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `automotive.facts` -- the Automotive Governor must
  reject this (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [a (store/vehicle db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction a))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "automotive.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :verification/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :verification/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-eol-defect
  "End-of-line-defect screening draft. `:eol-defect-unresolved?` on the
  vehicle record injects the failure mode: the Automotive
  Governor must HOLD, un-overridably, on any unresolved
  defect."
  [db {:keys [subject]}]
  (let [a (store/vehicle db subject)]
    (cond
      (nil? a)
      {:summary "対象車両記録が見つかりません" :rationale "no vehicle record"
       :cites [] :effect :eol-screen/set :value {:vehicle-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:eol-defect-unresolved? a))
      {:summary    (str (:vehicle-name a) ": 未解決の完成検査欠陥を検出")
       :rationale  "完成検査スクリーニングが未解決の欠陥を検出。人手確認とホールドが必須。"
       :cites      [:eol-check]
       :effect     :eol-screen/set
       :value      {:vehicle-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:vehicle-name a) ": 未解決の完成検査欠陥なし")
       :rationale  "完成検査欠陥スクリーニング完了。"
       :cites      [:eol-check]
       :effect     :eol-screen/set
       :value      {:vehicle-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- simulate-assembly-line
  "Runs the robot CAE/assembly-line verification mission
  (`automotive.robotics`) and drafts its result as a proposal. High
  confidence -- the mission itself is deterministic simulated telemetry
  derived from the vehicle's own recorded structural-deviation fields,
  not an LLM guess; the Automotive Governor still independently re-
  derives :passed? from those same fields before any `:actuation/
  dispatch-vehicle` proposal may commit -- see `automotive.governor`'s
  `robotics-simulation-violations`."
  [db {:keys [subject]}]
  (let [a (store/vehicle db subject)]
    (if (nil? a)
      {:summary "対象車両記録が見つかりません" :rationale "no vehicle record"
       :cites [] :effect :vehicle/upsert :value {:id subject :robotics-sim-verified? false}
       :stake nil :confidence 0.0}
      (let [{:keys [mission actions passed?]} (robotics/simulate-assembly-line subject a)]
        {:summary    (str subject ": CAE/組立ロボット検証ミッション " (if passed? "合格" "不合格"))
         :rationale  (str "mission=" (:mission/id mission) " actions=" (count actions)
                          " structural-deviation-actual=" (:structural-deviation-actual a))
         :cites      [(:mission/id mission)]
         :effect     :vehicle/upsert
         :value      {:id subject
                      :robotics-sim-verified? passed?
                      :robotics-sim-record {:mission-id (:mission/id mission)
                                            :actions (mapv #(dissoc % :action) actions)
                                            :passed? passed?}}
         :stake      nil
         :confidence 0.95}))))

(defn- propose-vehicle-dispatch
  "Draft the actual VEHICLE-DISPATCH action -- dispatching a real
  robot assembly/finishing action on a safety-critical vehicle.
  ALWAYS `:stake :actuation/dispatch-vehicle` -- this is a REAL-WORLD
  safety-critical act, never a draft the actor may auto-run. See
  README `Actuation`: no phase ever adds this op to a phase's `:auto`
  set (`automotive.phase`); the governor also always escalates on
  `:actuation/dispatch-vehicle`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [a (store/vehicle db subject)]
    {:summary    (str subject " 向け完成車実行提案"
                      (when a (str " (vehicle=" (:vehicle-name a) ")")))
     :rationale  (if a
                   (str "emissions-deviation-actual=" (:emissions-deviation-actual a)
                        " spec=[" (:emissions-deviation-min a) "," (:emissions-deviation-max a) "]")
                   "車両記録が見つかりません")
     :cites      (if a [subject] [])
     :effect     :vehicle/mark-dispatched
     :value      {:vehicle-id subject}
     :stake      :actuation/dispatch-vehicle
     :confidence (if (and a (not (registry/vehicle-emissions-out-of-range? a))) 0.9 0.3)}))

(defn- propose-conformity-certificate
  "Draft the actual CERTIFICATE-OF-CONFORMITY action -- issuing a
  real Certificate of Conformity certifying a vehicle as type-
  approval-worthy. ALWAYS `:stake :actuation/issue-conformity-
  certificate` -- this is a REAL-WORLD safety-critical act, never a
  draft the actor may auto-run. See README `Actuation`: no phase
  ever adds this op to a phase's `:auto` set (`automotive.phase`);
  the governor also always escalates on `:actuation/issue-
  conformity-certificate`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [a (store/vehicle db subject)]
    {:summary    (str subject " 向け適合証明書発行提案"
                      (when a (str " (vehicle=" (:vehicle-name a) ")")))
     :rationale  (if a
                   "jurisdiction-evidence-checklist referenced"
                   "車両記録が見つかりません")
     :cites      (if a [subject] [])
     :effect     :vehicle/mark-certified
     :value      {:vehicle-id subject}
     :stake      :actuation/issue-conformity-certificate
     :confidence (if a 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :vehicle/intake                              (normalize-intake db request)
    :type-approval-rules/verify                  (verify-requirements db request)
    :end-of-line-quality/screen                  (screen-eol-defect db request)
    :robotics/simulate-assembly-line             (simulate-assembly-line db request)
    :actuation/dispatch-vehicle                  (propose-vehicle-dispatch db request)
    :actuation/issue-conformity-certificate      (propose-conformity-certificate db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは自動車製造工場の完成車実行・適合証明書発行エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:vehicle/upsert|:verification/set|:eol-screen/set|"
       ":vehicle/mark-dispatched|:vehicle/mark-certified) "
       "(:robotics/simulate-assembly-line も :vehicle/upsert で "
       ":robotics-sim-verified? を提案する) "
       ":stake(:actuation/dispatch-vehicle か :actuation/issue-conformity-certificate か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :type-approval-rules/verify                  {:vehicle (store/vehicle st subject)}
    :end-of-line-quality/screen                   {:vehicle (store/vehicle st subject)}
    :robotics/simulate-assembly-line              {:vehicle (store/vehicle st subject)}
    :actuation/dispatch-vehicle                   {:vehicle (store/vehicle st subject)}
    :actuation/issue-conformity-certificate       {:vehicle (store/vehicle st subject)}
    {:vehicle (store/vehicle st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Automotive
  Governor escalates/holds -- an LLM hiccup can never auto-dispatch a
  vehicle action or auto-issue a Certificate of Conformity."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :automotiveadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
