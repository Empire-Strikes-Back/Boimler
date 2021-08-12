(ns mult.impl
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >! take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [clojure.string]
   [clojure.pprint :refer [pprint]]
   #?(:cljs [cljs.reader :refer [read-string]])
   #?(:cljs [goog.string.format])
   #?(:cljs [goog.string :refer [format]])
   [clojure.spec.alpha :as s]

   [cljctools.edit.core :as edit.core]

   [mult.spec]
   [mult.protocols]))

(defn send-data
  [tab data]
  {:pre [(s/assert ::mult.spec/mult-ops (:op data))]}
  (mult.protocols/send* tab (pr-str data)))