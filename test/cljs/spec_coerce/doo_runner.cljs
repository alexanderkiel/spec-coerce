(ns spec-coerce.doo-runner
  (:require
    [doo.runner :refer-macros [doo-tests]]
    [spec-coerce.alpha-test]))

(doo-tests 'spec-coerce.alpha-test)
