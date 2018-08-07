(ns artstor-collection-service-os.forum
  (:require [artstor-collection-service-os.util :as util]
            [artstor-collection-service-os.auth :as auth]
            [artstor-collection-service-os.schema :as schema]
            [artstor-collection-service-os.conf :refer [config-file]]
            [environ.core :refer [env]]
            [clojure.java.io :as io]
            [clojure.tools.logging :as logger]
            [clj-http.client :as http]
            [cheshire.core :as cheshire]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.spec :as spec]
            [clojure.set :as set]))

;; Functions related to calling the Forum Services
;;FILE UPLOAD TO FORUM

(def forum-spec {:forum-url (config-file :artstor-ccollection-forum-url)
                 :forum-username (config-file :artstor-ccollection-forum-username)
                 :forum-password (config-file :artstor-ccollection-forum-password)
                 :forum-project-id (config-file :forum-project-id)
                 :pc-publishing-target-id (config-file :pc-publishing-target-id)
                 :forum-media-source-id (config-file :forum-media-source-id)})

(def pc-fields-definition {:fields [{:label "Creator",      :id "fd_68602_s"}
                                    {:label "Nationality",  :id "fd_68603_s"}
                                    {:label "Life Dates",   :id "fd_68604_s"}
                                    {:label "Role",         :id "fd_68605_s"}
                                    {:label "Culture",      :id "fd_68606_s"}
                                    {:label "Title",        :id "fd_68607_s"}
                                    {:label "Series Title", :id "fd_68608_s"}
                                    {:label "Work Type",    :id "fd_68609_s"}
                                    {:label "Dynasty",      :id "fd_68610_s"}
                                    {:label "Period",       :id "fd_68611_s"}
                                    {:label "Date",         :id "fd_68612_s"}
                                    {:label "Design Date",  :id "fd_68613_s"}
                                    {:label "Alteration Date", :id "fd_68614_s"}
                                    {:label "Restoration Date", :id "fd_68615_s"}
                                    {:label "Location",    :id "fd_68616_s"}
                                    {:label "Site",        :id "fd_68617_s"}
                                    {:label "Discovery Site", :id "fd_68618_s"}
                                    {:label "Material",       :id "fd_68619_s"}
                                    {:label "Technique",      :id "fd_68620_s"}
                                    {:label "Measurements",   :id "fd_68621_s"}
                                    {:label "Style Period",   :id "fd_68622_s"}
                                    {:label "Style",          :id "fd_68623_s"}
                                    {:label "Movement",       :id "fd_68624_s"}
                                    {:label "Group",          :id "fd_68625_s"}
                                    {:label "School",         :id "fd_68626_s"}
                                    {:label "Description",    :id "fd_68627_s"}
                                    {:label "Repository",     :id "fd_68628_s"}
                                    {:label "Accession Number", :id "fd_68629_s"}
                                    {:label "Related Item",   :id "fd_68630_s"}
                                    {:label "Relationship",   :id "fd_68631_s"}
                                    {:label "Subject",        :id "fd_68632_s"}
                                    {:label "Vocabulary",     :id "fd_68633_s"}
                                    {:label "Collection",     :id "fd_68634_s"}
                                    {:label "ID Number",      :id "fd_68635_s"}
                                    {:label "Source",         :id "fd_68636_s"}
                                    {:label "Rights",         :id "fd_68637_s"}]})

(defn service-wrap
  "A middleware wrapper for handling http client exceptions"
  ([client-func]
   (fn [url options]
     (try
       (let [response (client-func url options)
             _ (logger/info { :url url :asset-update-response {:status (get response :status) :body (get response :body)}})]
         response)
       (catch Exception e
         (logger/error "Failed in Forum service call" (.getMessage e) "Exception=" e)
         {:status 500
          :body "Error returned trying to call service.  Underlying service might not be available."})))))

