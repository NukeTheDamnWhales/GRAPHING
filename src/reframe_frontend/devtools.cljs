(ns reframe-frontend.devtools
  (:require [devtools.core :as devtools]))

(devtools/install!)

(.log js/console (range 200))
