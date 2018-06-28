(ns spec-coerce.alpha-test
  (:require
    [cheshire.core :as json]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :refer :all]
    [clojure.test.check.clojure-test :refer [defspec]]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]
    [spec-coerce.alpha :refer [coercer coerce]]))

(st/instrument)

(defspec boolean-pred 100
  (prop/for-all [x gen/boolean]
    (= x (coerce boolean? (str x)))))

(defspec int-pred 100
  (prop/for-all [x gen/int]
    (= x (coerce int? (str x)))))

(defspec nat-int-pred 100
  (prop/for-all [x gen/nat]
    (= x (coerce nat-int? (str x)))))

(s/def ::int
  int?)

(s/def ::double
  double?)

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
    (is (s/invalid? (coerce (s/and int? #(< 100 %)) "100"))))

  (testing "Empty and-forms coerce the original value."
    (is (= "foo" (coerce (s/and) "foo")))))

(deftest or-form
  (testing "All preds are tried in order,"
    (let [coercer (coercer (s/or :int int? :double double?))]
      (testing "so an integer compatible string is coerced by the first pred,"
        (is (= 1 (coerce coercer "1"))))
      (testing "but a string with a decimal value is coerced by the second pred,"
        (is (= 1.1 (coerce coercer "1.1"))))
      (testing "and a non-numeric string is just invalid."
        (is (s/invalid? (coerce coercer "a"))))))

  (testing "Empty or-forms coerce to invalid always."
    (is (s/invalid? (coerce (s/or) "foo")))))

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
    (testing "optional keys"
      (is (= {:int 1} (coerce (s/keys :opt-un [::int]) {:int "1"})))))

  (testing "Leaves unqualified keys alone"
    (is (= {:int "1"} (coerce (s/keys) {:int "1"})))))

(deftest merge-form
  (testing "The simplest map validating spec `map?` can be used"
    (is (= {} (coerce (s/merge map?) {}))))

  (testing "A map containing"
    (let [coercer (coercer (s/merge (s/keys :req [::int])
                                    (s/keys :req [::double])))]
      (testing "only the first key is invalid"
        (is (s/invalid? (coerce coercer {::int "1"}))))
      (testing "only the second key is invalid"
        (is (s/invalid? (coerce coercer {::double "1"}))))
      (testing "containing both keys is valid"
        (is (= {::int 1 ::double 1.0} (coerce coercer {::int "1" ::double "1"})))))))

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
    (let [coercer (coercer (s/coll-of int? :kind vector?) :coerce-coll-types true)]
      (is (vector? (coerce coercer (list 1)))))))

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
            (is (s/invalid? (coerce coercer parsed-json)))))))))
