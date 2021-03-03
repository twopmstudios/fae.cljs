(ns fae.archive.core
  (:require
   [fae.core :as core]
   [fae.archive.attractor :as attractor]
   [fae.archive.deathzone :as deathzone]
   [fae.archive.force-field :as force-field]
   [fae.engine :as engine]
   [fae.ui :as ui]
   [cljsjs.pixi]
   [cljsjs.pixi-sound]))

(defn add-deathzones [{:keys [actors] :as state}]
  (if (> (mod (count (filter #(= (:type %) :attractor) actors)) 4) 2)
    (deathzone/random-deathzone state)
    state))

(defn add-attractor [state {:keys [x y] :as start-coords} end-coords]
  (let [attractor    (attractor/instance state x y (engine/distance start-coords end-coords))
        vector-field (:vector-field attractor)
        attractor    (dissoc attractor :vector-field)]
    (engine/add-actor-to-stage state attractor)
    (let [state (-> state
                    (update :actors conj attractor)
                    (update :vector-field #(merge-with (partial merge-with +) % vector-field)))]
      (force-field/draw-vector-field (some #(when (= (:id %) "force-field") %) (:background state)) state)
      (core/update-scene-objects state)
      (add-deathzones state))))

(defn stage-click-drag [action]
  (let [drag-state (volatile! {})]
    {:on-start (fn [state event]
                 (let [{:keys [x y] :as point} (engine/click-coords (:stage state) event)]
                   (when (engine/started? state)
                     (vswap! drag-state assoc
                             :start point
                             :line (let [line (js/PIXI.Graphics.)]
                                     (engine/draw-line line {:color 255 :width 10 :start point :end point})
                                     (.addChild (:stage state) line)
                                     line)))))
     :on-move  (fn [state event]
                 (when-let [line (:line @drag-state)]
                   (.clear line)
                   (engine/draw-line line {:color 0xFF0000
                                           :width 1
                                           :start (:start @drag-state)
                                           :end   (engine/click-coords (:stage state) event)})))
     :on-end   (fn [state event]
                 (when-let [start-coords (:start @drag-state)]
                   (.removeChild (:stage state) (:line @drag-state))
                   (vreset! drag-state {})
                   (action state start-coords (engine/click-coords (:stage state) event))))}))

(defn final-score [{:keys [width height score]}]
  (ui/text-box {:x 20 :y 20 :text (str "Final Score: " score " prizes collected!")}))

(defn restart-button [{:keys [width height score total-prizes]}]
  (ui/button {:label    (if (= score total-prizes) "You Win" "Try Again")
              :x        (- (/ width 2) 100)
              :y        (- (/ height 2) 25)
              :width    200
              :height   50
              :on-click core/restart!}))

(defn end-game-screen [state]
  (let [button (restart-button state)
        score  (final-score state)]
    (engine/add-actor-to-stage state button)
    (engine/add-actor-to-stage state score)
    (-> state
        (assoc :score 0 :game-state :game-over)
        (update :actors into [button score]))))

(defn deathzone-collisions [state player deathzones]
  (if (and deathzones (some (fn [zone] (engine/collides? player zone)) deathzones))
    (end-game-screen state)
    state))

(defn find-prize-collisions [{:keys [stage] :as state} player prizes]
  (reduce
   (fn [state {:keys [id] :as prize}]
     (if (engine/collides? player prize)
       (do
         (engine/remove-from-stage stage prize)
         (-> state
             (update :actors (fn [actors] (vec (remove #(= (:id %) id) actors))))
             (update :score inc)))
       state))
   state
   prizes))

(defn prize-collisions [state player prizes]
  (let [{:keys [total-prizes score] :as new-state} (find-prize-collisions state player prizes)]
    (if (= total-prizes score)
      (end-game-screen new-state)
      new-state)))

(defn collisions [{:keys [actors] :as state}]
  (let [{player     :player
         deathzones :deathzones
         prizes     :prizes} (core/group-actors-by-type actors)]
    (-> state
        (deathzone-collisions player deathzones)
        (prize-collisions player prizes))))