;;   1) Login on ssimata using account owner for the Shared Shelf / Forum "Personal Collections" project_id
;; curl -i POST   http://sharedshelf.stage.artstor.org/account   -H 'Cache-Control: no-cache'   -H 'Postman-Token: 8c7eff95-887b-94e9-0ad4-5596fcdd1d46'   -H 'content-type: multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW'   -F email=qa001@artstor.org   -F password=artstor
(defn forum-services-login []
  "Logs into Forum Services"
  (let [url (str (forum-spec :forum-url) "/account")
        response (http/post url {:form-params {:email (forum-spec :forum-username) :password (forum-spec :forum-password)}})
        _ (logger/info { :forum-login-response {:status (response :status) :body (response :body)} })]
    (get response :cookies)))

;;   2) Make the call to reserve asset on imata
(defn reserve-imata-asset [filedata cookies]
  "Makes the call to reserve asset on imata"
  (let [url (str (forum-spec :forum-url) "/projects/" (forum-spec :forum-project-id) "/media-sources/reserve")
        form-data {:name (filedata :filename) :size (filedata :size)}
        response (http/post url {:cookies cookies :throw-exceptions false :form-params form-data :content-type :application/x-www-form-urlencoded})
        _ (logger/info { :imata-reserve-response {:status (response :status) :body (response :body)}})
        body (cheshire/parse-string (:body response) true)
        ] body))

;;  3) Using the info in returned json from step#2, Upload image on stor
(defn upload-image-to-storage [filedata cookies reserve-data artstor-user]
  "Using the info in returned json from call to /reserve, uploads image on stor"
  (let [url (reserve-data :location)
        destfile (io/file (str (.getParent (filedata :tempfile)) (filedata :filename)))
        copied (io/copy (filedata :tempfile) destfile)
        multiparts [{:name "username" :content (str (reserve-data :username))}
                    {:name "instituteId" :content (str (reserve-data :instituteId))}
                    {:name "projectId" :content (str (reserve-data :projectId))}
                    {:name "reportId" :content (str (reserve-data :reportId))}
                    {:name "imageSize" :content (str (reserve-data :imageSize))}
                    {:name "Name" :content (str (reserve-data :Name))}
                    {:name "File" :content destfile}]
        response (http/post url {:save-request? true :multipart multiparts :cookies cookies :throw-exceptions false })
        _ (logger/info { :stor-upload-response {:status (response :status) :body (response :body)}})
        deleted (io/delete-file destfile true)
        body (cheshire/parse-string (:body response) true)]
    (body :imageId)))

;;4) Assign reserved image on imata
(defn assign-uploaded-asset-on-imata [filedata cookies image-id]
  "After uploading, this assigns the reserved image on imata"
  (let [url (str (forum-spec :forum-url) "/projects/" (forum-spec :forum-project-id) "/assets")
        form-data {:filename (filedata :filename) :representation_id image-id}
        response (http/post url {:cookies cookies :throw-exceptions false :form-params form-data :content-type :application/x-www-form-urlencoded})
        body (cheshire/parse-string (:body response) true)]
    body))

;;6) Publish the image at Forum so that derivatives are generated
(defn publish-asset-at-forum [cookies image-id]
  "After updating owner, publish from Forum"
  (let [url (str (forum-spec :forum-url) "/_bulk/projects/" (forum-spec :forum-project-id) "/publish/assets")
        form-data {:asset_ids image-id :publishing_target_ids (forum-spec :pc-publishing-target-id)}
        response (http/post url {:cookies cookies :throw-exceptions false :form-params form-data :content-type :application/x-www-form-urlencoded})
        body (cheshire/parse-string (:body response) true)
        publishing-status ((first (body :assets)) :publishing_status)
        _ (logger/info {:publish-asset-at-forum publishing-status})
        status (get-in publishing-status [(keyword (str (forum-spec :pc-publishing-target-id))) :status])
        _ (logger/info { :publish-response-from-forum {:status (body :success) :assets (body :assets)}})]
    (and (body :success) (= status "Published"))))


