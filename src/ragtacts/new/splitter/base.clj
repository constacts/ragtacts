(ns ragtacts.new.splitter.base)

(defmulti split (fn [{:keys [type]} docs] type))