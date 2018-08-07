(ns artstor-collection-service-os.conf-test
  (:require [clojure.test :refer :all]
            [environ.core :refer [env]]
            [clojure.java.io :as io]
            [amazonica.aws.s3 :as s3]
            [clojure.string :as string])
  (:import (java.io ByteArrayInputStream)))

; Utterly plagurized from the awesome environ.core by weavejester.  https://github.com/weavejester/environ
;
; We simple add the ability to read environment information from an S3 object.

(defn refresh-ns []
  (remove-ns 'artstor-collection-service-os.conf)
  (dosync (alter @#'clojure.core/*loaded-libs* disj 'artstor-collection-service-os.conf))
  (require 'artstor-collection-service-os.conf))

(defn refresh-config []
  (refresh-ns)
  (var-get (find-var 'artstor-collection-service-os.conf/config-file)))

(deftest test-conf
  (testing "Reading configuration from s3 file based on filename provided in the environment"
    (with-redefs [s3/does-object-exist (fn [bucket file]
                                         (is (= bucket "test123"))
                                         (is (string/ends-with? file "artstor-collection-service-os.edn"))
                                         true)
                  s3/get-object (fn [& opts] {:input-stream (ByteArrayInputStream.
                                                              (.getBytes (prn-str {:my-config-var "hello"})))})
                  env {:s3config true :s3-bucket "test123" :s3-key "test123/artstor-collection-service-os.edn"}]
      (let [config (refresh-config)]
        (is (= (:my-config-var config) "hello")))))
  (testing "Reading configuration is skipped if the file doesn't exist on S3"
    (with-redefs [s3/does-object-exist (fn [bucket file]
                                         (is (= bucket "test123"))
                                         (is (string/ends-with? file "bar.edn"))
                                         false)
                  s3/get-object (fn [& opts] (throw "get-object should not be called"))
                  env {:s3-config true :s3-bucket "test123" :s3-key "test123/bar.edn" }]
      (let [config (refresh-config)]
        (is (= config env)))))
  (testing "Skips reading config if no :s3config key found in environment"
    (with-redefs [s3/does-object-exist (fn [] (throw "does-object-exist should not be called"))
                  s3/get-object (fn [& opts] (throw "get-object should not be called"))
                  env {}]
      (let [config (refresh-config)]
        (is (= {} config))))))


