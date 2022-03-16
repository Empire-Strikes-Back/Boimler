(ns Boimler.core
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
   [Boimler.edit-core]

   [Boimler.spec]
   [Boimler.protocols]
   [Boimler.nrepl]
   [Boimler.seed]))

(s/def ::create-opts (s/keys :req [::Boimler.spec/config
                                   ::Boimler.spec/editor]
                             :opt []))


(s/def ::send| ::Boimler.spec/channel)
(s/def ::recv| ::Boimler.spec/channel)
(s/def ::recv|mult ::Boimler.spec/mult)

(s/def ::tabs (s/coll-of ::Boimler.spec/tab :into #{}))

(s/def ::nrepl-connections (s/map-of ::Boimler.spec/nrepl-id ::Boimler.spec/nrepl-connection))

(defn create
  [{:keys [::Boimler.spec/config
           ::Boimler.spec/editor] :as opts}]
  {:pre [(s/assert ::create-opts opts)]
   :post [(s/assert ::Boimler.spec/Boimler-program %)]}
  (let [stateA (atom nil)
        tab-recv| (chan 10)
        tab-evt| (chan 10)
        ops| (chan 10)
        cmd| (chan 10)

        create-tab
        (fn create-tab
          []
          (let [tab-id (str (random-uuid))
                tab (Boimler.protocols/create-tab*
                     editor
                     {::Boimler.spec/tab-id tab-id
                      ::Boimler.spec/tab-title "p-p-purging purging - i love purging"
                      ::Boimler.spec/on-tab-closed (fn [tab]
                                                  (put! tab-evt| {:op ::Boimler.spec/on-tab-closed
                                                                  ::Boimler.spec/tab-id tab-id}))
                      ::Boimler.spec/on-tab-message (fn [tab msg]
                                                   (put! tab-recv| (read-string msg)))})]
            (Boimler.protocols/open* tab)
            (Boimler.seed/send-data tab {:op ::Boimler.spec/op-update-ui-state
                                      ::Boimler.spec/config config})
            (swap! stateA update ::tabs assoc tab-id tab)))

        release-tab
        (fn release-tab
          [tab-id tab]
          (Boimler.protocols/release* tab)
          (swap! stateA update ::tabs dissoc tab-id))

        nrepl-connections
        (persistent!
         (reduce (fn [result {:keys [::Boimler.spec/nrepl-id] :as nrepl-meta}]
                   (assoc! result nrepl-id
                           (Boimler.nrepl/create-nrepl-connection
                            nrepl-meta)))
                 (transient {})
                 (get config ::Boimler.spec/nrepl-metas)))

        Boimler-program
        ^{:type ::Boimler.spec/Boimler-program}
        (reify
          Boimler.protocols/BoimlerProgram
          Boimler.protocols/Release
          (release*
            [_]
            (doseq [[tab-id tab] (get @stateA ::tabs)]
              (Boimler.protocols/release* tab)
              (swap! stateA update ::tabs dissoc tab-id))
            (close! tab-evt|)
            (close! tab-recv|))
          #?(:clj clojure.lang.IDeref)
          #?(:clj (deref [_] @stateA))
          #?(:cljs cljs.core/IDeref)
          #?(:cljs (-deref [_] @stateA)))]

    (reset! stateA (merge
                    opts
                    {::opts opts
                     ::Boimler.spec/editor editor
                     ::nrepl-connections nrepl-connections
                     ::tabs {}
                     ::Boimler.spec/ops| ops|
                     ::Boimler.spec/cmd| cmd|}))
    (doseq [_ (range 0 (::Boimler.spec/open-n-tabs-on-start config))]
      (create-tab))

    (go
      (loop []
        (let [[value port] (alts! [tab-evt| cmd| ops|])]
          (when value
            (condp = port

              ops|
              (condp = (:op value)

                ::Boimler.spec/evt-did-change-active-text-editor
                (let [{:keys []} value
                      active-text-editor (Boimler.protocols/active-text-editor* editor)
                      filepath (Boimler.protocols/filepath* active-text-editor)]
                  (when filepath
                    (let [text (Boimler.protocols/text* active-text-editor [0 0 100 0])
                          ns-symbol (Boimler.edit-core/read-ns-symbol text)
                          nrepl-ids (Boimler.seed/filepath-to-nrepl-ids
                                     config
                                     filepath)
                          nrepl-id (first nrepl-ids)]
                      (when ns-symbol
                        (doseq [[tab-id tab] (get @stateA ::tabs)]
                          (when (Boimler.protocols/visible?* tab)
                            (Boimler.seed/send-data tab {:op ::Boimler.spec/op-update-ui-state
                                                      ::Boimler.spec/ns-symbol ns-symbol
                                                      ::Boimler.spec/nrepl-id  nrepl-id})))))))

                (do ::ignore-other-ops))

              tab-evt|
              (condp = (:op value)

                ::Boimler.spec/on-tab-closed
                (let [{:keys [::Boimler.spec/tab-id]} value]
                  (swap! stateA update ::tabs dissoc tab-id)
                  (println ::tab-disposed)))

              cmd|
              (condp = (:op value)

                ::Boimler.spec/cmd-open
                (let []
                  (create-tab))

                ::Boimler.spec/cmd-ping
                (let []
                  (println ::cmd-ping)
                  (Boimler.protocols/show-notification* editor (str ::cmd-ping))
                  #_(Boimler.protocols/send* tab (pr-str {:op ::Boimler.spec/op-ping})))

                ::Boimler.spec/cmd-eval
                (let [active-text-editor (Boimler.protocols/active-text-editor* editor)
                      filepath (Boimler.protocols/filepath* active-text-editor)]
                  (when filepath
                    (let [text (Boimler.protocols/text* active-text-editor [0 0 100 0])
                          ns-symbol (Boimler.edit-core/read-ns-symbol text)
                          code-string (Boimler.protocols/selection* active-text-editor)
                          nrepl-ids (Boimler.seed/filepath-to-nrepl-ids
                                     config
                                     filepath)
                          nrepl-id (first nrepl-ids)
                          nrepl-connection (get-in @stateA [::nrepl-connections nrepl-id])]
                      (when (and ns-symbol nrepl-connection)
                        (let [{:keys [::Boimler.spec/runtime]} @nrepl-connection
                              #_code-string-formatted
                              #_(cond
                                  (= runtime :clj)
                                  (format "(do (in-ns '%s) %s)" ns-symbol code-string)

                                  (= runtime :cljs)
                                  (format "(binding [*ns* (find-ns '%s)] %s)" ns-symbol code-string))
                              {:keys [value err out]} (<! (Boimler.protocols/eval*
                                                           nrepl-connection
                                                           {::Boimler.spec/code-string code-string
                                                            ::Boimler.spec/ns-symbol ns-symbol}))]
                          (if (zero? (count (get @stateA ::tabs)))
                            (do
                              ;; if no tabs, we print eval results directly to console
                              (println (format "%s => \n\n %s \n\n" ns-symbol code-string))
                              (when out
                                (println out))
                              (when err
                                (println err))
                              (println value))
                            (doseq [[tab-id tab] (get @stateA ::tabs)]
                              (when (Boimler.protocols/visible?* tab)
                                (Boimler.seed/send-data tab {:op ::Boimler.spec/op-update-ui-state
                                                          ::Boimler.spec/eval-value value
                                                          ::Boimler.spec/eval-out out
                                                          ::Boimler.spec/eval-err err})))))))))

                (do ::ignore-other-cmds)))
            (recur)))))
    Boimler-program))