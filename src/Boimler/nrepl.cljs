(ns Boimler.nrepl
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >! take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [clojure.string]
   [clojure.pprint :refer [pprint]]
   [cljs.reader :refer [read-string]]
   [goog.string.format]
   [goog.string :refer [format]]
   [clojure.spec.alpha :as s]

   [Boimler.protocols]
   [Boimler.spec]))

(def fs (js/require "fs"))
(def path (js/require "path"))
(def NreplClient (js/require "nrepl-client"))

(s/def ::nrepl-client (s/nilable some?))

(defn create-nrepl-connection
  [{:as opts
    :keys [::Boimler.spec/host
           ::Boimler.spec/port
           ::Boimler.spec/nrepl-type
           ::Boimler.spec/shadow-build-key
           ::Boimler.spec/runtime]}]
  {:pre [(s/assert ::Boimler.spec/create-nrepl-connection-opts opts)]
   :post [(s/assert ::Boimler.spec/nrepl-connection %)]}
  (let [stateA (atom nil)

        reponses->map
        (fn reponses->map
          [responses]
          (let [data (->>
                      (reduce
                       (fn [result response]
                         (cond
                           (:value response)
                           (assoc result :value (clojure.string/join "" [(:value result) (:value response)]))
                           (:out response)
                           (assoc result :out (clojure.string/join "" [(:out result) (:out response)]))
                           :else
                           (merge result response)))
                       {}
                       (js->clj responses :keywordize-keys true)))]
            data))

        connected?
        (fn connected?
          []
          (and (get @stateA ::nrepl-client)
               (= (.. (get @stateA ::nrepl-client) -readyState) "open")))

        eval-fn
        (fn eval-fn
          [{:as opts
            :keys [::Boimler.spec/session-id
                   ::Boimler.spec/ns-symbol
                   ::Boimler.spec/code-string]}]
          (let [nrepl-client (get @stateA ::nrepl-client)
                result| (chan 1)]
            (let [session-id (or session-id (get @stateA ::Boimler.spec/session-id))]
              (.eval nrepl-client code-string (str ns-symbol) session-id
                     (fn [err responses]
                       (if err
                         (put! result| err)
                         (put! result| (reponses->map responses))))))
            result|))

        clone-fn
        (fn clone-fn
          [{:as opts
            :keys [::Boimler.spec/session-id]}]
          (let [nrepl-client (get @stateA ::nrepl-client)
                result| (chan 1)]
            (let [session-id (or session-id (get @stateA ::Boimler.spec/session-id))]
              (.clone nrepl-client session-id
                      (fn [err responses]
                        (if err
                          (do (put! result| err))
                          (do
                            (put! result| (reponses->map responses)))))))
            result|))


        init-session-fn
        (fn init-session-fn
          [nrepl-connection]
          (go
            (let [{:keys [:new-session]} (<! (clone-fn {}))]
              (swap! stateA assoc ::Boimler.spec/session-id new-session)
              new-session)))

        init-fns {[:nrepl :clj]
                  (fn init-fn
                    [nrepl-connection]
                    (go
                      true))

                  [:shadow-cljs :cljs]
                  (fn init-fn
                    [nrepl-connection]
                    (go
                      (let [{:keys [::Boimler.spec/session-id
                                    ::Boimler.spec/shadow-build-key]} @nrepl-connection
                            code-string (format
                                         "(shadow.cljs.devtools.api/nrepl-select %s)"
                                         shadow-build-key)]
                        (<! (eval-fn
                             {::Boimler.spec/code-string code-string
                              ::Boimler.spec/session-id session-id}))
                        true)))

                  [:shadow-cljs :clj]
                  (fn init-fn
                    [nrepl-connection]
                    (go
                      true))}

        initialize
        (fn initialize
          [nrepl-connection]
          (if (and (connected?)
                   (get @stateA ::Boimler.spec/session-id))
            (go true)
            (go
              (let [{:keys [::Boimler.spec/nrepl-type
                            ::Boimler.spec/runtime]} @stateA
                    init-fn (get init-fns [nrepl-type runtime])]
                (<! (init-session-fn nrepl-connection))
                (<!  (init-fn nrepl-connection))
                true))))



        nrepl-connection
        ^{:type ::Boimler.spec/nrepl-connection}
        (reify
          Boimler.protocols/NreplConnection
          (eval*
            [this {:as opts
                   :keys [::Boimler.spec/session-id
                          ::Boimler.spec/code-string]}]
            {:pre [(s/assert ::Boimler.spec/eval-opts opts)]}
            (go
                ;; lazy connect on eval, making it http request/response like
                ;; if user wants to eval and program is down, the response will be 'not connected'
                ;; once the program is up, evals will work
                ;; so no need for reconnecting socket, the request-lazy connect-response is better
              (<! (Boimler.protocols/connect* this))
              (<! (initialize this))
              (<! (eval-fn opts))))

          (clone*
            [this {:as opts
                   :keys [::Boimler.spec/session-id]}]
            {:pre [(s/assert ::Boimler.spec/clone-opts opts)]}
            (go
              (<! (Boimler.protocols/connect* this))
              (<! (clone-fn opts))))

          (connect*
            [this]
            (if (connected?)
              (go true)
              (let [result| (chan 1)]
                (let [nrepl-client (.connect NreplClient
                                             (->
                                              (get @stateA ::opts)
                                              (select-keys [::Boimler.spec/host
                                                            ::Boimler.spec/port])
                                              (clj->js)))]
                  (doto nrepl-client
                    (.on "ready" (fn []
                                   (put! result| true)))
                    (.on "close" (fn [code reason]
                                   (Boimler.protocols/disconnect* this))))
                  (swap! stateA assoc ::nrepl-client nrepl-client))
                result|)))

          (connect*
            [this {:keys [::Boimler.spec/host
                          ::Boimler.spec/port] :as opts}]
            {:pre [(s/assert ::Boimler.spec/connect-opts opts)]}
            (swap! stateA merge opts {::opts opts})
            (Boimler.protocols/connect* this))

          (disconnect*
            [this]
            (when-let [nrepl-client  (get @stateA ::nrepl-client)]
              (.end nrepl-client)
              (swap! stateA dissoc ::nrepl-client ::Boimler.spec/session-id)))

          Boimler.protocols/Release
          (release*
            [this]
            (Boimler.protocols/disconnect* this))

          cljs.core/IDeref
          (-deref [_] @stateA))]
    (reset! stateA (merge
                    opts
                    {::opts opts
                     ::Boimler.spec/session-id nil
                     ::nrepl-client nil}))
    nrepl-connection))