(ns artstor-collection-service-os.repository
  (:require [artstor-collection-service-os.util :as util]
            [artstor-collection-service-os.conf :refer [config-file]]
            [yesql.core :refer [defqueries]]
            [clojure.set :as set]
            [environ.core :refer [env]]
            [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json])
  (:import (com.mchange.v2.c3p0 DataSources)))

(def db-spec {:datasource (DataSources/pooledDataSource
                            (DataSources/unpooledDataSource (config-file :artstor-ccollection-db-url)))})

;; This is a macro that reads the db calls in the specified collections.sql file and generates functions based on the
;; comments in the collections.sql file.
(defqueries "artstor_collection_service_os/sql/collections.sql"
            {:connection db-spec})

(defn get-category-description [category-id]
  (set/rename-keys (first (sql-get-category-description {:category_id category-id}))
                   {:image_desc :imageDesc,
                    :image_url :imageUrl,
                    :lead_object_id :leadObjectId,
                    :short_description :shortDescription,
                    :blurb_url :blurbUrl}))


(defn find-assets-solr ([ssids] (find-assets-solr ssids []))
  ([ssids eme-tokens]
   "Get the metadata for the given ssids"
   (let [url (util/build-service-url "search-service" "browse/")
         form {:form-params  {:limit         (count ssids)
                              :content_types ["art"]
                              :filter_query  [(str "ssid:(" ssids ")")]
                              :tokens        eme-tokens}
               :content-type :json :as :json}
         response (http/post url form)]
     (if (nil? response)
       []
       (let [data (-> response :body :results)]
         (into [] (sort-by #(.indexOf ssids %) (map #(get % :artstorid) data))))))))

(defn record-ready? [ssid]
  "Checks with ARB whether the ssid can be translated into a valid records"
  (try
    (let [url (util/build-service-url "artstor-record-builder" (str "/api/v1/record/" ssid))
          resp (http/get url {:throw-exceptions false})]
      (= 200 (resp :status)))
    (catch Exception e
      (log/error "Failed to connect to Artstor Record Builder" e)
      false)))


(defn process-records [ssid]
  (let [url (util/build-service-url "artstor-record-builder" "/api/v1/publish")
        res (http/post url
                       {:body (json/write-str [ssid])
                        :content-type :json
                        :accept :json
                        :throw-exceptions false})]
    (condp = (res :status)
      200
      (let [processed ((json/read-str (res :body)) "count")]
        (log/info (str "Processed " processed " records from Personal collection."))
        (if (not (= processed 1))
          (log/info (str "Failed to process 1 records from batch-id.")) ssid))
      404 (log/info (str "404 Failed to process 1  record for [" ssid "]."))
      500 (log/info (str "500 Failed to process 1 record [" ssid "].")))))
