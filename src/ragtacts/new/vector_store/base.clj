(ns ragtacts.new.vector-store.base)

(defmulti save (fn [{:keys [db]} docs] (:type db)))

(defmulti search (fn [{:keys [db]} query & [params]] (:type db)))

(defmulti embed (fn [{:keys [db]} docs]))
