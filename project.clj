(defproject rc2 "0.1.0-SNAPSHOT"
  :description "Command and Control for robots."
  :url "https://nickpascucci.com"
  :license {:name "GNU General Public License V2"
            :url "https://www.gnu.org/licenses/gpl-2.0.html"}
  :dependencies [
                 [compojure "1.1.3"]
                 [cljs-ajax "0.2.6"]
                 [gloss "0.2.2"]
                 [http-kit "2.1.16"]
                 [javax.servlet/servlet-api "2.5"]
                 [liberator "0.10.0"]
                 [org.clojure/algo.generic "0.1.1"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2234"]
                 [org.clojure/core.async "0.1.0-SNAPSHOT"]
                 [org.clojure/math.combinatorics "0.0.7"]
                 [org.clojure/math.numeric-tower "0.0.2"]
                 [org.clojure/tools.cli "0.3.1"]
                 [prismatic/dommy "0.1.2"]
                 [prismatic/schema "0.2.4"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-devel "1.1.0"]
                 [ring/ring-json "0.2.0"]
                 [serial-port "1.1.2"]
                 [speclj "2.9.1"]
                 [specljs "2.9.1"]
                 ]
  :repositories {"local" ~(str (.toURI (java.io.File. "maven-local")))
                 "sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :plugins [
            [lein-cljsbuild "1.0.3"]
            [speclj "2.9.1"]
            ]
  :profiles {:dev {:plugins [[lein-marginalia "0.7.1"]]
                   :dependencies [[cider/cider-nrepl "0.7.0"]]
                   :repl-options {:nrepl-middleware
                                  [cider.nrepl.middleware.classpath/wrap-classpath
                                   cider.nrepl.middleware.complete/wrap-complete
                                   cider.nrepl.middleware.info/wrap-info
                                   cider.nrepl.middleware.inspect/wrap-inspect
                                   cider.nrepl.middleware.macroexpand/wrap-macroexpand
                                   ;cider.nrepl.middleware.ns/wrap-ns
                                   cider.nrepl.middleware.resource/wrap-resource
                                   cider.nrepl.middleware.stacktrace/wrap-stacktrace
                                   cider.nrepl.middleware.test/wrap-test
                                   cider.nrepl.middleware.trace/wrap-trace
                                   ;; cider.nrepl.middleware.undef/wrap-undef
                                   ]}}}
  :cljsbuild {
              :builds {:release {:source-paths ["src-cljs"]
                                 :compiler {:output-to "resources/public/js/rc2.js"
                                            :optimizations :whitespace
                                            :pretty-print true}
                                 :notify-command ["/usr/local/bin/terminal-notifier"
                                                  "-message" "CLJS Build Complete"
                                                  "-title" "Leiningen"]
                                 :jar true}
                       :test {:source-paths ["src-cljs" "test-cljs"]
                              :compiler {:output-to "target/unit-test.js"
                                         :optimizations :whitespace
                                         :pretty-print true}
                              :notify-command ["resources/specljs-runner.js"
                                               "target/unit-test.js"]}}}
  :main rc2.web.core)
