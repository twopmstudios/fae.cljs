(ns fae.engine
  (:require
   [fae.print :as print]
   [reagent.core :as r]))

(defn load-texture [resource-name]
  (.from (.-Texture js/PIXI) (str "assets/" resource-name)))

(defn set-anchor [obj x y]
  (.set (.-anchor obj) x y)
  obj)

(defn sigmoid [v]
  (/ v (+ 1 (js/Math.abs v))))

(defn distance [{x1 :x y1 :y} {x2 :x y2 :y}]
  (if js/Math.hypot
    (js/Math.hypot (js/Math.abs (- x1 x2)) (js/Math.abs (- y1 y2)))
    (js/Math.sqrt
     (+ (js/Math.pow (js/Math.abs (- x1 x2)) 2)
        (js/Math.pow (js/Math.abs (- y1 y2)) 2)))))

(defn collides? [p1 p2]
  (< (distance p1 p2) (+ (:radius p1) (:radius p2))))

(defn random-xyr [width height {:keys [padding min-r max-r existing retries max-retries]
                                :or {padding 0
                                     min-r 1
                                     max-retries 50
                                     retries 0
                                     max-r 1
                                     existing []} :as opts}]
  (let [r (+ min-r (if (and max-r (< min-r max-r))
                     (rand-int (inc (- max-r min-r)))
                     0))
        inset (+ r padding)
        net-width (- width (* 2 inset))
        net-height (- height (* 2 inset))
        x (+ inset (rand-int net-width))
        y (+ inset (rand-int net-height))]
    (when (or (> 0 net-width)
              (> 0 net-height))
      (throw (js/Error. (str "Your min-r or max-r is too large, please try a smaller radius"))))
    (if (some
         (fn [xyr2]
           (collides? {:x x
                       :y y
                       :radius r}
                      xyr2)) existing)
      (let [retries (inc retries)]
        (if (or (neg? max-retries) (< retries max-retries))
          (recur width height (assoc opts :retries retries))
          (throw (js/Error. (str "Failed to generate a non-colliding disk after " max-retries " retries")))))
      {:x x
       :y y
       :radius r})))

(defn random-xyrs [n width height opts]
  (reduce
   (fn [acc v]
     (conj acc (random-xyr width height (update opts :existing (fnil into []) acc))))
   []
   (range n)))

(defn sprite
  ([resource-name] (sprite resource-name [0.5 0.5]))
  ([resource-name [anchor-x anchor-y]]
   (let [sprite (new js/PIXI.Sprite (load-texture resource-name))]
     (set-anchor sprite anchor-x anchor-y))))

(defn set-graphics-position [{:keys [graphics transform velocity width height] :as entity}]
  (let [x (get-in transform [:position :x])
        y (get-in transform [:position :y])
        rotation (get-in transform [:rotation])]
    (when x (set! (.-x (.-position graphics)) x))
    (when y (set! (.-y (.-position graphics)) y))
    (when velocity (set! (.-rotation graphics) (js/Math.atan2 (:y velocity) (:x velocity))))
    (when rotation (set! (.-rotation graphics) rotation))
    (when width (set! (.-width graphics) width))
    (when height (set! (.-height graphics) height))
    entity))

(defn sort-by-z-index [stage]
  (-> (.-children stage)
      (.sort (fn [a b] (< (.-zOrder b) (.-zOrder a))))))

(defn add-actor-to-stage [{:keys [stage] :as state}
                          {:keys [init] :as actor}]
  (let [{:keys [graphics z-index] :as actor} (init actor state)]
    (set! (.-zOrder graphics) (or z-index 0))
    (set-graphics-position actor)
    (.addChild stage graphics)
    (sort-by-z-index stage)

    actor))

(defn remove-actor-from-stage [stage actor]
  (.removeChild stage (:graphics actor)))

(defn clear-stage [{:keys [background actors foreground stage]}]
  (doseq [c (.-children stage)]
    (.removeChild stage c))
  (doseq [object (concat background actors foreground)]
    (remove-actor-from-stage stage object)))

(defn init-stage []
  (new js/PIXI.Container))

(defn init-renderer [canvas width height]
  (js/PIXI.autoDetectRenderer (clj->js {:width width
                                        :height height
                                        :view canvas})))

