(ns rc2.web.settings)

(def config (atom {}))

(defn set-config! [cfg]
  "Replace the entire current configuration."
  (reset! config cfg))

(defn get-config [cfg]
  "Get the entire current configuration."
  @config)

(defn get-setting [key]
  "Read a configuration value from the current settings."
  (if (vector? key)
    (get-in @config key)
    (get @config key)))

(defn change-setting! [key new-val]
  "Change a configuration value."
  (if (vector? key)
    (swap! config assoc-in key new-val)
    (swap! config assoc key new-val)))
