(ns artstor-collection-service-os.logging-test
  (:require [clojure.test :refer :all]
            [artstor-collection-service-os.logging :as log]))

(deftest test-determining-eventtype
  (testing "test determining event type mulitple requests"
    (is (= "artstor_pcollection_image_create" (log/determine-event-type {:uri "/api/v1/pcollection/image" :request-method :post })))
    (is (= "artstor_pcollection_image_update" (log/determine-event-type {:uri "/api/v1/pcollection/image/123456" :request-method :put })))
    (is (= "artstor_pcollection_image_delete" (log/determine-event-type {:uri "/api/v1/pcollection/image?ssids=123456" :request-method :delete })))
    (is (= "artstor_pcollection_image_metadata_update" (log/determine-event-type {:uri "/api/v1/pcollection/image/metadata" :request-method :post }))))
  (testing "test artstor_pcollection_image_status_check"
    (is (= "artstor_pcollection_image_status_check" (log/determine-event-type {:uri "/api/v1/pcollection/image-status" :request-method :get }))))
  (testing "test not yet used api"
    (is (= "artstor_undefined_ccollection_api_event" (log/determine-event-type {:uri "/api/v1/somenewapi" :request-method :get }))))
  (testing "test not an api call"
    (is (= :no-op (log/determine-event-type {:uri "/swagger.json" :request-method :get })))))