(defn render [renderer stage]
  (.render renderer stage))

(defn draw-line [graphics {:keys [color width start end]}]
  (doto graphics
    (.lineStyle width color)
    (.moveTo (:x start) (:y start))
    (.lineTo (:x end) (:y end))))

(defn draw-circle [graphics {:keys [line-color fill-color x y radius line-thickness opacity]}]
  (when line-color
    (.lineStyle graphics (or line-thickness 3) line-color))
  (when fill-color
    (.beginFill graphics fill-color (or opacity 1))
    (.drawCircle graphics x y radius (or opacity 1)))
  (.endFill graphics))

(defn add-actors-to-stage [state]
  (vswap! state update :actors #(map (partial add-actor-to-stage @state) %)))

(defn add-objects-to-stage [state type]
  (vswap! state update type #(mapv (fn [{:keys [init] :as object}]
                                     (init object @state))
                                   %)))

(defn add-background-to-stage [state]
  (add-objects-to-stage state :background))

(defn add-foreground-to-stage [state]
  (add-objects-to-stage state :foreground))

(defn started? [{:keys [game-state]}]
  (= :started game-state))

(defn render-loop [state-atom]
  ((fn frame []
     (let [{:keys [renderer stage]} @state-atom]
       (render renderer stage)
       (js/requestAnimationFrame frame)))))

(defn init-render-loop [state update-fn]
  (.add (:ticker @state)
        (fn [delta]
          (when (started? @state)
            (vswap! state #(update-fn (assoc % :delta delta)))))))

(defn cancel-render-loop [state]
  (.destroy (:ticker @state)))

(defn add-drag-start-event [object handler]
  (if handler
    (doto object
      (.on "pointerdown" handler))
    object))

(defn add-drag-event [object handler]
  (if handler
    (doto object
      (.on "pointermove" handler))
    object))

(defn add-drag-end-event [object handler]
  (if handler
    (doto object
      (.on "pointerup" handler)
      (.on "pointerupoutside" handler))
    object))

(defn drag-event [object state {:keys [on-start on-move on-end]}]
  (-> object
      (add-drag-start-event (when on-start (partial on-start @state)))
      (add-drag-event (when on-move (partial on-move @state)))
      (add-drag-end-event (when on-end (fn [event] (vswap! state #(or (on-end % event) %)))))))

(defn click-coords [stage event]
  (let [point (.getLocalPosition (.-data event) stage)]
    {:x (.-x point) :y (.-y point)}))

(defn init-scene [state]
  (add-background-to-stage state)
  (add-actors-to-stage state)
  (add-foreground-to-stage state))

(defn set-stage-offset [state [x y]]
  (let [stage (:stage state)]
    (set! (-> stage .-pivot .-x) x)
    (set! (-> stage .-pivot .-y) y)))

(defn add-stage-on-click-event [state]
  (let [{:keys [stage on-click on-drag width height]} @state]
    (let [background-layer (new js/PIXI.Container)
          hit-area         (new js/PIXI.Rectangle 0 0 width height)]
      (set! (.-interactive background-layer) true)
      (set! (.-buttonMode background-layer) true)
      (set! (.-hitArea background-layer) hit-area)
      (.addChild stage background-layer)
      (when on-drag
        (drag-event background-layer state on-drag))
      (when on-click
        (set! (.-click background-layer) (partial on-click state))))))

(defn init-canvas [state scale init-fn update-fn]
  (fn [component]
    (let [canvas (r/dom-node component)
          real-width  (int  (.-width canvas))
          real-height (int  (.-height canvas))
          scaled-width  (int (/ (.-width canvas) scale))
          scaled-height (int (/ (.-height canvas) scale))
          stage  (init-stage)
          ticker (new js/PIXI.Ticker)]
      (print/lifecycle (str "init-canvas (" scaled-width "," scaled-height ")@" scale "x [" real-width "," real-height "]"))
      (vswap! state assoc
              :canvas canvas
              :width scaled-width
              :height scaled-height
              :stage stage
              :renderer (init-renderer canvas real-width real-height)
              :ticker ticker)
      (init-fn state)
      (init-render-loop state update-fn)
      (render-loop state)
      (.update ticker (js/Date.now))
      (.start ticker))))