;;5) Update Image owner info at Forum
(defn update-asset-owner-on-imata-initial-upload [profile-id asset-id cookies]
  "After uploading, change owner to whoever uploaded the image"
  (let [url (str (forum-spec :forum-url) "/assets/" asset-id "/updatemeta")
        created-by-response (http/post url {:cookies cookies :form-params {:profile_id profile-id :field_name "created_by"} :content-type :application/x-www-form-urlencoded})
        created-by-body (cheshire/parse-string (:body created-by-response) true)
        _ (logger/info {:update-asset-owner-in-created-by-field {:status (created-by-response :status) :body (created-by-response :body)}})
        updated-by-response (http/post url {:cookies cookies :form-params {:profile_id profile-id :field_name "updated_by"} :content-type :application/x-www-form-urlencoded})
        updated-by-body (cheshire/parse-string (:body updated-by-response) true)
        _ (logger/info {:update-asset-owner-in-updated-by-field {:status (updated-by-response :status) :body (updated-by-response :body)}})]
    (if (and (created-by-body :success) (updated-by-body :success))
      {:success true}
      {:success false})))

(defn massage-assign-result [assign-result profile-id]
  (if-let [data (first (assign-result :assets))]
    {:success true :assets [(assoc data :created_by profile-id
                                        :updated_by profile-id)]}))

(defn upload-image-to-personal-collection [filedata artstor-user]
  "Transfers the uploaded file from Binder over to Forum Services"
  (if-let [cookies (forum-services-login)]
    (let [reserve-data (reserve-imata-asset filedata cookies)
          image-id (upload-image-to-storage filedata cookies reserve-data artstor-user)
          assign-result (assign-uploaded-asset-on-imata filedata cookies image-id)
          ssid ((first (assign-result :assets)) :id)
          update-asset-owner (update-asset-owner-on-imata-initial-upload (artstor-user :profile-id) ssid cookies)
          updated-assign-result (if (and (update-asset-owner :success) (assign-result :success)) (massage-assign-result assign-result (artstor-user :profile-id)) assign-result)
          published (publish-asset-at-forum cookies ssid)]
      (do (logger/info { :message "Completed personal collection image upload" :forum-assign-result updated-assign-result
                        :forum-published-status published})
          (assoc updated-assign-result :forum-published-status published)))
    (do (logger/error { :artstor_error (str "Failed to authenticate to forum services" )})
        {:success false})))

;; update asset updated_by field after editing asset
(defn update-asset-owner-on-imata-after-editing [profile-id asset-id cookies]
  "After replacing/editing, change updatedBy to whoever modified the asset"
  (let [url (str (forum-spec :forum-url) "/assets/" asset-id "/updatemeta")
        updated-by-response (http/post url {:cookies cookies :form-params {:profile_id profile-id :field_name "updated_by"} :content-type :application/x-www-form-urlencoded})
        updated-by-body (cheshire/parse-string (:body updated-by-response) true)
        _ (logger/info {:update-asset-owner-in-updated-by-field {:status (updated-by-response :status) :body (updated-by-response :body)}})]
    (if (updated-by-body :success)
      {:success true}
      {:success false})))

;; update representation after uploading replacement media
(defn update-uploaded-asset-on-imata [filedata cookies asset-id image-id]
  "After uploading, this replaces the reserved image on imata"
  (let [url (str (forum-spec :forum-url) "/assets/" asset-id "/representation")
        form-data {:file_name (filedata :filename) :replace-media-field image-id :sourceId (forum-spec :forum-media-source-id) :sourceType "file"}
        response (http/post url {:cookies cookies :throw-exceptions false :form-params form-data :content-type :application/x-www-form-urlencoded})
        body (cheshire/parse-string (:body response) true)]
    body))

;; replace media file of a given asset in personal collecion
(defn update-media-in-personal-collection [filedata asset-id artstor-user]
  "Transfers the uploaded file from Binder over to Forum Services to update media for a given asset"
  (if-let [cookies (forum-services-login)]
    (let [reserve-data (reserve-imata-asset filedata cookies)
          image-id (upload-image-to-storage filedata cookies reserve-data artstor-user)
          update-media (update-uploaded-asset-on-imata filedata cookies asset-id image-id)
          update-asset-owner (update-asset-owner-on-imata-after-editing (artstor-user :profile-id) asset-id cookies)
          published (publish-asset-at-forum cookies asset-id)]
      (do (logger/info { :message "Completed personal collection media update"
                        :forum-published-status published})
          {:success true :forum-published-status published}))
    (do (logger/error { :artstor_error (str "Failed to authenticate to forum services" )})
        {:success false})))

