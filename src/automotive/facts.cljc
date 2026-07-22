(ns automotive.facts
  "Per-jurisdiction motor-vehicle type-approval/homologation catalog --
  the G2-style spec-basis table the Automotive Governor checks every
  `:type-approval-rules/verify` proposal against.

  Coverage is reported HONESTLY: a jurisdiction not in this table has
  NO spec-basis. Seed values cite official vehicle type-approval /
  homologation authorities; this is a starting catalog, not a survey
  of every market.")

(def catalog
  {"JPN" {:name "Japan"
          :owner-authority "国土交通省 (MLIT) 自動車局 / 独立行政法人自動車技術総合機構 (NALTEC)"
          :legal-basis "道路運送車両法 / 道路運送車両の保安基準 / 型式指定制度 (参考)"
          :national-spec "自動車の型式指定・完成検査・保安基準適合要件"
          :provenance "https://www.mlit.go.jp/"
          :required-evidence ["衝突安全性CAEシミュレーション報告書 (CAE-simulation-report)"
                              "排出ガス試験報告書 (emissions-test-report)"
                              "完成検査連鎖記録 (end-of-line-quality-chain-of-custody-record)"
                              "材料証明記録 (material-certification-record)"]}
   "USA" {:name "United States"
          :owner-authority "NHTSA (National Highway Traffic Safety Administration)"
          :legal-basis "49 CFR Part 567 (Certification) / 49 CFR Part 571 (FMVSS) (reference)"
          :national-spec "US self-certification of motor-vehicle conformity to FMVSS"
          :provenance "https://www.nhtsa.gov/"
          :required-evidence ["CAE-simulation-report"
                              "emissions-test-report"
                              "end-of-line-quality-chain-of-custody-record"
                              "Material-certification-record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "DVSA / UNECE WVTA (GB type-approval adoption)"
          :legal-basis "Road Vehicles (Approval) Regulations 2020 / UNECE 1958 Agreement WVTA (reference)"
          :national-spec "UK GB-type-approval conformity-of-production requirements"
          :provenance "https://www.gov.uk/government/organisations/driver-and-vehicle-standards-agency"
          :required-evidence ["CAE-simulation-report"
                              "emissions-test-report"
                              "end-of-line-quality-chain-of-custody-record"
                              "Material-certification-record"]}
   "DEU" {:name "Germany"
          :owner-authority "KBA (Kraftfahrt-Bundesamt) / EU Whole Vehicle Type Approval"
          :legal-basis "EU-Verordnung 2018/858 (Rahmenrichtlinie Typgenehmigung) / UNECE WVTA (Referenz)"
          :national-spec "EU-Typgenehmigung Übereinstimmung der Produktion Anforderungen"
          :provenance "https://www.kba.de/"
          :required-evidence ["CAE-Simulationsbericht (CAE-simulation-report)"
                              "Emissionsprüfbericht (emissions-test-report)"
                              "Endkontroll-Rückverfolgbarkeitsnachweis (end-of-line-quality-chain-of-custody-record)"
                              "Werkstoffzertifikat (material-certification-record)"]}
   ;; South Korea (KOR) -- Hyundai/Kia/GM Korea manufacturing base. Cite
   ;; ONLY what was independently re-fetched+read this session directly
   ;; from law.go.kr's Open API (site search UI is unreliable; the DRF
   ;; XML endpoint is not): 자동차관리법 (Motor Vehicle Management Act,
   ;; MST 286989, 소관부처=국토교통부/MOLIT, 시행일자 2026-06-16) 제29조
   ;; ("자동차는 ... 자동차안전기준 ... 에 적합하지 아니하면 운행하지
   ;; 못한다" -- vehicles may not operate unless conforming to MOLIT-set
   ;; safety standards) and 제30조 ("... 그 자동차의 형식이 자동차안전
   ;; 기준 ... 에 적합함을 스스로 인증 (자동차자기인증) ... 하여야 한다"
   ;; -- manufacturers self-certify conformity, a US-NHTSA-style
   ;; self-certification regime rather than JPN/DEU-style pre-market
   ;; type-approval). KNOWN GAP, disclosed honestly rather than
   ;; fabricated: the Act (제32조③) only says MOLIT designates a
   ;; "성능시험대행자" (performance-test proxy) by Ordinance/notice; the
   ;; specific designee (commonly reported elsewhere as KATRI / 한국교통
   ;; 안전공단) could NOT be confirmed this session -- katri.re.kr was
   ;; unreachable (connection refused) and no law.go.kr admrul/statute
   ;; text fetched this session names it -- so no testing-lab name is
   ;; asserted here, only the confirmed statutory ministry (MOLIT).
   "KOR" {:name "South Korea"
          :owner-authority "국토교통부 (MOLIT, Ministry of Land, Infrastructure and Transport) 자동차정책과"
          :legal-basis "자동차관리법 제29조(자동차의 구조 및 장치 등: 자동차안전기준) / 제30조(자동차의 자기인증 등) (참고)"
          :national-spec "자동차안전기준(국토교통부령) 적합에 대한 자동차제작자등의 자기인증 (manufacturer self-certification, not pre-market type-approval)"
          :provenance "https://www.law.go.kr/DRF/lawService.do?OC=test&target=law&MST=286989&type=XML"
          :required-evidence ["충돌안전성 CAE 시뮬레이션 보고서 (CAE-simulation-report)"
                              "배출가스 시험 보고서 (emissions-test-report)"
                              "완성검사 이력 관리 기록 (end-of-line-quality-chain-of-custody-record)"
                              "재료 인증 기록 (material-certification-record)"]}})

(defn spec-basis [iso3] (get catalog iso3))

(defn coverage
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-2910 R0: " (count catalog)
                 " jurisdictions seeded. Extend `automotive.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
