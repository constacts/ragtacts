(ns ragtacts.connector.sql
  (:require [ragtacts.connector.base :refer [Connector
                                             make-change-log
                                             make-change-log-result]]
            [ragtacts.document-loader.base :refer [make-doc]]
            [overtone.at-at :as at]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [next.jdbc.result-set :as rs]
            [ragtacts.logging :as log]))

(defn ->change-log-type [db-operation]
  (cond (= db-operation "I") :create
        (= db-operation "U") :update
        (= db-operation "D") :delete
        :else (throw (ex-info (str "Unknown db operation: " db-operation) {}))))

(defrecord SqlConnector [jdbc-url table-name batch-size interval !job]
  Connector
  (connect [_ callback]
    (let [last-id (atom 0)]
      (reset! !job
              (at/interspaced
               interval
               (fn []
                 (log/debug "Start SqlConnector" jdbc-url)
                 (let [db {:jdbcUrl jdbc-url}
                       ds (jdbc/get-datasource db)

                       rows (sql/query ds [(str "select * from " table-name " where id > ? order by id limit " batch-size) @last-id]
                                       {:builder-fn rs/as-unqualified-lower-maps})

                       _ (log/error "rows!!" rows)]
                   (when (seq rows)
                     (let [change-log-result (make-change-log-result (map #(make-change-log {:type (->change-log-type (:operation %))
                                                                                             :doc (make-doc (:entity_id %) (:content %))})
                                                                          rows))]
                       (callback {:type :complete :change-log-result change-log-result})
                       (reset! last-id (:id (last rows)))))))
               (at/mk-pool)))))
  (close [_]
    (log/debug "Stop SqlConnector" jdbc-url)
    (when @!job
      (at/stop @!job)))

  (closed? [_]
    (not (:scheduled? (:val @!job)))))

(defn make-sql-connector [opts]
  (map->SqlConnector (merge {:!job (atom nil)
                             :interval 5000
                             :batch-size 10}
                            opts)))

(comment
  (seq [])
  (require '[ragtacts.connector.base :refer [connect close]])
  ;; => nil
  (def make-sql-c (make-sql-connector {:jdbc-url "jdbc:postgresql://localhost/other_test_db?user=otheradmin&password=otheradmin"
                                       :table-name "rgtcs_articles_change_logs"
                                       :interval 3000
                                       :batch-size 1}))

  (connect make-sql-c
           (fn [result]
             (log/info "result" result)))

  (close make-sql-c)

 ;; todo : db에서 entity-id 로 적용, content도 넣어두자
 ;; 요 페이지 안에 trigger, table 설명서를 넣어두자.
  )
