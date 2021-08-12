(ns mult.spec
  (:require
   [clojure.spec.alpha :as s]
   [mult.protocols]))

(s/def ::channel #?(:clj #(instance? clojure.core.async.impl.channels.ManyToManyChannel %)
                    :cljs #(instance? cljs.core.async.impl.channels/ManyToManyChannel %)))
(s/def ::mult #?(:clj #(satisfies? clojure.core.async.Mult %)
                 :cljs #(satisfies? cljs.core.async/Mult %)))


(s/def ::evt| ::channel)
(s/def ::evt|mult ::mult)
(s/def ::cmd| ::channel)
(s/def ::cmd|mult ::mult)
(s/def ::ops| ::channel)


(s/def ::host string?)
(s/def ::port int?)
(s/def ::runtime #{:cljs :clj})
(s/def ::shadow-build-key keyword?)

(s/def ::session-id string?)
(s/def ::code-string string?)

(s/def ::eval-opts (s/keys :req [::code-string]
                           :opt [::session-id]))

(s/def ::clone-opts (s/keys :req []
                            :opt [::session-id]))


(s/def ::mult-program #(and
                          (satisfies? mult.protocols/MultProgram %)
                          #?(:clj (satisfies? clojure.lang.IDeref %))
                          #?(:cljs (satisfies? cljs.core/IDeref %))))

(s/def ::mult-cmd #{::cmd-open
                    ::cmd-ping
                    ::cmd-eval})

(s/def ::mult-ops #{::op-ping
                    ::op-eval
                    ::op-update-ui-state
                    ::op-did-change-active-text-editor
                    ::op-select-tab})

(s/def ::mult-op (s/merge
                  ::mult-cmd
                  ::mult-ops))

(s/def ::eval-result (s/nilable string?))
(s/def ::eval-err (s/nilable string?))
(s/def ::eval-out (s/nilable string?))

(s/def ::ns-symbol symbol?)

(s/def ::ui-state (s/keys :req []
                          :opt [::eval-value
                                ::eval-err
                                ::eval-out
                                ::ns-symbol
                                ::config
                                ]))


(s/def ::edit #(and
                (satisfies? mult.protocols/Edit %)
                (satisfies? mult.protocols/Release %)
                #?(:clj (satisfies? clojure.lang.IDeref %))
                #?(:cljs (satisfies? cljs.core/IDeref %))))

(s/def ::zloc any?)

(s/def ::clj-string string?)

(s/def ::position (s/tuple int? int?))

(s/def ::cursor-position ::position)


(s/def ::edit-evt #{})

(s/def ::edit-cmd #{::cmd-format-current-form
                    ::cmd-select-current-form})

(s/def ::edit-ops #{})

(s/def ::edit-op (s/merge
                  ::edit-evt
                  ::edit-cmd
                  ::edit-ops))


(s/def ::editor #(and
                  (satisfies? mult.protocols/Editor %)
                  (satisfies? mult.protocols/Release %)
                  #?(:clj (satisfies? clojure.lang.IDeref %))
                  #?(:cljs (satisfies? cljs.core/IDeref %))))

(s/def ::tab-id string?)
(s/def ::tab-title string?)

(s/def ::filepath string?)

(s/def ::editor-op #{::evt-did-change-active-text-editor})

(s/def ::on-tab-closed ifn?)
(s/def ::on-tab-message ifn?)

(s/def ::text-editor #(satisfies? mult.protocols/TextEditor %))

(s/def ::range (s/tuple int? int? int? int?))

(s/def ::tab #(and
               (satisfies? mult.protocols/Tab %)
               (satisfies? mult.protocols/Open %)
               (satisfies? mult.protocols/Close %)
               (satisfies? mult.protocols/Send %)
               (satisfies? mult.protocols/Active? %)
               (satisfies? mult.protocols/Release %)
               #?(:clj (satisfies? clojure.lang.IDeref %))
               #?(:cljs (satisfies? cljs.core/IDeref %))))

