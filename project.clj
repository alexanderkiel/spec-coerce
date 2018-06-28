(defproject spec-coerce "0.1-SNAPSHOT"
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
    [[org.clojure/clojure "1.9.0"]
     [org.clojure/test.check "0.9.0"]]}
   :clj-1.10
   {:dependencies
    [[org.clojure/clojure "1.10.0-alpha5"]]}}

  :deploy-repositories
  [["life-snapshots"
    {:url "https://portal.life.uni-leipzig.de/content/repositories/snapshots"
     :sign-releases false}]
   ["life-releases"
    {:url "https://portal.life.uni-leipzig.de/content/repositories/releases"
     :sign-releases false}]]

  :aliases
  {"lint" ["eastwood" "{}"]})
