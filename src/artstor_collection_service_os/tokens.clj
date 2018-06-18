(ns artstor-collection-service-os.tokens
  (:require [artstor-collection-service-os.util :as util]
            [artstor-collection-service-os.conf :refer [config-file]]
            [cheshire.core :as cjson]
            [environ.core :refer [env]]
            [clj-http.client :as http]))

(def external-account-id (config-file :artstor-external-account-id))

(defn get-artstor-licenses [all-licenses]
  (if (some #(>  (.indexOf ["Artstor" "ArtstorTrial" "Artstor_Institution_Collection" "Artstor_User_Collection"] (% :licSubType)) -1) (all-licenses :licenses))
    (all-licenses :entExtId)))

(defn legacy? [id]
  (< (count (str id)) 12))

(defn get-user-ext-accountid-from-profileid [profile-id]
  (let [url (util/build-service-url "iac-service" (str "profile/" profile-id))
        response (http/get url {})]
    (if (empty? response)
      {:status false :message "Unknown error"}
      ((cjson/parse-string (response :body) true) :userId))))

(defn get-inst-ext-accountid-from-legacyid [inst-id]
  (let [institution-id (if (legacy? inst-id) (+ 1100000000 inst-id) inst-id)
        url (util/build-service-url "iac-service" (str "account/byLegacy/" institution-id))
        response (http/get url {})]
    (if (empty? response)
      {:status false :message "Unknown error"}
      ((cjson/parse-string (response :body) true) :id))))

(defn build-query-params-to-get-licenses [artstor-user-info]
  (let [inst-id (artstor-user-info :institution-id)
        inst-ext-id (get-inst-ext-accountid-from-legacyid inst-id)
        prof-id (artstor-user-info :profile-id)
        user-acct-id (get-user-ext-accountid-from-profileid prof-id)]
    (if (artstor-user-info :default-user)
      {:accountIds (str inst-ext-id "," external-account-id) :idType "externalId" :includeFlag false}
      {:accountIds (str inst-ext-id "," user-acct-id "," external-account-id) :idType "externalId" :includeFlag false})))

(defn get-all-licenses-from-artstor-user-info [artstor-user-info]
  (let [params (build-query-params-to-get-licenses artstor-user-info)
        url (util/build-service-url "iac-service" "license/byAccount/")
        response (http/get url {:query-params params})]
    (if (empty? response)
      {:status false :message "Unknown error"}
      ((cjson/parse-string (response :body) true) :results))))

(defn get-eme-tokens-from-artstor-user-info [artstor-user-info]
  (let [all-licenses (get-all-licenses-from-artstor-user-info artstor-user-info)
        artstor-licenses (filter get-artstor-licenses all-licenses)]
    (into [] (map #(get % :entExtId) artstor-licenses))))

(defn get-eme-tokens [request]
  (get-eme-tokens-from-artstor-user-info (request :artstor-user-info)))
