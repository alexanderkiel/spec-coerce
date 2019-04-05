(ns spec-coerce.alpha
  (:require
    [clojure.spec.alpha :as s]
    [spec-coerce.form :as form]
    [spec-coerce.spec.specs.alpha :as spec-specs]))

(defprotocol Coercer
  (-coerce [_ x]))

(defn coercer? [x]
  (satisfies? Coercer x))

(defmulti pred-coercer identity)

(def boolean-coercer
  (reify
    Coercer
    (-coerce [_ x]
      (cond
        (boolean? x) x
        (string? x)
        #?(:clj
           (try
             (Boolean/parseBoolean x)
             (catch Exception _
               ::s/invalid))
           :cljs
           (cond
             (= "true" x)
             true
             (= "false" x)
             false
             :else
             ::s/invalid))
        :else ::s/invalid))))

(def double-coercer
  (reify
    Coercer
    (-coerce [_ x]
      (cond
        (double? x) x
        (string? x)
        #?(:clj
           (try
             (Double/parseDouble x)
             (catch Exception _
               ::s/invalid))
           :cljs
           (let [value (js/parseFloat x)]
             (if (js/isNaN value)
               ::s/invalid
               value)))
        :else ::s/invalid))))

(def identity-coercer
  (reify
    Coercer
    (-coerce [_ x] x)))

(def int-coercer
  (reify
    Coercer
    (-coerce [_ x]
      (cond
        (int? x) x
        (string? x)
        #?(:clj
           (try
             (Long/parseLong x)
             (catch Exception _
               ::s/invalid))
           :cljs
           (let [value (js/parseInt x)]
             (if (js/isNaN value)
               ::s/invalid
               value)))
        :else ::s/invalid))))

(def map-coercer
  (reify
    Coercer
    (-coerce [_ x]
      (if (map? x)
        x
        ::s/invalid))))

(def nat-int-coercer
  (reify
    Coercer
    (-coerce [_ x]
      (let [x (-coerce int-coercer x)]
        (cond
          (s/invalid? x) x
          (not (neg? x)) x
          :else ::s/invalid)))))

(def pos-int-coercer
  (reify
    Coercer
    (-coerce [_ x]
      (let [x (-coerce int-coercer x)]
        (cond
          (s/invalid? x) x
          (pos? x) x
          :else ::s/invalid)))))

(def pos-coercer
  (reify
    Coercer
    (-coerce [_ x]
      (if (and (number? x) (pos? x))
        x
        ::s/invalid))))

(defmethod pred-coercer `boolean? [_] boolean-coercer)
(defmethod pred-coercer `double? [_] double-coercer)
(defmethod pred-coercer `int? [_] int-coercer)
(defmethod pred-coercer `map? [_] map-coercer)
(defmethod pred-coercer `nat-int? [_] nat-int-coercer)
(defmethod pred-coercer `pos-int? [_] pos-int-coercer)
(defmethod pred-coercer `pos? [_] pos-coercer)
(defmethod pred-coercer `string? [_] identity-coercer)

(defmethod pred-coercer :default [sym]
  (throw (ex-info (str "Unsupported predicate `" sym "`.") {:pred sym})))

