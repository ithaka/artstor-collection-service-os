(ns artstor-collection-service-os.conf
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [amazonica.aws.s3 :as s3]
            [environ.core :refer [env]]))

(defn- keywordize [s]
  (-> (str/lower-case s)
      (str/replace "_" "-")
      (str/replace "." "-")
      (keyword)))

(defn- sanitize-key [k]
  (let [s (keywordize (name k))]
    (if-not (= k s) (println "Warning: bad keyword in configuration file key" k "has been corrected to" s))
    s))

(defn- sanitize [hm]
  (into {} (for [[k v] hm] [(sanitize-key k) (str v)])))

(defn- read-s3-env-file
  "Read configuration information from S3 configuration file. Configuration files should sport the .edn suffix."
  []
  (let [bucket (env :s3-bucket)
        key (env :s3-key)]
    (log/info (str "Reading configuration from " key))
    (if (s3/does-object-exist bucket key)
      (-> (s3/get-object bucket key)
          :input-stream
          (slurp)
          (edn/read-string)
          (sanitize)))))

(defonce ^{:doc "Service configuration information."}
         config-file
         (merge
           env
           (if (contains? env :s3config)
             (read-s3-env-file))))

