(ns artstor-collection-service-os.forum-test
  (:require [artstor-collection-service-os.forum :as forum]
            [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clj-http.client :as http]
            [environ.core :refer [env]]
            [clojure.tools.logging :as logger]))

(deftest test-upload-image-to-personal-collection
  (testing "test uploading an image to personal collection"
    (with-redefs [env {:secret "swiss cheese"}
                  forum/forum-services-login (fn [] {:status 200, :body {:username "qa@artstor.org"}})
                  forum/reserve-imata-asset (fn [_ _] {:status 200})
                  forum/upload-image-to-storage (fn [_ _ _ _] "100")
                  forum/assign-uploaded-asset-on-imata (fn [_ _ _] {:success true :assets [{:id "123456789"}]})
                  forum/update-asset-owner-on-imata-initial-upload (fn [_ _ _] {:success true})
                  forum/massage-assign-result (fn [_ _] {:success true})
                  forum/publish-asset-at-forum (fn [_ _] true)]
      (is (= {:success true  :forum-published-status true} (forum/upload-image-to-personal-collection "my_awesome_pic.jpg" {:username "qa@artstor.org" :profile-id 12345})))))
  (testing "test uploading an image to personal collection - failed login"
    (with-redefs [env {:secret "swiss cheese"}
                  forum/forum-services-login (fn [] nil)
                  forum/reserve-imata-asset (fn [_ _] {:status 200})
                  forum/upload-image-to-storage (fn [_ _ _ _] "100")
                  forum/assign-uploaded-asset-on-imata (fn [_ _ _] {:success true})
                  forum/assign-uploaded-asset-on-imata (fn [_ _ _] {:success true :assets [{:id "123456789"}]})
                  forum/update-asset-owner-on-imata-initial-upload (fn [_ _ _] {:success true})
                  forum/massage-assign-result (fn [_ _] {:success true})
                  forum/publish-asset-at-forum (fn [_ _] {:success true})]
      (is (= {:success false} (forum/upload-image-to-personal-collection "my_awesome_pic.jpg" {:username "qa@artstor.org" :profile-id 12345}))))))

(deftest test-update-media-in-personal-collection
  (testing "test updating media of an assest in personal collection"
    (with-redefs [env {:secret "swiss cheese"}
                  forum/forum-services-login (fn [] {:status 200, :body {:username "qa@artstor.org"}})
                  forum/reserve-imata-asset (fn [_ _] {:status 200})
                  forum/upload-image-to-storage (fn [_ _ _ _] "100")
                  forum/update-uploaded-asset-on-imata (fn [_ _ _ _] {:success true, :updated_by 12345, :filename "my_awesome_pic1.jpg" :id 123456789})
                  forum/update-asset-owner-on-imata-after-editing (fn [_ _ _] {:success true})
                  forum/publish-asset-at-forum (fn [_ _] true)]
      (is (= {:success true  :forum-published-status true} (forum/update-media-in-personal-collection "my_awesome_pic1.jpg" 123456789 {:username "qa@artstor.org" :profile-id 12345})))))
  (testing "test updating media of an assest in personal collection - failed login"
    (with-redefs [env {:secret "swiss cheese"}
                  forum/forum-services-login (fn [] nil)
                  forum/reserve-imata-asset (fn [_ _] {:status 200})
                  forum/upload-image-to-storage (fn [_ _ _ _] "100")
                  forum/update-uploaded-asset-on-imata (fn [_ _ _ _] {:success true})
                  forum/update-asset-owner-on-imata-after-editing (fn [_ _ _] {:success true})
                  forum/publish-asset-at-forum (fn [_ _] {:success true})]
      (is (= {:success false} (forum/update-media-in-personal-collection "my_awesome_pic1.jpg" 123456789 {:username "qa@artstor.org" :profile-id 12345}))))))

(deftest test-delete-image-from-personal-collection
  (testing "test deleting personal collection image - single object"
    (with-redefs [env {:secret "swiss cheese"}
                  forum/validate-owner-before-delete-pc-images (fn [_ _] ["123456789"])
                  forum/forum-services-login (fn [] {:status 200, :body {:username "qa@artstor.org"}})
                  forum/delete-pc-images-from-forum (fn [_ _] {:success true})]
      (is (= {:success true} (forum/delete-images-from-personal-collection ["123456789"] {:username "qa@artstor.org" :profile-id 12345})))))
  (testing "test deleting personal collection image - multiple objects"
    (with-redefs [env {:secret "swiss cheese"}
                  forum/validate-owner-before-delete-pc-images (fn [_ _] ["123456789", "123456780"])
                  forum/forum-services-login (fn [] {:status 200, :body {:username "qa@artstor.org"}})
                  forum/delete-pc-images-from-forum (fn [_ _] {:success true})]
      (is (= {:success true} (forum/delete-images-from-personal-collection ["123456789", "123456780", "123456781"] {:username "qa@artstor.org" :profile-id 12345})))))
  (testing "test deleting personal collection image - failed login"
    (with-redefs [env {:secret "swiss cheese"}
                  forum/validate-owner-before-delete-pc-images (fn [_ _] ["123456789"])
                  forum/forum-services-login (fn [] nil)
                  forum/delete-pc-images-from-forum (fn [_ _] {:success true})]
      (is (= {:success false} (forum/delete-images-from-personal-collection ["123456789"] {:username "qa@artstor.org" :profile-id 12345}))))))

