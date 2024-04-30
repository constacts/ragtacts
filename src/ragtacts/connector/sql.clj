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

(defrecord SqlConnector [jdbc-url table-name batch-size interval !job pool]
  Connector
  (connect [_ callback opts]
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
               pool))))
  (close [_]
    (log/debug "Stop SqlConnector" jdbc-url)
    (at/stop-and-reset-pool! pool))

  (closed? [_]
    (not (:scheduled? (:val @!job)))))

(defn make-sql-connector [opts]
  (map->SqlConnector (merge {:!job (atom nil)
                             :interval 5000
                             :batch-size 10
                             :pool (at/mk-pool)}
                            opts)))

(comment
  ;; In order to utilize the SQL connector, it's essential to have a table designated for the connector.
  ;; These tables can be created either through triggers and Functions
  ;; They should include the following columns:
  ;; -----------------------------------------------
  ;; |   id           | serial          |         -
  ;; |   entity_id    | int             | To reflect changes in the articles table, this is the ID of the table, for example.
  ;; |   operation    | varchar (6)     | To store the operation type (I, U, D)
  ;; |   content      | text            | To store the content of the article's changes
  ;; |   created_at   | timestampz      |         -
  ;; -----------------------------------------------

  ;; *** Function example:

  ;; -- CREATE OR REPLACE FUNCTION log_article_changes ()
  ;; -- RETURNS TRIGGER AS $$
  ;; -- BEGIN
  ;; --     -- Check the type of operation (INSERT, UPDATE, DELETE)
  ;; --     IF TG_OP = 'INSERT' THEN
  ;; --         INSERT INTO test2 (article_id, action)
  ;; --         VALUES (NEW.id, 'I');
  ;; --     ELSIF TG_OP = 'UPDATE' THEN
  ;; --         INSERT INTO test2 (article_id, action)
  ;; --         VALUES (NEW.id, 'U');
  ;; --     ELSIF TG_OP = 'DELETE' THEN
  ;; --         INSERT INTO test2 (article_id, action)
  ;; --         VALUES (OLD.id, 'D');
  ;; --     END IF;
  ;; --     RETURN NULL; -- This is required for AFTER triggers
  ;; -- END;
  ;; -- $$ LANGUAGE plpgsql;


  ;; *** Trigger example:

  ;; -- CREATE TRIGGER articles_change_trigger
  ;; -- AFTER INSERT OR UPDATE OR DELETE ON articles
  ;; -- FOR EACH ROW
  ;; -- EXECUTE FUNCTION log_article_changes ();


  ;; *** Write the options as shown below:
  ;; --------------------------------------------------------
  ;; | :jdbc-url            | Your JDBC URL. eg: jdbc:postgresql://localhost/{db_name}?user={user}&password={password}"
  ;; | :table-name          | Table name to put in RAG. eg: rgtcs_{table-name}_change_logs
  ;; | :check-for-changes   | Check for table's changes.
  ;; | :changed-values-size | Number of rows to fetch at one time.
  ;; --------------------------------------------------------

  (require '[ragtacts.connector.base :refer [connect close]])

  (def options {:jdbc-url "jdbc:postgresql://localhost/your_test_db?user=ragtacts&password=ragtacts"
                :table-name "rgtcs_artcles_change_logs"
                :interval 3000
                :batch-size 1})

  (def make-sql-c (make-sql-connector options))

  (connect make-sql-c
           (fn [result]
             (log/info "result" result))
           {})

  (close make-sql-c))
