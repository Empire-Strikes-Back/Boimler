(ns Boimler.main
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

   [clojure.walk]

   [Boimler.protocols]
   [Boimler.spec]
   [Boimler.core]
   [Boimler.editor]
   [Boimler.edit]

   [cljfmt.core]))

(def fs (js/require "fs"))
(def path (js/require "path"))
(def vscode (js/require "vscode"))

(do (clojure.spec.alpha/check-asserts true))

#_(foo)
#_(foo)

(defonce ^:private registryA (atom {}))

(defn activate
  [context]
  (let [editor (Boimler.editor/create-editor context {})]
    ; commands should be registered before activation function returns
    (let [cmds {::Boimler.spec/cmd-open {::Boimler.editor/cmd-id "Boimler.spec/cmd-open"}
                ::Boimler.spec/cmd-ping {::Boimler.editor/cmd-id "Boimler.spec/cmd-ping"}
                ::Boimler.spec/cmd-eval {::Boimler.editor/cmd-id "Boimler.spec/cmd-eval"}}]
      (doseq [k (keys cmds)] (s/assert ::Boimler.spec/mult-cmd k))
      (Boimler.editor/register-commands*
       editor
       {::Boimler.editor/cmds cmds
        ::Boimler.spec/cmd| (::Boimler.spec/cmd| @editor)}))
    (let [cmds {::Boimler.spec/cmd-format-current-form {::Boimler.editor/cmd-id "Boimler.spec/cmd-format-current-form"}
                ::Boimler.spec/cmd-select-current-form {::Boimler.editor/cmd-id "Boimler.spec/cmd-select-current-form"}}]
      (doseq [k (keys cmds)] (s/assert ::Boimler.spec/edit-cmd k))
      (Boimler.editor/register-commands*
       editor
       {::Boimler.editor/cmds cmds
        ::Boimler.spec/cmd| (::Boimler.spec/cmd| @editor)}))
    (go
      (let [config (<! (Boimler.protocols/read-Boimler-edn* editor))
            edit (Boimler.edit/create context {})
            Boimler-program (Boimler.core/create {::Boimler.spec/config config
                                              ::Boimler.spec/edit edit
                                              ::Boimler.spec/editor editor})]
        (swap! registryA merge {::editor editor
                                ::Boimler-program Boimler-program
                                ::edit edit})

        (tap (::Boimler.spec/cmd|mult @editor) (::Boimler.spec/cmd| @Boimler-program))
        (tap (::Boimler.spec/evt|mult @editor) (::Boimler.spec/ops| @Boimler-program))
        (tap (::Boimler.spec/cmd|mult @editor) (::Boimler.spec/cmd| @edit))
        (tap (::Boimler.spec/evt|mult @editor) (::Boimler.spec/ops| @edit))))))

(defn deactivate
  []
  (go
    (let [{:keys [::editor ::Boimler-program ::edit]} @registryA]
      (when (and editor Boimler-program edit)
        (Boimler.protocols/release* Boimler-program)
        (Boimler.protocols/release* edit)
        (Boimler.protocols/release* editor)
        (swap! registryA dissoc ::editor ::edit ::mult)))))

(def exports #js {:activate (fn [context]
                              (println ::activate)
                              (activate context))
                  :deactivate (fn []
                                (println ::deactivate)
                                (deactivate))})

(when (exists? js/module)
  (set! js/module.exports exports))

(defn ^:export main [] (println ::main))