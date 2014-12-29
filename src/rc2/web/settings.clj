(ns rc2.web.settings
  (:require [schema.core :as s]
            [clojure.pprint :as pprint]))

(def DescriptorSettings
  "Schema for the descriptor settings in RC2 config files."
  [s/Any])

(def ConfigFile
  "Schema for the RC2 config file"
  {
   :calibration-file s/Str
   :descriptor s/Keyword
   :descriptor-settings DescriptorSettings
   :driver s/Keyword
   :max-accel s/Num
   :max-velocity s/Num
   (s/optional-key :http-port) s/Int
   (s/optional-key :serial-port) s/Str
   (s/optional-key :output) s/Str
   })

(def config (atom {}))

(defn set-config! [cfg]
  "Replace the entire current configuration."
  (reset! config (s/validate ConfigFile cfg)))

(defn get-config [cfg]
  "Get the entire current configuration."
  @config)

(defn get-setting [key]
  "Read a configuration value from the current settings."
  (if (vector? key)
    (get-in @config key)
    (get @config key)))

(defn change-setting! [key-or-keys new-val]
  "Change a configuration value."
  (if (vector? key-or-keys)
    (swap! config assoc-in key-or-keys new-val)
    (swap! config assoc key-or-keys new-val)))

(defn load-config! [file]
  "Load configuration from a file."
  (set-config! (read-string (slurp file))))

(defn sanitize-config [cfg]
  (let [schema-keys (map (fn [k] (if (map? k) (:k k) k)) (keys ConfigFile))]
    (reduce (fn [new k]
              (if (contains? cfg k) (assoc new k (get cfg k)) new)) {} schema-keys)))

(defn save-config! [config-file calibration-file]
  "Save configuration to a file."
  (println "Saving configuration to disk.")
  (let [cfg (sanitize-config @config)]
    (println "Saved:" cfg)
    (spit config-file (pprint/pprint cfg)))
  (spit calibration-file (pprint/pprint
                          (get-setting [:connection :calibration]))))
