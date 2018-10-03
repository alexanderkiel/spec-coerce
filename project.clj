(defproject org.clojars.akiel/spec-coerce "0.2-SNAPSHOT"
  :description "Coercion for Clojure Spec"
  :url "http://git.life.uni-leipzig.local/clojure/spec-coerce"

  :min-lein-version "2.0.0"
  :pedantic? :abort

  :dependencies [[org.clojure/spec.alpha "0.2.168"]]

  :plugins
  [[jonase/eastwood "0.2.6" :exclusions [org.clojure/clojure]]]

  :profiles
  {:dev
   {:dependencies
    [[cheshire "5.8.0"]
     [org.clojure/clojure "1.9.0"]
     [org.clojure/test.check "0.9.0"]]}
   :clj-1.10
   {:dependencies
    [[org.clojure/clojure "1.10.0-alpha5"]]}}

  :aliases
  {"lint" ["eastwood" "{}"]})
