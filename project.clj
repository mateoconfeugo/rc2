(defproject rc2 "0.1.0-SNAPSHOT"
  :description "Command and Control for robots."
  :url "https://nickpascucci.com"
  :license {:name "GNU General Public License V2"
            :url "https://www.gnu.org/licenses/gpl-2.0.html"}
  :dependencies [
                 [compojure "1.1.3"]
                 [gloss "0.2.2"]
                 [javax.servlet/servlet-api "2.5"]
                 [liberator "0.10.0"]
                 [org.clojure/algo.generic "0.1.1"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2234"]
                 [org.clojure/core.async "0.1.0-SNAPSHOT"]
                 [org.clojure/core.typed "0.2.13"]
                 [org.clojure/math.combinatorics "0.0.7"]
                 [org.clojure/math.numeric-tower "0.0.2"]
                 [org.clojure/tools.cli "0.2.4"]
                 [prismatic/dommy "0.1.2"]
                 [prismatic/schema "0.1.8"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-devel "1.1.0"]
                 [ring/ring-json "0.2.0"]
                 [http-kit "2.1.16"]
                 [serial-port "1.1.2"]
                 ]
  :repositories {"local" ~(str (.toURI (java.io.File. "maven-local")))
                 "sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :plugins [
            [lein-cljsbuild "1.0.3"]
            [lein-marginalia "0.7.1"]
            ]
  :profiles {:dev {:dependencies [[midje "1.5.1"]]
                   :plugins [[lein-midje "3.1.1"]]}}
  :cljsbuild {
              :builds {:release {:source-paths ["src-cljs"]
                                 :compiler {:output-to "resources/public/js/rc2.js"
                                            :optimizations :whitespace
                                            :pretty-print true}
                                 :jar true}
                       :test {:source-paths ["src-cljs" "test-cljs"]
                              :compiler {:output-to "target/unit-test.js"
                                         :optimizations :whitespace
                                         :pretty-print true}
                              :notify-command ["resources/specljs-runner.js"
                                               "target/unit-test.js"]}}}
  :core.typed {:check [rc2.lib.math rc2.lib.position]}
  :main rc2.web.core)