(defn fn-coercer [args body]
  (reify
    Coercer
    (-coerce [_ x]
      #?(:clj
          (if (eval `((fn ~args ~@body) ~x))
            x
            ::s/invalid)
         :cljs
         ;;TODO - remove HACK of not evaluating the function
         x))))

(declare coercer*)

(defn and-coercer [specs opts]
  (let [coercers (map #(coercer* % opts) specs)]
    (reify
      Coercer
      (-coerce [_ x]
        (reduce
          (fn [x coercer]
            (let [x (-coerce coercer x)]
              (if (s/invalid? x)
                (reduced x)
                x)))
          x
          coercers)))))

(defn or-coercer [tagged-preds opts]
  (let [coercers (mapv (fn [{:keys [pred]}] (coercer* pred opts)) tagged-preds)]
    (reify
      Coercer
      (-coerce [_ x]
        (loop [[coercer & next-coercers] coercers]
          (if coercer
            (let [x (-coerce coercer x)]
              ;; skip over invalid branches
              (if (s/invalid? x)
                (recur next-coercers)
                x))
            ::s/invalid))))))

(declare coercer)

(s/def :spec-coerce.alpha.conformed/key
  (s/or :key (s/tuple #{:key} qualified-keyword?)
        :and (s/tuple #{:and} (s/keys :req-un [:spec-coerce.alpha.conformed/keys]))
        :or (s/tuple #{:or} (s/keys :req-un [:spec-coerce.alpha.conformed/keys]))))

(s/def :spec-coerce.alpha.conformed/keys
  (s/coll-of :spec-coerce.alpha.conformed/key))

(s/fdef gen-req-keys-expr
  :args (s/cat :m-sym simple-symbol? :key-mod fn?
               :keys (s/nilable :spec-coerce.alpha.conformed/keys)))

(defn- gen-req-keys-expr [m-sym key-mod keys]
  (map
    (fn [[kind {:keys [keys] :as key}]]
      (case kind
        :key
        `(contains? ~m-sym ~(key-mod key))
        :and
        `(and ~@(gen-req-keys-expr m-sym key-mod keys))
        :or
        `(or ~@(gen-req-keys-expr m-sym key-mod keys))))
    keys))

#?(:clj
   (defn keys-coercer
     [{:keys [req req-un opt-un] :as args} {:keys [f] :as opts}]
     (let [remove-ns #(-> % name keyword)
           req-un-keys
           (reduce
             (fn [ret [k v]]
               (case k
                 :key
                 (conj ret [k v])
                 :or
                 (into ret (:keys v))
                 :and
                 (into ret (:keys v))))
             []
             req-un)

           coercers
           (reduce
             (fn [ret [kind val]]
               (case kind
                 :key
                 (assoc ret (remove-ns val) (coercer val opts))))
             (reduce
               (fn [ret key]
                 (assoc ret (remove-ns key) (coercer key opts)))
               nil
               opt-un)
             req-un-keys)

           coercers
           (fn [k]
             (if-let [coercer (get coercers k)]
               coercer
               (when-let [spec (s/get-spec k)]
                 (coercer spec opts))))

           m-sym 'm
           pred-exprs [`(map? ~m-sym)]
           pred-exprs (into pred-exprs (gen-req-keys-expr m-sym identity req))
           pred-exprs (into pred-exprs (gen-req-keys-expr m-sym remove-ns req-un))
           keys-pred (eval `(fn* [~m-sym] (and ~@pred-exprs)))]
       (reify
         Coercer
         (-coerce [_ x]
           (if (keys-pred (f [:form {:s `s/keys :args args}] x))
             (loop [ret x, [[k v] & next-keys :as keys] x]
               (if keys
                 (if-let [coercer (coercers k)]
                   (let [cv (-coerce coercer v)]
                     (if (s/invalid? cv)
                       ::s/invalid
                       (recur (if (identical? cv v) ret (assoc ret k cv))
                              next-keys)))
                   (recur ret next-keys))
                 ret))
             ::s/invalid))))))

(defn merge-coercer [specs opts]
  (let [coercers (map #(coercer* % opts) specs)]
    (reify
      Coercer
      (-coerce [_ x]
        (let [ms (map #(-coerce % x) coercers)]
          (if (some s/invalid? ms)
            ::s/invalid
            (apply merge ms)))))))

#?(:clj
   (defn multi-spec-coercer [{mm-sym :mm} opts]
     (let [mm (var-get (resolve mm-sym))
           methods (methods mm)
           coercers
           (reduce-kv
             (fn [ret _ spec-fn]
               (let [spec (spec-fn nil)]
                 (assoc ret (s/form spec) (coercer spec opts))))
             nil
             methods)]
       (reify
         Coercer
         (-coerce [_ x]
           (let [spec (mm x)]
             (if-let [coercer (get coercers (s/form spec))]
               (-coerce coercer x)
               (-coerce (coercer spec) x))))))))

#?(:clj
   (defn coll-of-coercer
     [{:keys [spec]
       {:keys [kind min-count]} :opts}
      {:keys [coerce-coll-types] :or {coerce-coll-types false} :as opts}]
     (let [coercer (coercer* spec opts)
           coll-pred (some-> kind resolve var-get)]
       (reify
         Coercer
         (-coerce [_ x]
           (if (or (nil? coll-pred) coerce-coll-types (coll-pred x))
             (if (and min-count (< (count x) min-count))
               ::s/invalid
               (let [[init complete]
                     (case kind
                       `vector?
                       [[] identity]
                       (cond
                         (list? x)
                         [(list) reverse]
                         (vector? x)
                         [[] identity]))]
                 (complete
                   (reduce
                     (fn [ret v]
                       (let [v (-coerce coercer v)]
                         (if (s/invalid? v)
                           (reduced ::s/invalid)
                           (conj ret v))))
                     init
                     x))))
             ::s/invalid))))))