(defn get-log-data [one-record]
  (let [doi (get one-record :doi)]
    {:arstorid (get one-record :artstorid)
     :ssid (if (nil? doi) nil (last (str/split  doi #"\.")))}))

;;Check ssids in SOLR
(defn check-ssids-in-SOLR [ssids]
  (let [url (util/build-service-url "search-service" "browse/")
        form {:form-params  {:limit         (count ssids)
                             :content_types ["art"]
                             :filter_query  [(str "ssid:(" (str/join " OR " ssids) ")")]
                             :additional_fields ["ssid"]}
              :content-type :json :as :json}
        response (http/post url form)]
    response))

(defn get-object-ids-from-ssids [ssids]
  "Get the object-ids for the given ssids"
  (let [response (check-ssids-in-SOLR [ssids])]
    (if (nil? response)
      (do (logger/error (str "ssids=" ssids ", unable to lookup in SOLR. Groups may not be properly modifed."))[]);note this should throw a 500
      (let [data (-> response :body :results)]
        (if (empty? data)
          (do (logger/warn (str "ssid=" (first ssids) ", is not yet indexed in SOLR"))[""])
          (do (logger/info (str "Map of ssid/arstorid=" (apply str (map get-log-data data))))
              (into [] (sort-by #(.indexOf ssids %) (map #(get % :artstorid) data)))))))))

;; validate owner before deleting PC image
(defn validate-owner-before-delete-pc-images [ssids artstor-user]
  (let [url (util/build-service-url "search-service" "browse/")
        form {:form-params  {:limit         (count ssids)
                             :content_types ["art"]
                             :filter_query  [(str "ssid:(" (str/join " OR " ssids) ") AND personalcollectionowner:("(get artstor-user :profile-id) ")")]
                             :additional_fields ["ssid"]}
              :content-type :json :as :json}
        response (http/post url form)]
    (if (nil? response)
      (do (logger/error (str "ssids=" ssids ", unable to lookup in SOLR"))[]);note this should throw a 500
      (let [data (-> response :body :results)]
        (if (empty? data)
          (do (logger/warn (str "ssid=" (apply str ssids) ", is not yet indexed in SOLR or ssids not owned by th user")))
          (let [accessable-ssids (map #(get (get % :additional_Fields) :ssid)  data)
                not-owner-of-ssids (set/difference (set ssids) (set accessable-ssids))]
            (if (not (empty? not-owner-of-ssids))
              (do (logger/warn (str "ssids=" (apply str not-owner-of-ssids) ", are not owned by the user. So user cannot delete them"))))
            accessable-ssids))))))

(defn delete-pc-images-from-forum [ssids cookies]
  "Delete personal collection image(s) from forum"
  (let [url (str (forum-spec :forum-url) "/_bulk/projects/" (forum-spec :forum-project-id) "/delete/assets")
        form-data {:asset_ids ssids}
        response (http/post url {:cookies cookies :throw-exceptions false :form-params form-data :content-type :application/x-www-form-urlencoded})
        body (cheshire/parse-string (:body response) true)]
    (if (body :success)
      (do (logger/info {:message "Completed deleting personal collection image(s) from forum" :ssids ssids})
          body)
      (do (logger/error {:artstor_error (str "Failed to delete personal collection image(s) from forum") :ssids ssids})
          {:success false}))))

(defn delete-pc-images-from-image-groups [object-ids artstor-user]
  "Delete personal collection image(s) from image groups"
  (let [web-token (auth/generate-web-token artstor-user)
        url (util/build-service-url "artstor-group-service" "api/v1/group/items/delete")
        response (http/put url {:headers {"web-token" web-token}
                                :throw-exceptions false
                                :content-type :application/json
                                :body (json/encode object-ids)})]
    (json/parse-string (response :body) true)))

(defn delete-images-from-personal-collection [ssids artstor-user]
  (let [ssids (validate-owner-before-delete-pc-images ssids artstor-user)]
    (if (not(empty? ssids))
      (if-let [cookies (forum-services-login)]
        (let [delete-from-forum (delete-pc-images-from-forum ssids cookies)]
          (if (delete-from-forum :success)
            (do (logger/info {:message "Completed deleting personal collection image(s)"})
                {:success true})
            (do (logger/error {:artstor_error (str "Failed to delete personal collection image(s)")})
                {:success false})))
        (do (logger/error { :artstor_error (str "Failed to authenticate to forum services" )})
            {:success false}))
      (do (logger/error {:artstor_error (str "User not authorized to delete personal collection image(s)")})
          {:success false}))))

(defn retrieve-column-definitions
  "Logs into forum and retrieves the definitions and ids for the provided project"
  ([] (retrieve-column-definitions (forum-spec :forum-project-id))) ;default is the personal collections project id
  ([project-id]
   (if-let [cookies (forum-services-login)]
     (let [url (str (forum-spec :forum-url) "/admin/projects/" project-id "/definitions")
           response (http/get url {:cookies cookies :throw-exceptions false})
           _ (logger/info { :project-definitions-response {:status (response :status) :body (response :body)}})
           body (cheshire/parse-string (:body response) true)]
       body))))

(defn validate-metadata-input
  "validates one ssids metadata object from the web service"
  [metadata]
  (if-let [metadata (if-not (empty? metadata) metadata nil)]
    (let [errors (map #(spec/explain-data ::schema/metadata_field_id (% :field_id)) metadata)]
      (if (not (every? #(nil? %) errors))
        (logger/error "Invalid metadata=" errors)
        true )))) ;;no errors

(defn magically-massage-pc-metadata
  "Converts the UI sensible structure into the magical yet mysterious forum data structure"
  [one-ssids-data]
  (let [massaged-fields (map #(hash-map (% :field_id) (% :value)) (one-ssids-data :metadata))]
    (into {:id (one-ssids-data :ssid)} massaged-fields)))

(defn update-image-metadata
  "Logs into forum and updates metadata for the image asset"
  ([ssid metadata artstor-user] (update-image-metadata ssid metadata artstor-user (forum-services-login)))
  ([ssid metadata artstor-user pcookies]
   (if-let [cookies pcookies]
     (if (validate-metadata-input (get metadata :metadata))
       (let [url (str (forum-spec :forum-url) "/projects/" (forum-spec :forum-project-id) "/assets/" ssid)
             assets (json/encode (magically-massage-pc-metadata metadata))
             wrapped-call (->> http/put service-wrap)
             response (wrapped-call url {:cookies cookies :form-params {:assets assets} :content-type :application/x-www-form-urlencoded})
             body (if (= 200 (get response :status)) (cheshire/parse-string (:body response) true) nil)]
         (if (get body :assets)
           (do
             (update-asset-owner-on-imata-after-editing (artstor-user :profile-id) ssid cookies)
             (publish-asset-at-forum cookies ssid)
             {:ssid ssid :success true :status (get response :status)})
           {:ssid ssid :success false :status (get response :status)}))
       (do (logger/error { :artstor_error (str "Failed to validate metadata" :ssid ssid)})
           {:ssid ssid :success false :status 400}))
     (do (logger/error { :artstor_error (str "Failed to authenticate to forum services" )})
         {:ssid ssid :success false :status 401}))))

(defn update-multiple-images-metadata
  "Loops through multiple ssids with metadata and calls update to forum"
  [all-ssids-metadata artstor-user]
  (let [cookies (forum-services-login)
        all-results (map #(update-image-metadata (% :ssid) % artstor-user cookies) all-ssids-metadata)]
    (if all-results
      {:success true :results all-results}
      {:success false})))