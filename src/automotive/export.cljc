(ns automotive.export
  "Audit-package export for social / regulatory hand-off.

  Produces plain EDN maps and CSV strings over a `automotive.store/Store`
  snapshot -- the same append-only ledger, vehicle-dispatch drafts and
  Certificate-of-Conformity drafts the governor writes. Pure data
  transforms only: no I/O, no network, no signature. The
  manufacturer's own act is to sign and file the package; this
  namespace only materializes the package body.

  This is the honest delivery of the industry-stack `:export?` contract
  (robotics / audit-ledger capabilities) for ISIC 2910."
  (:require [clojure.string :as str]
            [automotive.store :as store]))

(defn- csv-escape [v]
  (let [s (str (if (nil? v) "" v))]
    (if (re-find #"[,\"\n\r]" s)
      (str "\"" (str/replace s "\"" "\"\"") "\"")
      s)))

(defn- csv-row [cols]
  (str/join "," (map csv-escape cols)))

(defn ledger-rows
  "Normalize ledger facts into flat row maps suitable for CSV."
  [st]
  (mapv (fn [i f]
          {:seq i
           :t (:t f)
           :op (str (:op f))
           :actor (:actor f)
           :subject (:subject f)
           :disposition (str (:disposition f))
           :basis (pr-str (:basis f))
           :summary (:summary f)})
        (range)
        (store/ledger st)))

(defn dispatch-rows [st]
  (mapv (fn [i r]
          {:seq i
           :record_id (get r "record_id")
           :kind (get r "kind")
           :vehicle_id (get r "vehicle_id")
           :jurisdiction (get r "jurisdiction")})
        (range)
        (store/dispatch-history st)))

(defn evidence-rows [st]
  (mapv (fn [i r]
          {:seq i
           :record_id (get r "record_id")
           :kind (get r "kind")
           :vehicle_id (get r "vehicle_id")
           :jurisdiction (get r "jurisdiction")})
        (range)
        (store/evidence-history st)))

(defn vehicles-snapshot [st]
  (mapv (fn [b]
          (select-keys b [:id :vehicle-name :jurisdiction :status
                          :emissions-deviation-actual
                          :emissions-deviation-min
                          :emissions-deviation-max
                          :eol-defect-unresolved?
                          :vehicle-dispatched?
                          :conformity-certified?
                          :dispatch-number
                          :evidence-number]))
        (store/all-vehicles st)))

(defn audit-package
  "Full audit package for a store snapshot -- the body a motor-vehicle
  manufacturer would hand to type-approval inspectors, market-
  regulator inspectors or internal compliance. `:format` is always
  `:edn-maps` for the nested package; use `package->csv-bundle` for
  CSV strings."
  [st]
  {:isic "2910"
   :business-id "cloud-itonami-isic-2910"
   :format :edn-maps
   :vehicles (vehicles-snapshot st)
   :ledger (vec (store/ledger st))
   :dispatches (vec (store/dispatch-history st))
   :conformity-certificates (vec (store/evidence-history st))
   :counts {:vehicles (count (store/all-vehicles st))
            :ledger (count (store/ledger st))
            :dispatches (count (store/dispatch-history st))
            :conformity-certificates (count (store/evidence-history st))}})

(defn rows->csv
  "Render a seq of flat maps as CSV using `header` column order."
  [header rows]
  (let [lines (into [(csv-row (map name header))]
                    (map (fn [r] (csv-row (map #(get r %) header))) rows))]
    (str (str/join "\n" lines) (when (seq lines) "\n"))))

(defn package->csv-bundle
  "CSV bundle for spreadsheet hand-off. Keys are filenames; values are
  CSV body strings."
  [st]
  {"vehicles.csv" (rows->csv [:id :vehicle-name :jurisdiction :status
                            :emissions-deviation-actual
                            :vehicle-dispatched? :conformity-certified?
                            :dispatch-number :evidence-number]
                           (vehicles-snapshot st))
   "ledger.csv" (rows->csv [:seq :t :op :actor :subject :disposition :basis :summary]
                           (ledger-rows st))
   "dispatches.csv" (rows->csv [:seq :record_id :kind :vehicle_id :jurisdiction]
                               (dispatch-rows st))
   "conformity-certificates.csv" (rows->csv [:seq :record_id :kind :vehicle_id :jurisdiction]
                                   (evidence-rows st))})

#?(:clj
(defn write-csv-bundle!
  "Write `package->csv-bundle` files under `dir` (created if missing).
  Returns the absolute path of `dir`. JVM-only I/O seam for social
  hand-off scripts; pure package construction stays in `package->csv-bundle`."
  [st dir]
  (let [d (java.io.File. (str dir))
        _ (.mkdirs d)
        bundle (package->csv-bundle st)]
    (doseq [[name body] bundle]
      (spit (java.io.File. d (str name)) body))
    (.getAbsolutePath d))))
