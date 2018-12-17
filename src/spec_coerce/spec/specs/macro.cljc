(ns spec-coerce.spec.specs.macro
  (:require [clojure.spec.alpha :as s]))

;; helper to define a spec for a spec form /foo/ as:
;;  ::/foo/-args - the arg spec, suitable for use in an fdef
;;  ::/foo/-form - the form spec, as returned by form
;;  and register as a method in spec-form
(defmacro defspec [sym args-spec]
  (let [spec-ns (if (:ns &env) "cljs.spec.alpha" "clojure.spec.alpha")
        sym (symbol spec-ns (name sym))
        args-key (keyword "spec-coerce.spec.specs.alpha" (str (name sym) "-args"))
        form-key (keyword "spec-coerce.spec.specs.alpha" (str (name sym) "-form"))]
    `(do
       (s/def ~args-key ~args-spec)

       (s/def ~form-key
         (s/cat :s #{'~sym}
                :args ~args-spec))

       (defmethod spec-coerce.spec.specs.alpha/spec-form '~sym [_#] ~form-key))))