(defn set-coercer [set {:keys [f]}]
  (reify
    Coercer
    (-coerce [_ x]
      (let [x (f [:set set] x)]
        (if (contains? set x)
          x
          ::s/invalid)))))

(defn- coercer*
  "Returns a coercer for `spec`."
  {:arglists '([conformed-spec-form opts])}
  [[kind val] opts]
  ;; look at `::spec-specs/spec` for possible kinds and vals
  (case kind
    :set
    (set-coercer val opts)
    :pred
    (pred-coercer val)
    :kw
    ;; for keywords, resolve the spec by calling top-level `coercer`
    (coercer val opts)
    :form
    (let [{:keys [f s args body]} val]
      ;; forms can be a function (f) or a spec (s)
      (cond
        f
        (fn-coercer args body)
        s
        (condp = s
          `s/and
          (and-coercer args opts)
          `s/or
          (or-coercer args opts)
          `s/keys
          #?(:clj
             (keys-coercer args opts)
             :cljs
             (throw (js/Error. "Unsupported operation.")))
          `s/merge
          (merge-coercer args opts)
          `s/multi-spec
          #?(:clj
             (multi-spec-coercer args opts)
             :cljs
             (throw (js/Error. "Unsupported operation.")))
          `s/tuple
          (throw
            #?(:clj  (UnsupportedOperationException.)
               :cljs (js/Error. "Unsupported operation.")))
          `s/every
          (throw
            #?(:clj  (UnsupportedOperationException.)
               :cljs (js/Error. "Unsupported operation.")))
          `s/every-kv
          (throw
            #?(:clj  (UnsupportedOperationException.)
               :cljs (js/Error. "Unsupported operation.")))
          `s/coll-of
          #?(:clj
             (coll-of-coercer args opts)
             :cljs
             (throw (js/Error. "Unsupported operation.")))
          `s/map-of
          (throw
            #?(:clj  (UnsupportedOperationException.)
               :cljs (js/Error. "Unsupported operation.")))
          `s/cat
          (throw
            #?(:clj  (UnsupportedOperationException. "The cat-form is unsupported.")
               :cljs (js/Error. "The cat-form is unsupported.")))
          `s/conformer
          (throw
            #?(:clj  (UnsupportedOperationException. "The conformer-form is unsupported.")
               :cljs (js/Error. "The conformer-form is unsupported.")))
          `s/fspec
          (throw
            #?(:clj  (UnsupportedOperationException. "The fspec-form is unsupported.")
               :cljs (js/Error. "The fspec-form is unsupported.")))
          (throw (ex-info (str "Unexpected spec form `" (prn-str val) "`.")
                          {:spec-form val})))
        :else
        (throw (ex-info (str "Unexpected spec form `" (prn-str val) "`.")
                        {:spec-form val}))))
    (throw (ex-info (str "Unexpected spec kind `" kind "`.")
                    {:kind kind :val val}))))

(defn- form [spec]
  (let [form (s/form spec)]
    (if (= ::s/unknown form)
      (form/resolve-form spec)
      form)))

(defn coercer
  "Returns a coercer for `spec`.

  `spec` can be everything which is accepted by `s/valid?` or `s/conform`.

  opts can be:
   * :coerce-coll-kinds - whether collections should be converted into specified
                          types as per `:kind` in `s/every`, `s/every-kv`,
                          `s/coll-of` or `s/map-of`. Defaults to false"
  ([spec]
   (coercer spec nil))
  ([spec {:keys [f] :as opts}]
   (let [spec-form (form spec)
         conformed-spec-form (s/conform ::spec-specs/spec spec-form)]
     (if (s/invalid? conformed-spec-form)
       (throw
         (ex-info
           (str "Unable to build a coercer for spec `" spec-form "`.")
           (s/explain-data ::spec-specs/spec spec-form)))
       (coercer* conformed-spec-form
                 (cond-> opts (nil? f) (assoc :f (fn [_ x] x))))))))

(defn- to-coercer [x opts]
  (if (coercer? x)
    x
    (coercer x opts)))

(defn coerce
  ([coercer-or-spec x]
   (coerce coercer-or-spec x nil))
  ([coercer-or-spec x opts]
   (-coerce (to-coercer coercer-or-spec opts) x)))
