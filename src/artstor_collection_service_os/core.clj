(ns artstor-collection-service-os.core
  (:require [artstor-collection-service-os.schema :as data]
            [artstor-collection-service-os.repository :as repo]
            [artstor-collection-service-os.forum :as forum]
            [artstor-collection-service-os.auth :as auth]
            [artstor-collection-service-os.tokens :as tokens]
            [artstor-collection-service-os.logging :as cclog]
            [org.ithaka.clj-iacauth.core :refer [get-session-id]]
            [org.ithaka.clj-iacauth.ring.middleware :refer [with-auth]]
            [captains-common.core :as captains]
            [ring.util.http-response :refer :all]
            [ring.middleware.cookies :as rcookie]
            [ring.logger :as logger]
            [ring.swagger.upload :as upload]
            [compojure.api.sweet :refer :all]
            [clojure.tools.logging :as clogger]
            [schema.core :as s]
            [clojure.set :as set]))

(def service
  (->
    (api
      {:swagger
       {:ui   "/"
        :spec "/swagger.json"
        :data {:info {:title       "Artstor Collection Service API"
                      :description "This service exposes CRUD methods for retrieving collection information."}
               :tags [{:name "collection" :description "Services for handling collection data"}
                      {:name "category" :description "Services for handling category information about a collection"}
                      {:name "pcollection" :description "Services for handling personal collection data"}]}}}
      (context "/api/v1/collection" []
               :tags ["collection"]
               (GET "/" request
                    :return {:success s/Bool :total s/Int}
                    :header-params [{web-token :- (s/maybe s/Str) ""}]
                    :query-params [{todo :- [s/Str] []}]
                    :summary "search for collections based on logged in user"
                    :responses {200 {:schema      {:success s/Bool :total s/Int}
                                     :description "Find collections by user and return matches"}
                                400 {:description "Invalid identifiers supplied"}
                                500 {:description "Server Error handling the request"}}
                    (ok {:success true :total 0})))
      (context "/api/v1/categorydesc" []
               :tags ["category description"]
               (GET "/:id" request
                    :auth-rules auth/logged-in?
                    :header-params [{web-token :- (s/maybe s/Str) ""}]
                    :path-params [id :- Long]
                    :summary "Get Category Description"
                    :responses {200 {:description "Category Description Found"}
                                400 {:description "Invalid Category Id"}
                                404 {:description "Category Not Found"}
                                500 {:description "Server Error handling the request"}}
                    (if-let [result (repo/get-category-description id)]
                      (captains/ok result)
                      (not-found "Category Id Unavailable"))))
      (context "/api/v1/pcollection" []
               :tags ["pcollection"]
               ; file is a map with :filename, :content-type, :size and :tempfile as keys
               (POST "/image" request
                     :auth-rules auth/logged-in?
                     :header-params [{web-token :- (s/maybe s/Str) ""}]
                     :multipart-params [file :- upload/TempFileUpload]
                     :middleware [upload/wrap-multipart-params]
                     :summary "personal collection Image upload"
                     :responses {200 {:description "Uploads Image from user"}
                                 400 {:description "Invalid identifiers supplied"}
                                 500 {:description "Server Error handling the request"}}
                     (let [result (forum/upload-image-to-personal-collection file (request :artstor-user-info))]
                       (if (result :success)
                         (let [renamed-result (set/rename-keys (first (result :assets)) {:id :ssid})]
                           (captains/ok renamed-result {:ssid (get renamed-result :ssid) :filename (get renamed-result :filename)}))
                         (internal-server-error {:error "An error occurred uploading to forum services"}))))
               (PUT "/image/:ssid" request
                    :auth-rules auth/logged-in?
                    :header-params [{web-token :- (s/maybe s/Str) ""}]
                    :path-params [ssid :- Long]
                    :multipart-params [file :- upload/TempFileUpload]
                    :middleware [upload/wrap-multipart-params]
                    :summary "personal collection Media update"
                    :responses {200 {:description "Updates Image from user"}
                                400 {:description "Invalid identifiers supplied"}
                                500 {:description "Server Error handling the request"}}
                    (let [result (forum/update-media-in-personal-collection file ssid (request :artstor-user-info))]
                      (if (result :success)
                        (captains/ok {:sucess true} {:ssid ssid})
                        (internal-server-error {:error "An error occurred uploading to forum services"}))))
               (DELETE "/image" request
                       :auth-rules auth/logged-in?
                       :header-params [{web-token :- (s/maybe s/Str) ""}]
                       :query-params [{ssids :- [s/Str] []}]
                       :summary "personal collection Image upload"
                       :responses {200 {:description "Deletes Images from user"}
                                   400 {:description "Invalid identifiers supplied"}
                                   500 {:description "Server Error handling the request"}}
                       (let [items (filter #(> (count %) 0) ssids)]
                         (if (> (count items) 0)
                           (let [result (forum/delete-images-from-personal-collection items (request :artstor-user-info))]
                             (if (result :success)
                               (captains/ok result {:ssids ssids})
                               (internal-server-error! {:error "An error occurred deleting from forum services"})))
                           (bad-request))))
               (POST "/image/metadata" request
                     :auth-rules auth/logged-in?
                     :header-params [{web-token :- (s/maybe s/Str) ""}]
                     :body [metadata data/PC-Metadata]
                     :summary "personal collection Images metadata update"
                     :responses {200 {:description "Uploads Metadatas from user"}
                                 400 {:description "Invalid identifiers supplied"}
                                 500 {:description "Server Error handling the request"}}
                     (let [result (forum/update-multiple-images-metadata metadata (request :artstor-user-info))]
                       (if (result :success)
                         (captains/ok result {:ssids (map #(% :ssid) metadata)})
                         (do
                           (clogger/error {:artstor_error (str "An error occurred uploading personal collection metadata") :metadata metadata})
                           (internal-server-error {:error "An error occurred uploading personal collection metadata"})))))
               (GET "/image/metadata/definitions" request
                    :auth-rules auth/logged-in?
                    :header-params [{web-token :- (s/maybe s/Str) ""}]
                    :summary "personal collection Images metadata field definitions"
                    :responses {200 {:description "Returns personal collections field definitions"}
                                500 {:description "Server Error handling the request"}}
                    (if-let [result forum/pc-fields-definition]
                      (ok result)
                      (internal-server-error {:error "An error occurred retrieving personal collection metadata definitions"})))
               (GET "/image-status/:ssid" request
                    :auth-rules auth/logged-in?
                    :path-params [ssid :- String]
                    :header-params [{web-token :- (s/maybe s/Str) ""}]
                    :summary "Personal collection Image Status by ssid"
                    :responses {200 {:description "Returns personal collections image status"}
                                500 {:description "Server Error handling the request"}}
                    (if-let [result (first (repo/find-assets-solr ssid (tokens/get-eme-tokens request)))]
                      (ok {:status {:code 200 :message "OK" :artstorid result}})
                      (let [ready (repo/record-ready? ssid)
                            ssid-num (Integer/parseInt ssid)
                            processed (if ready (repo/process-records ssid-num))
                            res {:error {:code 500  :message
                                               (cond  (and ready (== processed ssid-num))  "Published & Indexed OK"
                                                      ready "Failed to index, trying again"
                                                      :else "Failed to publish, trying again") }}]
                        (ok res)))))
      (context "/internal/generate" []
               :tags ["web-token"]
               (POST "/" []
                     :return [{:success s/Bool :tags [s/Str]}]
                     :body [data data/ArtstorUser]
                     :summary "Returns a web token"
                     :responses {200 {:schema      {:success s/Bool :token s/Str}
                                      :description "Generate a Web token"}
                                 400 {:description "Invalid form data supplied"}
                                 403 {:description "Access denied"}
                                 500 {:description "Server Error handling the request"}}
                     (let [wt (auth/generate-web-token data)]
                       (if (nil? wt)
                         (bad-request)
                         (ok {:success true :token wt})))))
      (ANY "/*" []
           :responses {404 {:schema data/RequestStatus}}
           (not-found {:success false :message "My human masters didn't plan for this eventuality.  Pity."})))))

(def app (->> service
              (captains/wrap-web-logging {:event-type-func cclog/determine-event-type})
              (with-auth {:exclude-paths [#"/index.html"
                                          #"/swagger.json"
                                          #".\.js"
                                          #".*.js"
                                          #"/images/.*"
                                          #"/lib/.*"
                                          #"/css/.*"
                                          #"/conf/.*"
                                          #"/internal/.*"
                                          #"/"
                                          #"/watchable"]})
              auth/add-cors
              rcookie/wrap-cookies
              logger/wrap-with-logger))