(ns ragtacts.splitter.base)

(defmulti split (fn [{:keys [type]} docs] type))