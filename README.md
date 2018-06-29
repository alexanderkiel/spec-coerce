# Spec Coerce

Coercion for Clojure Spec

## Usage

```clojure
(require '[spec-coerce.alpha :refer [coerce]])

(coerce int? "1")
;;=> 1
```

## Unsupported Spec Forms

### cat

The `cat` form is unsupported because it's most useful in parsing sequential syntaxes. However such sequences are uncommon in data structures which are used for communication or storage. Spec Coerce concentrates on coercing maps or collections of values following a common spec. Such collections can be spec'ed with the `every` or `coll-of` form. Please file an issue if you have a use case for `cat`.
