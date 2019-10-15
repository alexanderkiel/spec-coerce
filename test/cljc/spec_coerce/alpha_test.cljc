(ns spec-coerce.alpha-test
  (:require
    #?(:clj [cheshire.core :as json])
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer [deftest is are testing]]
    #?(:clj [clojure.test.check.clojure-test :refer [defspec]])
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [spec-coerce.alpha :refer [coercer coerce]]))

(st/instrument)

#?(:clj
   (defspec boolean-pred 100
     (prop/for-all [x gen/boolean]
       (= x (coerce boolean? (str x))))))

#?(:clj
   (defspec int-pred 100
     (prop/for-all [x gen/int]
       (= x (coerce int? (str x))))))

#?(:clj
   (defspec nat-int-pred 100
     (prop/for-all [x gen/nat]
       (= x (coerce nat-int? (str x))))))

#?(:clj
   (defspec pos-int-pred 100
     (prop/for-all [x gen/s-pos-int]
       (= x (coerce pos-int? (str x))))))

(deftest predicate-test
  (testing "int?"
    (are [dest src] (= dest (coerce int? src))
      1 "1"
      1 1
      0 "0"
      0 0
      -1 "-1"
      -1 -1))
  (testing "nat-int?"
    (are [dest src] (= dest (coerce nat-int? src))
      1 "1"
      1 1
      0 "0"
      0 0
      ::s/invalid "-1"
      ::s/invalid -1))
  (testing "pos-int?"
    (are [dest src] (= dest (coerce pos-int? src))
      1 "1"
      1 1
      ::s/invalid "0"
      ::s/invalid 0))
  (testing "double?"
    (are [dest src] (= dest (coerce double? src))
      1.0 "1"
      1.0 1
      1.0 1.0
      ::s/invalid "a")))

(s/def ::int
  int?)

(s/def ::double
  double?)

