(ns rc2.web.settings)

(def config (atom {}))

(defn set-config! [cfg]
  "Replace the entire current configuration."
  (reset! config cfg))

(defn load-config! [file]
  "Load configuration from a file."
  (set-config! (read-string (slurp file))))

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
