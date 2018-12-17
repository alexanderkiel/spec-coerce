(ns spec-coerce.spec.specs.alpha
  "This namespace is a copy of the patch in
  https://dev.clojure.org/jira/browse/CLJ-2112.

  Please don't use it. It's private."
  (:require
    [clojure.spec.alpha :as s]
    [spec-coerce.spec.specs.macro #?(:clj :refer :cljs :refer-macros) [defspec]]))

;; open spec for spec forms
(defmulti spec-form first)

;; any spec form (spec-form + preds + sets)
(s/def ::spec
  (s/or :set set?
        :pred symbol?
        :kw qualified-keyword?
        :form (s/multi-spec spec-form (fn [val tag] val))))

(def ^:private fn-sym #?(:clj 'clojure.core/fn :cljs 'cljs.core/fn))

(defmethod spec-form fn-sym [_]
  (s/cat :f #{fn-sym}
         :args (s/and vector? #(= 1 (count %)))
         :body (s/* any?)))

(defspec and (s/* ::spec))
(defspec clojure.spec.alpha/or (s/* (s/cat :tag keyword? :pred ::spec)))

;;;; Colls

(s/def ::key (s/or :key qualified-keyword?
                   :and (s/cat :and #{'and} :keys (s/* ::key))
                   :or (s/cat :or #{'or} :keys (s/* ::key))))

(s/def ::req (s/coll-of ::key :kind vector? :into []))
(s/def ::req-un (s/coll-of ::key :kind vector? :into []))
(s/def ::opt (s/coll-of qualified-keyword? :kind vector? :into []))
(s/def ::opt-un (s/coll-of qualified-keyword? :kind vector? :into []))
(s/def ::gen ifn?)                                          ;; OPEN: (s/fspec :args (s/cat)) ? more?

(defspec clojure.spec.alpha/keys (s/keys* :opt-un [::req ::req-un ::opt ::opt-un ::gen]))
(defspec clojure.spec.alpha/merge (s/* ::spec))
(defspec clojure.spec.alpha/multi-spec (s/cat :mm qualified-symbol?
                                              :retag (s/alt :k keyword? :f ifn?)))
(defspec clojure.spec.alpha/tuple (s/* ::spec))

(s/def ::kind ifn?)                                         ;; OPEN: should this be ::spec ?
(s/def ::into (s/and coll? empty?))
(s/def ::count nat-int?)
(s/def ::min-count nat-int?)
(s/def ::max-count nat-int?)
(s/def ::distinct boolean?)
(s/def ::conform-keys boolean?)
(s/def ::gen-max nat-int?)

(s/def ::coll-opts
  (s/keys* :opt-un [::kind ::into ::count ::min-count ::max-count ::distinct ::gen-max ::gen]))

(defspec clojure.spec.alpha/every
         (s/cat
           :spec ::spec
           :opts ::coll-opts))

(defspec clojure.spec.alpha/every-kv
         (s/cat
           :kpred ::spec
           :vpred ::spec
           :opts ::coll-opts))

(defspec clojure.spec.alpha/coll-of
         (s/cat
           :spec ::spec
           :opts ::coll-opts))

(defspec clojure.spec.alpha/map-of
         (s/cat
           :kpred ::spec
           :vpred ::spec
           :opts ::coll-opts))

;;;; regex

(defspec clojure.spec.alpha/cat (s/* (s/cat :tag keyword? :spec ::spec)))
(defspec clojure.spec.alpha/alt (s/* (s/cat :tag keyword? :spec ::spec)))
(defspec clojure.spec.alpha/* ::spec)
(defspec clojure.spec.alpha/+ ::spec)
(defspec clojure.spec.alpha/? ::spec)
(defspec clojure.spec.alpha/& (s/cat :regex ::spec :preds (s/* ::spec)))
(defspec clojure.spec.alpha/keys* (s/keys* :opt-un [::req ::req-un ::opt ::opt-un ::gen]))

;;;; conformer

;; OPEN: could fspec :fn and :unfn
(defspec clojure.spec.alpha/conformer (s/cat :fn ifn? :unfn (s/? ifn?)))

;;;; fspec

(s/def ::args ::spec)
(s/def ::ret ::spec)
(s/def ::fn ::spec)

(defspec clojure.spec.alpha/fspec (s/keys* :opt-un [::args ::ret ::fn]))

;;;; spec form

; clojure.spec.alpha/form uses clojure.spec.alpha/specize so it accepts also
; keywords and such
;(s/fdef clojure.spec.alpha/form
;  :args (s/cat :spec s/spec?)
;  :ret ::spec)

;; could do something like (s/fdef clojure.spec.alpha/and :args ::and-args), but circularity problems

;;;; Tests

(comment
  (s/conform ::spec (s/form (s/spec int?)))
  (s/conform ::spec (s/form (s/spec #{42})))
  (s/conform ::spec (s/form (s/spec #(= % 42))))
  (s/conform ::spec (s/form (s/spec even?)))
  (s/conform ::spec (s/form (s/and int? even?)))
  (s/conform ::spec (s/form (s/or :a int? :b even?)))
  (s/conform ::key :a/b)
  (s/conform ::key '(and :a/b :c/d))
  (s/conform ::key '(or :a/b :c/d))
  (s/conform ::key '(and (or :a/b :c/d) :e/f))
  (s/conform ::spec (s/form (s/keys :req [::foo])))
  (s/conform ::spec (s/form (s/merge (s/keys) (s/keys :req [::foo]))))
  (defmulti ms :tag)
  (s/conform ::spec (s/form (s/multi-spec ms identity)))
  (s/conform ::spec (s/form (s/tuple int? string?)))
  (s/conform ::spec (s/form (s/every int?)))
  (s/conform ::spec (s/form (s/every-kv int? int?)))
  (s/conform ::spec (s/form (s/coll-of int?)))
  (s/conform ::spec (s/form (s/coll-of :a/b)))
  (s/conform ::spec (s/form (s/coll-of #{:a})))
  (s/conform ::spec (s/form (s/coll-of int? :min-count 1)))
  (s/conform ::spec (s/form (s/coll-of :a/b :min-count 1)))
  (s/explain ::spec (s/form (s/map-of int? int? :conform-keys true))) ;; fails - :kind is fn object
  (s/conform ::spec (s/form (s/cat :a int? :b string?)))
  (s/conform ::spec (s/form (s/alt :a int? :b string?)))
  (s/conform ::spec (s/form (s/* int?)))
  (s/conform ::spec (s/form (s/+ int?)))
  (s/conform ::spec (s/form (s/? int?)))
  (s/conform ::spec (s/form (s/& (s/* int?) #(= (count %) 3))))

  ;; derived
  (s/conform ::spec (s/form (s/keys* :req [::foo])))
  (s/conform ::spec (s/form (s/conformer str int?)))
  (s/conform ::spec (s/form (s/nilable int?)))
  (s/conform ::spec (s/form (s/int-in 0 10)))
  (s/conform ::spec (s/form (s/inst-in #inst "1977" #inst "1978")))
  (s/conform ::spec (s/form (s/double-in :min 0.0 :max 1.0 :infinite? false :NaN? false)))

  ;; generators
  (require '[clojure.spec.gen :as gen])
  (gen/sample (s/gen ::spec) 20)

  )
