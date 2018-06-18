(ns artstor-collection-service-os.logging
  (:require [clojure.string :as str]))


(defn determine-event-type
  "Determines the type of event based on the Request Method and uri passed in"
  [req]
  (let [uri (req :uri)]
    (if (str/starts-with? uri "/api")
      (cond
        (and (str/starts-with? uri "/api/v1/categorydesc") (= (req :request-method) :get)) "artstor_category_description"
        (and (= uri "/api/v1/pcollection/image") (= (req :request-method) :post)) "artstor_pcollection_image_create"
        (and (str/starts-with? uri "/api/v1/pcollection/image") (= (req :request-method) :put)) "artstor_pcollection_image_update"
        (and (str/starts-with? uri "/api/v1/pcollection/image") (= (req :request-method) :delete)) "artstor_pcollection_image_delete"
        (and (str/starts-with? uri "/api/v1/pcollection/image/metadata") (= (req :request-method) :post)) "artstor_pcollection_image_metadata_update"
        (and (str/starts-with? uri "/api/v1/pcollection/image-status") (= (req :request-method) :get)) "artstor_pcollection_image_status_check"
        :else "artstor_undefined_ccollection_api_event")
      :no-op  ;default uses ring logging only, does not log
      )))