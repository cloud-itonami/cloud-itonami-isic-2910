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
                              "Werkstoffzertifikat (material-certification-record)"]}})

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
