(ns artstor-collection-service-os.repository-test
  (:require [artstor-collection-service-os.util :as util]
            [artstor-collection-service-os.repository :as repo]
            [artstor-collection-service-os.conf :refer [config-file]]
            [clj-http.client :as http]
            [clojure.test :refer :all]
            [ragtime.repl :refer [migrate rollback]]
            [ragtime.jdbc :as rag]))

(def config {:datastore  (rag/sql-database {:connection-uri (config-file :artstor-ccollection-db-url)})
             :migrations (rag/load-resources "test-migrations")})

(defmacro with-db [conf & body]
  `(do
     (migrate ~conf)
     (try
       ~@body
       (finally
         (rollback ~conf "001")))))

(deftest test-find-assets-solr
   (testing "call search with ssids and valid tokens"
            (with-redefs [util/build-service-url (fn [_ _] "search-service")
                          http/post (fn [_ opts]
                                      (is (= ((opts :form-params) :limit) 1))
                                      (is (= ((opts :form-params) :filter_query)
                                             [(str "ssid:(" [123] ")")]))
                                      {:body {:results [{:artstorid "obj1" :ssid 123}]}})]
                (is (= ["obj1"] (repo/find-assets-solr [123]  "12345678901")))))
   (testing "call search with invalid ssids and valid tokens"
     (with-redefs [util/build-service-url (fn [_ _] "search-service")
                   http/post (fn [_ opts]
                               (is (= ((opts :form-params) :limit) 1))
                               (is (= ((opts :form-params) :filter_query)
                                      [(str "ssid:(" [123456] ")")]))
                               {:body {:results []}})]
       (is (empty? (repo/find-assets-solr [123456]  "12345678901"))))))

(deftest test-record-ready
  (testing "call ARB to check if ssid is ready"
    (with-redefs [util/build-service-url (fn [_ _] "artstor-record-builder")
                  http/get (fn [_ _] {:status 200})]
      (is (true? (repo/record-ready? 123)))))
  (testing "call ARB to check if an invalid ssid is ready"
    (with-redefs [util/build-service-url (fn [_ _] "artstor-record-builder")
                  http/get (fn [_ _] {:status 404 :body "I couldn't find a SharedShelf record with that id."})]
      (is (false? (repo/record-ready? 123456))))))

(deftest test-process-records
  (testing "call process records for indexing for valid ssid"
    (with-redefs [util/build-service-url (fn [_ _] "artstor-record-builder")
                  http/post (fn [_ opts]
                              (is (= (opts :body) "[123]"))
                              {:status 200 :body "{\"count\" 1 }"})]
      (is (= 123 (repo/process-records 123)))))
  (testing "call process records for indexing for invalid ssid"
    (with-redefs [util/build-service-url (fn [_ _] "artstor-record-builder")
                  http/post (fn [_ opts]
                              (is (= (opts :body) "[123456]"))
                              {:status 404 :body "I couldn't find any of the SharedShelf records requested."})])))

(deftest test-get-category-description
  (with-db config
           (testing "Finding an existing category 123456789"
             (let [data (repo/get-category-description 123456789)]
               (is (= 123456789 (data :id)))
               (is (= "Test Category Name 1" (data :name)))
               (is (= "Test Image Description 1" (data :imageDesc)))
               (is (= "/path-to-image-url/filename1.jpg" (data :imageUrl)))
               (is (= "<html> <p>Testing html Blurb Url 1<br><A HREF=\"http://www.artstor.org\">artstor1</A></p></html>" (data :blurbUrl)))
               (is (= "Testing Short Description 1" (data :shortDescription)))
               (is (= "OBJ1" (data :leadObjectId)))))
           (testing "Finding an existing category 987654321"
             (let [data (repo/get-category-description 987654321)]
               (is (= 987654321 (data :id)))
               (is (= "Test Category Name 2" (data :name)))
               (is (= "Test Image Description 2" (data :imageDesc)))
               (is (= "/path-to-image-url/filename2.jpg" (data :imageUrl)))
               (is (= "<html> <p>Testing html Blurb Url 2<br><A HREF=\"http://www.artstor.org\">artstor2</A></p></html>" (data :blurbUrl)))
               (is (= "Testing Short Description 2" (data :shortDescription)))
               (is (= "OBJ2" (data :leadObjectId)))))
           (testing "Finding an non existing category"
             (let [data (repo/get-category-description 12345678900)]
               (is (nil? data))))))