(deftest test-getting-object-ids-from-ssids
  (testing "test getting good object-id from ssid"
    (with-redefs [http/post (fn [_ _] {:status 200,
                                       :body {:requestId "22403631-c2b3-4f02-a9c1-184e7a771fab",
                                              :total 1,
                                              :results [{:date nil,
                                                         :updatedon "2018-02-09T23:04:06Z",
                                                         :yearend nil,
                                                         :doi "10.2307/artstor.1234567890",
                                                         :collections [37436],
                                                         :id "d3385544-6c0c-3cd7-9aa7-527ef07fed24",
                                                         :artstorid "SS37436_37436_39075864",}]}})]
      (is (= ["SS37436_37436_39075864"] (forum/get-object-ids-from-ssids [1234567890]))))))

(deftest test-validate-owner-before-delete-pc-images
  (testing "test getting owner allowed ssids from given ssids"
    (with-redefs [logger/log* (fn [_ _ _ message] (println "Test Log Message=" message))
                  http/post (fn [_ _] {:status 200,
                                       :body {:requestId "22403631-c2b3-4f02-a9c1-184e7a771fab",
                                              :total 1,
                                              :results [{:personalcollectionowner 12345,
                                                         :additional_Fields {:ssid "1234567890"}}]}})]
      (is (= ["1234567890"] (forum/validate-owner-before-delete-pc-images ["1234567890", "9087654321"] {:username "qa@artstor.org" :profile-id 12345}))))))

(deftest test-deleted-object-ids-from-ssids
  (testing "test getting deleted object-id from ssid"
    (with-redefs [http/post (fn [_ _] {:status 200,
                                       :body {:requestId "22403631-c2b3-4f02-a9c1-184e7a771fab",
                                              :total 1,
                                              :results [{:date "",
                                                         :updatedon "2018-02-09T23:04:06Z",
                                                         :yearend nil,
                                                         :name "Removed by request",
                                                         :doi "10.2307/artstor.1234567890",
                                                         :collections [],
                                                         :id "d3385544-6c0c-3cd7-9aa7-527ef07fed24",
                                                         :artstorid "",}]}})]
      (is (= [""] (forum/get-object-ids-from-ssids [1234567890]))))))

(deftest test-deleted-object-ids-from-ssids
  (testing "test getting not object-id from ssid"
    (with-redefs [http/post (fn [_ _] {:status 200,
                                       :body {:requestId "22403631-c2b3-4f02-a9c1-184e7a771fab",
                                              :total 0,
                                              :results []}})]
      (is (= [""] (forum/get-object-ids-from-ssids [1234567890]))))))


(deftest test-magically-massage-pc-metadata
  (testing "Converts the UI sensible structure happy path"
    (let [one-ssids-data {:ssid 12345678, :metadata [{:field_id "fd_68634_s", :value "Working"} {:field_id "fd_68633_s", :value "Awesome"}]}
          massaged-fields (forum/magically-massage-pc-metadata one-ssids-data)]
      (is (= {:id 12345678, "fd_68634_s" "Working", "fd_68633_s" "Awesome"} massaged-fields)))))

(deftest test-bad-massage-pc-metadata
  (testing "No ssid test conversion"
    (let [one-ssids-data { :metadata [{:field_id "fd_68634_s", :value "Working"} {:field_id "fd_68633_s", :value "Awesome"}]}
          massaged-fields (forum/magically-massage-pc-metadata one-ssids-data)]
      (is (= {:id nil, "fd_68634_s" "Working", "fd_68633_s" "Awesome"} massaged-fields))))
  (testing "No metadata test conversion"
    (let [one-ssids-data { :ssid 12345678}
          massaged-fields (forum/magically-massage-pc-metadata one-ssids-data)]
      (is (= {:id 12345678 } massaged-fields)))))

