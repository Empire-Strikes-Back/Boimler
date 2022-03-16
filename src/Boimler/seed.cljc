(ns Boimler.seed
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

   [sci.core :as sci]
   [Boimler.edit]

   [Boimler.spec]
   [Boimler.protocols]))

(defn send-data
  [tab data]
  {:pre [(s/assert ::Boimler.spec/mult-ops (:op data))]}
  (Boimler.protocols/send* tab (pr-str data)))

(defn filepath-to-nrepl-ids
  [config filepath]
  (let [opts {:namespaces {'foo.bar {'x 1}}}
        sci-ctx (sci/init opts)]
    (into []
          (comp
           (filter (fn [{:keys [::Boimler.spec/nrepl-id
                                ::Boimler.spec/include-file?]}]
                     (let [include-file?-fn (sci/eval-string* sci-ctx (pr-str include-file?))]
                       (include-file?-fn filepath))))
           (map ::Boimler.spec/nrepl-id))
          (::Boimler.spec/nrepl-metas config))))