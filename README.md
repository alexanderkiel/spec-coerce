[![Build Status](https://travis-ci.org/alexanderkiel/spec-coerce.svg?branch=master)](https://travis-ci.org/alexanderkiel/spec-coerce)

# Spec Coerce

Coercion for Clojure Spec

## Rationale

In computing [coercion][1] stands for the conversation of values into a different data type. It can be done implicit or explicit. In Clojure implicit conversations occurs at arithmetic expressions. For example if you add a double to an integer, the integer will be converted into a double before calculating the sum. The result will be a double.

```clojure
(+ 1 1.0)
;;=> 2.0
```

One the other hand, explicit conversations are done by casting. The next example shows

```clojure
(double 3)
;;=> 3.0
```



In Clojure we can use specs to describe constraints a value must conform to. For example we can write a spec which constraints values of the map key `::x` to conform to a double.

```clojure
(s/def ::x double?)
``` 

Now we can build a mechanism which coerces maps with integer values to doubles.

```clojure
(coerce (s/keys :req [::x]) {::x 1})
;;=> {::x 1.0}
```

That coercion is still implicit because it only uses the spec of the target and the type of the source to decide which conversation to undertake. Such implicit conversations have to be documented very clearly and this library will do that.

In most programming languages, an explicit conversation would be a cast. Spec coerce will also support casts by a function, one can supply.

## Install

```clojure
[org.clojars.akiel/spec-coerce "0.2"]
```

## Usage

```clojure
(require '[spec-coerce.alpha :refer [coerce]])

(coerce int? "1")
;;=> 1
```

## Implicit Conversations

| Spec | Source Type | Conversation
|------|-------------|-------------
| int? | long 

## Unsupported Spec Forms

### cat

The `cat` form is unsupported because it's most useful in parsing sequential syntaxes. However such sequences are uncommon in data structures which are used for communication or storage. Spec Coerce concentrates on coercing maps or collections of values following a common spec. Such collections can be spec'ed with the `every` or `coll-of` form. Please file an issue if you have a use case for `cat`.

## License

Copyright © 2018 Alexander Kiel

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: <https://en.wikipedia.org/wiki/Type_conversion>
