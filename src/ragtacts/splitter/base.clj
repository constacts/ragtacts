(ns ragtacts.splitter.base)

(defmulti split
  "Split the documents into chunks.
   
   Args:
   - splitter: A map with the following keys:
     - `:type`: A keyword with the splitter type.
   - docs: A list of documents.

   Returns:
    - A list of chunks."
  (fn [{:keys [type]} docs] type))