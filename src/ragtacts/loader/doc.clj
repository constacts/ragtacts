(ns ragtacts.loader.doc
  (:require [clj-ulid :refer [ulid]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [nextjournal.beholder :as beholder])
  (:import [dev.langchain4j.data.document Document DocumentParser]
           [dev.langchain4j.data.document.parser.apache.tika ApacheTikaDocumentParser]
           (java.io ByteArrayOutputStream)
           [org.apache.pdfbox.pdmodel PDDocument]
           [org.apache.pdfbox.rendering PDFRenderer]
           [org.apache.pdfbox.rendering ImageType]
           [org.apache.pdfbox.tools.imageio ImageIOUtil]))

(defn- range-intersection-for-border-pairs
  [range-border-pairs]
  (let [intersection-vec (reduce
                          (fn [[start1 end1] [start2 end2]]
                            [(max start1 start2) (min end1 end2)])
                          (first range-border-pairs)
                          (rest range-border-pairs))]
    (range (first intersection-vec)
           (last intersection-vec))))

(defn- image->byte-array
  [{image :image ext :ext dpi :dpi}]
  (let [baos (ByteArrayOutputStream.)]
    (try
      (ImageIOUtil/writeImage image ext baos dpi)
      (.flush baos)
      (.toByteArray baos)
      (finally
        (when (not= baos nil) (.close baos))))))

(defn- pdf-to-images-byte-array-list
  [pdf-input-stream {:keys [start-page end-page dpi ext]}]
  (let [real-start-page (dec start-page)
        real-end-page end-page
        pd-document (PDDocument/load pdf-input-stream)
        pdf-renderer (PDFRenderer. pd-document)
        pages (vec (.getPages pd-document))
        page-range (range-intersection-for-border-pairs [[0 (count pages)]
                                                         [real-start-page real-end-page]])]
    (try
      (doall
       (map
        (fn [page-index]
          (let [image (.renderImageWithDPI pdf-renderer page-index dpi ImageType/RGB)]
            (image->byte-array {:image image
                                :ext ext
                                :dpi dpi})))
        page-range))
      (finally
        (if (not= pd-document nil)
          (.close pd-document)
          (print "No PDF document to close"))))))

(defn get-text
  "Return the text of a document. PDF, DOCX, and other formats are supported.
   
   Returns:
    - A map with the following keys:
      - `:id`: A string with the document id.
      - `:text`: A string with the document text.
      - `:metadata`: A map with the document metadata.
   "
  [path-or-input-stream]
  (let [input-stream (if (string? path-or-input-stream)
                       (-> path-or-input-stream
                           (str/replace #"~" (System/getProperty "user.home"))
                           io/input-stream)
                       path-or-input-stream)
        ^DocumentParser parser (ApacheTikaDocumentParser.)
        ^Document doc (.parse parser input-stream)]
    {:id (if (string? path-or-input-stream)
           path-or-input-stream
           (ulid))
     :text (.text doc)
     :metadata {}}))

(defn watch [{:keys [path last-change]} callback]
  (let [watcher-id (beholder/watch (fn [event]
                                     (reset! last-change (System/currentTimeMillis))
                                     (callback event))
                                   path)]
    watcher-id))

(defn stop-watch [watcher-id]
  (beholder/stop watcher-id))


(defn get-images-from-pdf
  "Return the image's byte-array list of a documnet.PDF supported.
  Args:
  - pdf-input-stream: An InputStream of a PDF file,
   e.g.,  If you have a file `(clojure.java.io/input-stream \"/path/to/your/file.pdf\")
  - options: A map with the following keys
   - `:start-page`: A start page of the pdf file. (default 0)
   - `:end-page`: A last page of the pdf file. (default Integer/MAX_VALUE)
   - `dpi`: A number of dots that fit horizontally and vertically into a one-inch length. (default 300)
   - `ext`: A string with the extension of the image.(default 'png')"
  [pdf-input-stream & {:keys [start-page end-page dpi ext]
                       :or {start-page 0
                            end-page Integer/MAX_VALUE
                            dpi 300
                            ext "png"}}]
  (pdf-to-images-byte-array-list  pdf-input-stream
                                  {:start-page start-page
                                   :end-page end-page
                                   :dpi dpi
                                   :ext ext}))