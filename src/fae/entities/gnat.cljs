(ns fae.entities.gnat
  (:require
   [fae.engine :as engine]
   [fae.print :as print]
   [fae.events :as e]
   [fae.entities :as entities]
   [fae.behavior.licked :as lick]
   [fae.behavior.damaged :as damage]
   [fae.behavior.id :as id]
   [fae.behavior.movement :as move]
   [fae.grid :as grid]))

(defn init! [pos p _state] (move/set-initial-position p pos))

(defn update! [p _state] (move/smooth-move p))

(defn build-sprite []
  (engine/sprite "gnat.png" [0 0]))

(defn handle-movement [g state movement]
  (let [dir (rand-nth [:up :down :left :right])
        [x y] (move/dir->vec dir movement)]
    (if (> movement 0)
      (move/move-grid g state x y)
      g)))


(defn instance [_state [x y]]
  {:id       (id/generate!)
   :type     :gnat

   :transform {:position {:x 0 :y 0}
               :rotation 0}

   :stats {:hp 5
           :speed 0.7}
   :movement {:meter 0
              :move-fn handle-movement}

   :effects [:damage
             :tire]
   :status []

   :grid {:x 0 :y 0}
   :graphics (build-sprite)
   :z-index  1

   :inbox []
   :events {:move-tick (fn [g state] (move/perform g state (get-in g [:movement :move-fn])))
            :licked-target (fn [g state {target-id :id
                                         dmg :dmg}]
                             (if (= (:id g) target-id)
                               (lick/handle g dmg)
                               g))

            :damaged (fn [g state {id :id
                                   amount :amount}]
                       (if (= id (:id g)) (damage/handle g amount) g))

            :bump (fn [g state {bumpee :bumpee
                                effects :effects}]
                    (if (= bumpee (:id g))
                      (move/bumped g effects)
                      g))}
   :init   (partial init! [x y])
   :update update!})