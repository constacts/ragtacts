(ns ragtacts.logging
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]))

(def ^:private colors
  {:red 31
   :green 32
   :yellow 33
   :blue 34})

(defn- colorize [color args]
  (str "\u001B[" (get colors color) "m" (str/join " " (map #(if (string? %)
                                                              %
                                                              (pr-str %)) args)) "\u001B[0m"))

(defn error [& args]
  (log/error (colorize :red args)))

(defn debug [& args]
  (log/debug (colorize :blue args)))

(defn warn [& args]
  (log/warn (colorize :yellow args)))

(defn info [& args]
  (log/info (colorize :green args)))

