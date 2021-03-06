(ns fae.events
  (:require [fae.print :as print]))

(def inbox (volatile! []))

(defn clear-inbox! []
  (vreset! inbox []))

(defn trigger-event!
  ([ev] (trigger-event! ev nil))
  ([ev data]
   (print/debug (str "trigger:" ev data))
   (vswap! inbox (fn [i]
                   (let [new (conj i [ev data])]
                    ;; (print/debug new)
                     new)))))

