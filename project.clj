(defproject rc2 "0.1.0-SNAPSHOT"
  :description "Command and Control for robots."
  :url "https://nickpascucci.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.0-SNAPSHOT"]]
  :repositories {"local" ~(str (.toURI (java.io.File. "maven-local")))
                 "sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :plugins [[lein-marginalia "0.7.1"]]
  :main rc2.core)
