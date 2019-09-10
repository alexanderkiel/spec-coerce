(defproject org.clojars.akiel/spec-coerce "0.4-SNAPSHOT"
  :description "Coercion for Clojure Spec"
  :url "https://github.com/alexanderkiel/spec-coerce"

  :license {:name "Apache License"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :min-lein-version "2.0.0"
  :pedantic? :abort

  :dependencies [[org.clojure/spec.alpha "0.2.176"]]

  :plugins
  [[jonase/eastwood "0.2.6" :exclusions [org.clojure/clojure]]
   [lein-cljsbuild "1.1.7" :exclusions [org.clojure/clojure]]
   [lein-doo "0.1.10" :exclusions [org.clojure/clojure]]]

  :profiles
  {:dev
   {:dependencies
    [[cheshire "5.9.0"]
     [com.google.guava/guava "25.1-jre"]
     [org.clojure/clojure "1.10.1"]
     [org.clojure/clojurescript "1.10.520"]
     [org.clojure/test.check "0.10.0"]]}
   :clj-1.9
   {:dependencies
    [[org.clojure/clojure "1.9.0"]]}}

  :source-paths ["src"]
  :test-paths ["test/cljc"]

  :cljsbuild
  {:builds
   {:test
    {:source-paths ["src" "test/cljc" "test/cljs"]
     :compiler
     {:output-to "out/testable.js"
      :main spec-coerce.doo-runner
      :optimizations :simple
      :process-shim false}}}}

  :clean-targets ["target" "out"]

  :aliases
  {"cljs-nashorn-tests" ["doo" "nashorn" "test" "once"]
   "cljs-phantom-tests" ["doo" "phantom" "test" "once"]
   "all-tests" ["do" "test," "cljs-nashorn-tests," "cljs-phantom-tests"]
   "lint" ["eastwood" "{}"]})
