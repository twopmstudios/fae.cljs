(ns fae.behavior.damaged
  (:require [fae.entities :as entities]
            [fae.events :as events]
            [fae.util :as util]))

(defn handle [g amount source]
  (let [new-hp (- (get-in g [:stats :hp]) amount)
        type (:type g)]
    (events/trigger-event! :log-entry-posted {:msg (util/format "%s took %i dmg from %s" type amount source)})
    (when (<= new-hp 0)
      (events/trigger-event! :log-entry-posted {:msg (util/format "%s died!" type)})
      (when (and (= :beetle (:type g))
                 (or (= :player source)
                     (= "lick" source)))
        (events/trigger-event! :log-entry-posted {:msg "You eat the yummy beetle."})
        (events/trigger-event! :player-healed))
      (entities/remove-entity! (:id g)))
    (assoc-in g [:stats :hp] new-hp)))