(s/def ::enum
  #{:foo})

(deftest coercer-test
  (testing ""
    (is (coercer int?))))

(deftest set-test
  (testing "Set contains value"
    (is (= "foo" (coerce #{"foo"} "foo"))))

  (testing "Fails on missing value"
    (is (s/invalid? (coerce #{"foo"} "bar"))))

  (testing "Coerces strings to keywords"
    (let [f (fn [_ x] (keyword x))]
      (is (= :foo (coerce #{:foo} "foo" {:f f}))))))

(deftest and-form
  (testing "Value is first conformed by `int?` and than by `pos?` resulting in a long."
    (is (= 1 (coerce (s/and int? pos?) "1"))))

  (testing "An invalid integer leads to an overall invalid result."
    (is (s/invalid? (coerce (s/and int? pos?) "a"))))

  (testing "Zero is not a positive int."
    (is (s/invalid? (coerce (s/and int? pos?) "0"))))

  (testing "Using the `double?` predicate results in a double."
    (is (= 1.0 (coerce (s/and double? pos?) "1"))))

  (testing "A referenced pred can be used."
    (is (= 1 (coerce (s/and ::int pos?) "1"))))

  (testing "A function pred can be used."
    (is (= 101 (coerce (s/and int? #(< 100 %)) "101")))
    #?(:clj (is (s/invalid? (coerce (s/and int? #(< 100 %)) "100")))))

  (testing "Empty and-forms coerce the original value."
    (is (= "foo" (coerce (s/and) "foo")))))

(deftest or-form
  (testing "All preds are tried in order,"
    (let [coercer (coercer (s/or :int int? :double double?))]
      (testing "so an integer compatible string is coerced by the first pred."
        (is (= 1 (coerce coercer "1"))))
      #?(:clj
         (testing "but a string with a decimal value is coerced by the second pred."
           (is (= 1.1 (coerce coercer "1.1")))))
      (testing "and a non-numeric string is just invalid."
        (is (s/invalid? (coerce coercer "a"))))))

  (testing "Empty or-forms coerce to invalid always."
    (is (s/invalid? (coerce (s/or) "foo")))))

#?(:clj
   (deftest keys-form
     (testing "Non-maps are invalid."
       (is (s/invalid? (coerce (s/keys) "string")))
       (is (s/invalid? (coerce (s/keys) 1))))

     (testing "Empty map is returned."
       (is (= {} (coerce (s/keys) {}))))

     (testing "Qualified keywords are always coerced."
       (is (= {::int 1} (coerce (s/keys) {::int "1"}))))

     (testing "Returns invalid on missing required key"
       (testing "for qualified keys"
         (is (s/invalid? (coerce (s/keys :req [::int]) {}))))
       (testing "for unqualified keys"
         (is (s/invalid? (coerce (s/keys :req-un [::int]) {}))))
       (testing "for qualified keys inside `and`"
         (is (s/invalid? (coerce (s/keys :req [(and ::int ::double)]) {}))))
       (testing "for qualified keys inside `and` with only one required key"
         (is (s/invalid? (coerce (s/keys :req [(and ::int ::double)]) {::int 1}))))
       (testing "for qualified keys inside `or`"
         (is (s/invalid? (coerce (s/keys :req [(or ::int ::double)]) {})))))

     (testing "Coerces unqualified"
       (testing "required keys"
         (is (= {:int 1} (coerce (s/keys :req-un [::int]) {:int "1"}))))
       (testing "required keys"
         (is (= {:int 1 :double 2.0} (coerce (s/keys :req-un [::int ::double]) {:int "1" :double "2.0"}))))
       (testing "required keys inside `or`"
         (is (= {:int 1} (coerce (s/keys :req-un [(or ::int ::double)]) {:int "1"}))))
       (testing "required keys inside `and`"
         (is (= {:int 1 :double 2.0} (coerce (s/keys :req-un [(and ::int ::double)]) {:int "1" :double "2.0"}))))
       (testing "optional keys"
         (is (= {:int 1} (coerce (s/keys :opt-un [::int]) {:int "1"})))))

     (testing "Leaves unqualified keys alone"
       (is (= {:int "1"} (coerce (s/keys) {:int "1"}))))

     (testing "Coerces strings to keywords at a nested set spec."
       (let [f (fn [[kind] x] (if (= :set kind) (keyword x) x))]
         (is (= {::enum :foo} (coerce (s/keys :req [::enum]) {::enum "foo"} {:f f})))))))

(deftest merge-form
  (testing "The simplest map validating spec `map?` can be used"
    (is (= {} (coerce (s/merge map?) {}))))

  #?(:clj
     (testing "A map containing"
       (let [coercer (coercer (s/merge (s/keys :req [::int])
                                       (s/keys :req [::double])))]
         (testing "only the first key is invalid"
           (is (s/invalid? (coerce coercer {::int "1"}))))
         (testing "only the second key is invalid"
           (is (s/invalid? (coerce coercer {::double "1"}))))
         (testing "containing both keys is valid"
           (is (= {::int 1 ::double 1.0} (coerce coercer {::int "1" ::double "1"}))))))))

#?(:clj
   (deftest coll-of-form
     (testing "Retains the original collection type."
       (is (list? (coerce (s/coll-of int?) (list 1))))
       (is (vector? (coerce (s/coll-of int?) [1]))))

     (testing "Lists remain in order"
       (is (= (list 1 2) (coerce (s/coll-of int?) (list 1 2)))))

     (testing "Succeeds on matching collection type"
       (is (vector? (coerce (s/coll-of int? :kind vector?) [1]))))

     (testing "Fails on non-matching collection type"
       (is (s/invalid? (coerce (s/coll-of int? :kind vector?) (list 1)))))

     (testing "Coerces collection kinds"
       (let [coercer (coercer (s/coll-of int? :kind vector?) {:coerce-coll-types true})]
         (is (vector? (coerce coercer (list 1))))))))

(deftest cat-form
  (testing "The cat-form is unsupported."
    (is (thrown? #?(:clj UnsupportedOperationException :cljs js/Error) (coercer (s/cat))))))

(deftest conformer-form
  (testing "The conformer-form is unsupported."
    (is (thrown? #?(:clj UnsupportedOperationException :cljs js/Error) (coercer (s/conformer identity))))))

#?(:clj
   (deftest json-coercion
     (testing "Objects"
       (let [coercer (coercer (s/keys :req-un [::int]))]
         (testing "Property names are converted to keywords by Cheshire."
           (testing "with numeric int"
             (let [parsed-json (json/parse-string "{\"int\":1}" keyword)]
               (is (= {:int 1} (coerce coercer parsed-json)))))
           (testing "with string int"
             (let [parsed-json (json/parse-string "{\"int\":\"1\"}" keyword)]
               (is (= {:int 1} (coerce coercer parsed-json)))))
           (testing "fails with decimal number"
             (let [parsed-json (json/parse-string "{\"int\":\"1.1\"}" keyword)]
               (is (s/invalid? (coerce coercer parsed-json))))))))))
