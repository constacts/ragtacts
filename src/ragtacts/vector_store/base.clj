(ns ragtacts.vector-store.base
  (:require [ragtacts.embedding.base :as embedding]))

(defmulti save (fn [{:keys [db]} docs] (:type db)))

(defmulti search (fn [{:keys [db]} query & [params]] (:type db)))

(defn embed [{:keys [embedding]} texts]
  (embedding/embed embedding texts))