(deftest test-update-image-metadata
  (testing "Tests update image metadata happy path"
    (with-redefs [forum/forum-services-login (fn [] {:status 200, :body {:username "pc@artstor.org"}})
                  http/put (fn [_ _] {:status  200,
                                      :headers {"Server"         "gunicorn/19.0.0",
                                                "Content-Length" "1534", "Content-Type" "application/json; charset=UTF-8"},
                                      :body    "{\"assets\": [{\"fd_68602_s\":\"Mr Pumpkin\", \"id\": 1234567890, \"created_by\": 12345, \"filename\": \"scary_friday_prod_deploy.jpg\",\"fd_68619_s\": \"Pumpkin\", \"project_id\": 3793, \"fd_68607_s\": \"Friday the Prod Deploy\"}], \"success\": true}"})
                  forum/update-asset-owner-on-imata-after-editing (fn [_ _ _] {:success true})
                  forum/publish-asset-at-forum (fn [_ _] {:success true})]
      (let [artstor-user  {:username "qa@artstor.org" :profile-id 12345}
            pcookies { :testcookie "magicallydelicious"}
            one-ssids-data {:ssid 1234567890, :metadata [{:field_id "fd_68602_s", :value "Mr Pumpkin"} {:field_id "fd_68619_s", :value "Pumpkin"}]}
            result (forum/update-image-metadata (one-ssids-data :ssid) one-ssids-data artstor-user)]
        (is (= {:ssid 1234567890 :success true :status 200} result))))))

(deftest test-bad-update-image-metadata
  (testing "Tests update image metadata sad path"
    (with-redefs [forum/forum-services-login (fn [] {:status 200, :body {:username "pc@artstor.org"}})
                  http/put (fn [_ _] {:status  500,
                                      :headers {"Server"         "gunicorn/19.0.0",
                                                "Content-Length" "1534", "Content-Type" "application/json; charset=UTF-8"},
                                      :body    "<head>\n    <title>Internal Server Error</title>\n  </head>\n  <body>\n    <h1>Internal Server Error</h1>\n  </body>\n</html>"})
                  forum/update-asset-owner-on-imata-after-editing (fn [_ _ _] {:success true})
                  forum/publish-asset-at-forum (fn [_ _] {:success true})]
      (let [artstor-user  {:username "qa@artstor.org" :profile-id 12345}
            pcookies { :testcookie "magicallydelicious"}
            one-ssids-data {:ssid 9999867318, :metadata [{:field_id "fd_68602_s", :value "Mr Pumpkin"} {:field_id "fd_68619_s", :value "Pumpkin"}]}
            result (forum/update-image-metadata (one-ssids-data :ssid) one-ssids-data artstor-user)]
        (is (= {:ssid 9999867318 :success false :status 500} result))))))

(deftest test-multiple-update-image-metadata
  (testing "Tests update multiple image metadata happy path"
    (with-redefs [forum/forum-services-login (fn [] {:status 200, :body {:username "pc@artstor.org"}})
                  http/put (fn [_ _] {:status  200,
                                      :headers {"Server"         "gunicorn/19.0.0",
                                                "Content-Length" "1534", "Content-Type" "application/json; charset=UTF-8"},
                                      :body    "{\"assets\": [{\"fd_68602_s\":\"Mr Pumpkin\", \"id\": 1234567890, \"created_by\": 12345, \"filename\": \"scary_friday_prod_deploy.jpg\",\"fd_68619_s\": \"Pumpkin\", \"project_id\": 3793, \"fd_68607_s\": \"Friday the Prod Deploy\"}], \"success\": true}"})
                  forum/update-asset-owner-on-imata-after-editing (fn [_ _ _] {:success true})
                  forum/publish-asset-at-forum (fn [_ _] {:success true})]
      (let [artstor-user  {:username "qa@artstor.org" :profile-id 12345}
            pcookies { :testcookie "magicallydelicious"}
            many-ssids-data [{:ssid 1234567890, :metadata [{:field_id "fd_68602_s", :value "Mr Pumpkin"} {:field_id "fd_68619_s", :value "Pumpkin"}]}]
            result (forum/update-multiple-images-metadata many-ssids-data artstor-user)]
        (is (result :success))))))

(deftest test-bad-login-multiple-update-image-metadata
  (testing "Tests failed login update multiple image metadata"
    (with-redefs [forum/forum-services-login (fn [] nil)
                  http/put (fn [_ _] {:status  200,
                                      :headers {"Server"         "gunicorn/19.0.0",
                                                "Content-Length" "1534", "Content-Type" "application/json; charset=UTF-8"},
                                      :body    "{\"assets\": [{\"fd_68602_s\":\"Mr Pumpkin\", \"id\": 1234567890, \"created_by\": 12345, \"filename\": \"scary_friday_prod_deploy.jpg\",\"fd_68619_s\": \"Pumpkin\", \"project_id\": 3793, \"fd_68607_s\": \"Friday the Prod Deploy\"}], \"success\": true}"})
                  forum/update-asset-owner-on-imata-after-editing (fn [_ _ _] {:success true})
                  forum/publish-asset-at-forum (fn [_ _] {:success true})]
      (let [artstor-user  {:username "qa@artstor.org" :profile-id 12345}
            many-ssids-data [{:ssid 1234567890, :metadata [{:field_id "fd_68602_s", :value "Mr Pumpkin"} {:field_id "fd_68619_s", :value "Pumpkin"}]}]
            result (forum/update-multiple-images-metadata many-ssids-data artstor-user)]
        (is (result :success))))))