(ns fae.entities.skink
  (:require
   [fae.engine :as engine]
   [fae.behavior.id :as id]
   [fae.behavior.movement :as move]
   [fae.behavior.standard :as standard]
   [fae.grid :as grid]))

(defn init! [pos p _state] (move/set-initial-position p pos))

(defn update! [p _state] (move/smooth-move p))

(defn build-sprite []
  (engine/sprite "skink.png" [0 0]))

(defn instance [_state [x y]]
  {:id       (id/generate!)
   :type     :skink

   :transform {:position {:x 0 :y 0}
               :rotation 0}

   :stats {:hp 10
           :speed 1.5}
   :movement {:meter 0
              :move-fn move/handle-movement-random}

   :effects [:damage :bleed]
   :status []

   :grid {:x 0 :y 0}
   :graphics (build-sprite)
   :z-index  1

   :inbox []
   :events {:move-tick standard/handle-move-tick
            :licked-target standard/handle-licked-target
            :damaged standard/handle-damaged
            :bump standard/handle-bumped}
   :init   (partial init! [x y])
   :update update!})