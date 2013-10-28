(defproject rc2 "0.1.0-SNAPSHOT"
  :description "Command and Control for robots."
  :url "https://nickpascucci.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[gloss "0.2.2"]
                 [org.clojure/algo.generic "0.1.1"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.0-SNAPSHOT"]
                 [org.clojure/core.typed "0.2.13"]
                 [org.clojure/math.numeric-tower "0.0.2"]
                 [org.clojure/tools.cli "0.2.4"]
                 [serial-port "1.1.2"]]
  :repositories {"local" ~(str (.toURI (java.io.File. "maven-local")))
                 "sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :plugins [[lein-marginalia "0.7.1"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]]
                   :plugins [[lein-midje "3.1.1"]]}}
  :core.typed {:check [rc2.lib.math rc2.lib.position]}
  :main rc2.core)
