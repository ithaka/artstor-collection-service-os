(ns artstor-collection-service-os.core-test
  (:require [artstor-collection-service-os.forum :as forum]
            [artstor-collection-service-os.auth :as auth]
            [artstor-collection-service-os.core :as core]
            [artstor-collection-service-os.repository :as repo]
            [artstor-collection-service-os.tokens :as tokens]
            [artstor-collection-service-os.conf :refer [config-file]]
            [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.tools.logging :as logger]
            [ragtime.jdbc :as rag]
            [ragtime.repl :refer [migrate rollback]]
            [ring.mock.request :as mock]
            [peridot.core :as peridot]
            [clojure.java.io :as io]
            [cheshire.core :as cheshire]
            [clj-http.client :as http]))

(def config {:datastore  (rag/sql-database {:connection-uri (config-file :artstor-ccollection-db-url)})
             :migrations (rag/load-resources "test-migrations")})

(defmacro with-db [conf & body]
  `(do
     (migrate ~conf)
     (try
       ~@body
       (finally
         (rollback ~conf "001")))))

(def web-token (auth/generate-web-token 12345 1000 false "qa@artstor.org"))

;REPL/unit test option-- [#schema.core.OptionalKey{:k :tempfile} java.io.File]
(deftest personal-collection-image-upload
  (let [app core/app
        file {:filename "BillsFunFile.png" :content-type "image/png" :size 1234}]
    (with-redefs [forum/upload-image-to-personal-collection (fn [_ _] {:success true :id "123456012"})
                  logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
      (testing "Test invalid parameters image upload"
        (let [response (app (-> (mock/request :post "/api/v1/pcollection/image" )
                                (mock/header "web-token" web-token)))
              raw-body (slurp (:body response))
              body (cheshire.core/parse-string raw-body true)]
          (is (= (:status response) 400)))))

    (with-redefs [forum/upload-image-to-personal-collection (fn [_ _] {:success true :id "123456012"})
                  logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
      (testing "Test not authorized to upload"
        (let [response (app (-> (mock/request :post "/api/v1/pcollection/image")))]
          (is (= (:status response) 401)))))

    ;;Uses peridot instead of mock to generate multipart/form-data
    (with-redefs [forum/upload-image-to-personal-collection (fn [_ _] (do (println "uploading file redef....") {:success true :id "123456012"}))
                  logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
      (testing "Test good image upload"
        (let [_ (spit "simulatedimage.png" "Iwannahippopatamousforchristmas")
              my-image (io/file "simulatedimage.png")
              state-info (-> (peridot/session app) ;Use your ring app
                             (peridot/header "web-token" web-token)
                             (peridot/request "/api/v1/pcollection/image" :request-method :post :params {:file my-image}))
              deleted (io/delete-file "simulatedimage.png" true)]
          (is (= ((state-info :response) :status) 200)))))))

(deftest personal-collection-image-upload-handle-long-number
  (let [app core/app
        file {:filename "BillsFunFile.png" :content-type "image/png" :size 1234}]
    ;;Uses peridot instead of mock to generate multipart/form-data
    (with-redefs [forum/upload-image-to-personal-collection (fn [_ _] (do (println "uploading file redef....") {:success true :id "123456012789"}))
                  logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
      (testing "Test good image upload"
        (let [_ (spit "simulatedimage.png" "Iwannahippopatamousforchristmas")
              my-image (io/file "simulatedimage.png")
              state-info (-> (peridot/session app) ;Use your ring app
                             (peridot/header "web-token" web-token)
                             (peridot/request "/api/v1/pcollection/image" :request-method :post :params {:file my-image}))
              deleted (io/delete-file "simulatedimage.png" true)]
          (is (= ((state-info :response) :status) 200)))))))

(deftest personal-collection-image-delete
  (let [app core/app]
    (with-redefs [forum/delete-images-from-personal-collection (fn [_ _] {:success true})
                  logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
      (testing "Test delete image from personal collection - single image"
        (let [response (app (-> (mock/header (mock/request :delete "/api/v1/pcollection/image?ssids=123456789") "web-token" web-token)))
              raw-body (slurp (:body response))
              body (cheshire.core/parse-string raw-body true)]
          (is (= (:status response) 200))))
      (testing "Test delete image from personal collection - single image with long number ssid"
        (let [response (app (-> (mock/header (mock/request :delete "/api/v1/pcollection/image?ssids=123456012789") "web-token" web-token)))
              raw-body (slurp (:body response))
              body (cheshire.core/parse-string raw-body true)]
          (is (= (:status response) 200))))
      (testing "Test delete image from personal collection - multiple images"
        (let [response (app (-> (mock/header (mock/request :delete "/api/v1/pcollection/image?ssids=123456789&object_ids=123456780") "web-token" web-token)))
              raw-body (slurp (:body response))
              body (cheshire.core/parse-string raw-body true)]
          (is (= (:status response) 200))))
      (testing "Test delete image from personal collection - no image"
        (let [response (app (-> (mock/header (mock/request :delete "/api/v1/pcollection/image?ssids=") "web-token" web-token)))]
          (is (= (:status response) 400)))))))

(deftest personal-collection-media-replace
  (let [app core/app
        file {:filename "tmpimages.jpeg" :content-type "image/jpeg" :size 1234}]
    (with-redefs [forum/update-media-in-personal-collection (fn [_ _ _] {:success true :id "123456012"})
                  logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
      (testing "Test invalid parameters image upload"
        (let [response (app (-> (mock/request :put "/api/v1/pcollection/image/123456012")
                                (mock/header "web-token" web-token)))
              raw-body (slurp (:body response))
              body (cheshire.core/parse-string raw-body true)]
          (is (= (:status response) 400)))))

    (with-redefs [forum/update-media-in-personal-collection (fn [_ _ _] {:success true :id "123456012"})]
      (testing "Test not authorized to upload"
        (let [response (app (-> (mock/request :put "/api/v1/pcollection/image/123456012" )))]
          (is (= (:status response) 401)))))

    ;;Uses peridot instead of mock to generate multipart/form-data
    (with-redefs [forum/update-media-in-personal-collection (fn [_ _ _] (do (println "updating media redef....") {:success true :id "123456012"}))
                  logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
      (testing "Test good image replacement"
        (let [_ (spit "simulatedimage.png" "Iwannahippopatamousforchristmas")
              my-image (io/file "simulatedimage.png")
              state-info (-> (peridot/session app) ;Use your ring app
                             (peridot/header "web-token" web-token)
                             (peridot/request "/api/v1/pcollection/image/123456012" :request-method :put :params {:file my-image}))
              deleted (io/delete-file "simulatedimage.png" true)]
          (is (= ((state-info :response) :status) 200)))))

    (with-redefs [forum/update-media-in-personal-collection (fn [_ _ _] (do (println "updating media redef for long ssid....") {:success true :id "123456012789"}))
                  logger/log* (fn [_ _ _ message] (println "Test Log Message for long ssid=" message))]
      (testing "Test good image replacement for long ssid"
        (let [_ (spit "simulatedimage.png" "Iwannahippopatamousforchristmas")
              my-image (io/file "simulatedimage.png")
              state-info (-> (peridot/session app) ;Use your ring app
                             (peridot/header "web-token" web-token)
                             (peridot/request "/api/v1/pcollection/image/123456012789" :request-method :put :params {:file my-image}))
              deleted (io/delete-file "simulatedimage.png" true)]
          (is (= ((state-info :response) :status) 200)))))))

(deftest update-personal-collection-image-metadata
  (let [app core/app]
    (testing "Test update image metadata for personal collection - single image"
      (with-redefs [forum/forum-services-login (fn [] {:status 200, :body {:username "pc@artstor.org"}})
                    http/put (fn [_ _] {:status  200,
                                        :headers {"Server"         "gunicorn/19.0.0",
                                                  "Content-Length" "1534", "Content-Type" "application/json; charset=UTF-8"},
                                        :body    "{\"assets\": [{\"fd_68602_s\":\"Mr Pumpkin\", \"id\": 1234567890, \"created_by\": 123456, \"filename\": \"scary_friday_prod_deploy.jpg\",\"fd_68619_s\": \"Pumpkin\", \"project_id\": 3793, \"fd_68607_s\": \"Friday the Prod Deploy\"}], \"success\": true}"})
                    forum/update-asset-owner-on-imata-after-editing (fn [_ _ _] {:success true})
                    forum/publish-asset-at-forum (fn [_ _] {:success true})
                    logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
        (let [request-data (cheshire/generate-string [{:ssid 1234567890,
                                                       :metadata [{:field_id "fd_68602_s", :value "Mr Pumpkin"}
                                                                  {:field_id "fd_68619_s", :value "Pumpkin"}]}])
              response (app (-> (mock/request :post "/api/v1/pcollection/image/metadata" request-data)
                                (mock/content-type  "application/json")
                                (mock/header "web-token" web-token)))
              raw-body (slurp (:body response))
              body (cheshire.core/parse-string raw-body true)]
          (is (= (:status response) 200))
          (is (= (body :success))))))
    (testing "Test update image metadata for personal collection - log post body when 500"
      (with-redefs [forum/update-multiple-images-metadata (fn [_ _] {:success false})
                    logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
        (let [request-data (cheshire/generate-string [{:ssid 1234567890,
                                                       :metadata [{:field_id "fd_68602_s", :value "Mr Pumpkin"}
                                                                  {:field_id "fd_68619_s", :value "Pumpkin"}]}])
              response (app (-> (mock/request :post "/api/v1/pcollection/image/metadata" request-data)
                                (mock/content-type  "application/json")
                                (mock/header "web-token" web-token)))
              raw-body (slurp (:body response))
              body (cheshire.core/parse-string raw-body true)]
          (is (= (:status response) 500))
          (is (= "An error occurred uploading personal collection metadata" (body :error))))))))

(deftest cors-options-test
  (with-redefs [forum/update-multiple-images-metadata (fn [_ _] {:success true})
                logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
    (let [app core/app]
      (testing "Options call returns CORS headers"
        (let [response (app (-> (mock/header (mock/request :options "/api/v1/pcollection/image/metadata") "origin" "weird-domain")
                                (mock/header "fastly-client-ip" "38.66.77.2")))]
          (is (= (:status response) 200))
          (is (= (get (:headers response) "Access-Control-Allow-Origin") "weird-domain"))
          (is (= (get (:headers response) "Access-Control-Allow-Methods") "GET, POST, PUT, DELETE, OPTIONS")))))))

(deftest cors-post-options-test
  (with-redefs [forum/update-multiple-images-metadata (fn [_ _] {:success true})
                logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
    (let [app core/app]
      (testing "POST call does not return CORS headers"
        (let [response (app (-> (mock/header (mock/request :post "/api/v1/pcollection/image/metadata") "origin" "weird-domain")
                                (mock/header "web-token" web-token)
                                (mock/header "fastly-client-ip" "38.66.77.2")))]
          (is (= (:status response) 200))
          (is (= (not (contains? (:headers response) "Access-Control-Allow-Origin"))))
          (is (= (not (contains? (:headers response) "Access-Control-Allow-Methods")))))))))

(deftest cors-put-options-test
  (with-redefs [logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
    (let [app core/app]
      (testing "PUT call returns CORS headers"
        (let [response (app (-> (mock/header (mock/request :put "/api/v1/pcollection/image/1234") "origin" "totally-not-nefarious")
                                (mock/header "web-token" web-token)
                                (mock/header "fastly-client-ip" "38.66.77.2")))]
          (is (= (:status response) 400))
          (is (= (get (:headers response) "Access-Control-Allow-Origin") "totally-not-nefarious"))
          (is (= (get (:headers response) "Access-Control-Allow-Methods") "GET, POST, PUT, DELETE, OPTIONS")))))))

(deftest cors-delete-options-test
  (with-redefs [forum/delete-images-from-personal-collection (fn [_ _] {:success true})
                logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
    (let [app core/app]
      (testing "DELETE call returns CORS headers"
        (let [response (app (-> (mock/header (mock/request :delete "/api/v1/pcollection/image?ssids=12345") "origin" "totally-not-nefarious")
                                (mock/header "web-token" web-token)
                                (mock/header "fastly-client-ip" "38.66.77.2")))]
          (is (= (:status response) 200))
          (is (= (get (:headers response) "Access-Control-Allow-Origin") "totally-not-nefarious"))
          (is (= (get (:headers response) "Access-Control-Allow-Methods") "GET, POST, PUT, DELETE, OPTIONS")))))))

(deftest personal-collection-image-status
  (let [app core/app]
    (with-redefs [repo/process-records (fn [_] 123)
                  tokens/get-eme-tokens (fn [_] [])
                  repo/find-assets-solr (fn [_ _] ["obj1"])
                  repo/record-ready? (fn [_] true)
                  logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
      (testing "Test poll for personal collection image status"
        (let [response (app (-> (mock/header (mock/request :get "/api/v1/pcollection/image-status/123") "web-token" web-token)))
              raw-body (slurp (:body response))
              body (cheshire.core/parse-string raw-body true)]
          (is (= (:status response) 200))
          (is (= ((:status body) :message "OK"))))))
    (with-redefs [repo/process-records (fn [_] nil)
                  tokens/get-eme-tokens (fn [_] [])
                  repo/find-assets-solr (fn [_ _] [])
                  repo/record-ready? (fn [_] false)
                  logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
      (testing "Test poll for personal collection image status by passing invalid ssid"
        (let [response (app (-> (mock/header (mock/request :get "/api/v1/pcollection/image-status/12356") "web-token" web-token)))
              raw-body (slurp (:body response))
              body (cheshire.core/parse-string raw-body true)]
          (is (= (:status response) 200))
          (is (= ((:error body) :code "500"))))))))

(deftest test-get-category-description
  (with-redefs [logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
    (let [app core/app]
      (with-db config
               (testing "Test get category description when valid category 123456789"
                 (let [response (app (-> (mock/request :get "/api/v1/categorydesc/123456789")
                                         (mock/header "web-token" web-token)))
                       raw-body (slurp (:body response))
                       body (cheshire.core/parse-string raw-body true)]
                   (is (= (:status response) 200))
                   (is (= 123456789 (body :id)))
                   (is (= "Test Image Description 1" (body :imageDesc)))
                   (is (= "/path-to-image-url/filename1.jpg" (body :imageUrl)))
                   (is (= "<html> <p>Testing html Blurb Url 1<br><A HREF=\"http://www.artstor.org\">artstor1</A></p></html>" (body :blurbUrl)))
                   (is (= "Testing Short Description 1" (body :shortDescription)))
                   (is (= "OBJ1" (body :leadObjectId)))
                   (is (= "Test Category Name 1" (body :name)))))
               (testing "Test get category description when valid category 987654321"
                 (let [response (app (-> (mock/request :get "/api/v1/categorydesc/987654321")
                                         (mock/header "web-token" web-token)))
                       raw-body (slurp (:body response))
                       body (cheshire.core/parse-string raw-body true)]
                   (is (= (:status response) 200))
                   (is (= 987654321 (body :id)))
                   (is (= "Test Image Description 2" (body :imageDesc)))
                   (is (= "/path-to-image-url/filename2.jpg" (body :imageUrl)))
                   (is (= "<html> <p>Testing html Blurb Url 2<br><A HREF=\"http://www.artstor.org\">artstor2</A></p></html>" (body :blurbUrl)))
                   (is (= "Testing Short Description 2" (body :shortDescription)))
                   (is (= "OBJ2" (body :leadObjectId)))
                   (is (= "Test Category Name 2" (body :name)))))
               (testing "Test get category description when invalid long category id"
                 (let [response (app (-> (mock/request :get "/api/v1/categorydesc/111111111")
                                         (mock/header "web-token" web-token)))]
                   (is (= (:status response) 404))
                   (is (= "Category Id Unavailable" (:body response)))))
               (testing "Test get category description when invalid category with "
                 (let [response (app (-> (mock/request :get "/api/v1/categorydesc/11111aaaa")
                                         (mock/header "web-token" web-token)))]
                   (is (= (:status response) 400))))))))

(deftest personal-collection-image-status
  (let [app core/app]
    (with-redefs [repo/process-records (fn [_] 123)
                  tokens/get-eme-tokens (fn [_] [])
                  repo/find-assets-solr (fn [_ _] ["obj1"])
                  repo/record-ready? (fn [_] true)
                  logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
      (testing "Test poll for personal collection image status"
        (let [response (app (-> (mock/header (mock/request :get "/api/v1/pcollection/image-status/123") "web-token" web-token)))
              raw-body (slurp (:body response))
              body (cheshire.core/parse-string raw-body true)]
          (is (= (:status response) 200))
          (is (= ((:status body) :message "OK"))))))
    (with-redefs [repo/process-records (fn [_] nil)
                  tokens/get-eme-tokens (fn [_] [])
                  repo/find-assets-solr (fn [_ _] [])
                  repo/record-ready? (fn [_] false)
                  logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))]
      (testing "Test poll for personal collection image status by passing invalid ssid"
        (let [response (app (-> (mock/header (mock/request :get "/api/v1/pcollection/image-status/12356") "web-token" web-token)))
              raw-body (slurp (:body response))
              body (cheshire.core/parse-string raw-body true)]
          (is (= (:status response) 200))
          (is (= ((:error body) :code "500"))))))))