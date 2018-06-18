(ns  artstor-collection-service-os.auth
  (:require [org.ithaka.clj-iacauth.token :refer [generate]]
            [compojure.api.meta :refer [restructure-param]]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [buddy.auth.accessrules :as baa]))

(defn logged-in? [req]
  (and (-> req :artstor-user-info :username) (not (-> req :artstor-user-info :default-user))))

(defn generate-web-token
  "Generate web token given profile id and ins id"
  ([user] (generate-web-token (user :profile-id) (user :institution-id) (user :default-user) (user :username)))
  ([profile-id ins-id] (generate-web-token profile-id ins-id true "default-user"))
  ([profile-id ins-id default-user user-name]
   (generate {:profile-id profile-id :institution-id ins-id :default-user default-user :username user-name})))

(defn access-error [req val]
  (forbidden val))

(defn wrap-rule [handler rule]
  (-> handler
      (baa/wrap-access-rules {:rules [{:pattern #".*"
                                       :handler rule}]
                              :on-error access-error})))

(defmethod restructure-param :auth-rules
  [_ rule acc]
  (update-in acc [:middleware] conj [wrap-rule rule]))

(defn cors-headers [{:keys [headers]}]
  {"Access-Control-Allow-Origin"  (get headers "origin" "*")
   "Access-Control-Allow-Headers" "Origin, Accept, Content-Type"
   "Access-Control-Allow-Methods" "GET, POST, PUT, DELETE, OPTIONS"
   "Access-Control-Allow-Credentials" "true"})

(defn preflight?
  "Returns true if the request is a preflight request"
  [request]
  (= (request :request-method) :options))

(defn add-cors
  "Allow requests from all origins - also check preflight"
  [handler]
  (fn [request]
    (if (preflight? request)
      {:status 200
       :headers (cors-headers request)
       :body "preflight complete"}
      (let [response (handler request)]
        (if (contains? #{:put :delete} (request :request-method))
          (update-in response [:headers]
                     merge (cors-headers request))
          response)))))