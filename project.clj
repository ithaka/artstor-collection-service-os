(defproject artstor-collection-service-os "1.0.0"
  :description "Artstor Collection Service"
  :url "http://www.artstor.org/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[clj-sequoia "3.0.3"]
                 [org.ithaka/clj-iacauth "1.1.3"]
                 [org.ithaka.artstor/captains-common "0.4.0"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.codec "0.1.0"]
                 [org.clojure/core.cache "0.6.5"]
                 [org.clojure/data.zip "0.1.2"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 [metosin/compojure-api "2.0.0-alpha6"]
                 [amazonica "0.3.90"]
                 [com.amazonaws/aws-java-sdk-core "1.11.98"]
                 [com.mchange/c3p0 "0.9.5-pre10"]
                 [compojure "1.6.0"]
                 [commons-codec/commons-codec "1.4"]
                 [clojure-future-spec "1.9.0-alpha15"]
                 [clj-time "0.14.0"]
                 [clj-http "2.3.0"]
                 [cheshire "5.7.0"]
                 [ring "1.6.2"]
                 [ring-logger "0.7.7"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-codec "1.0.1"]
                 [ring/ring-mock "0.3.0"]
                 [hiccup "1.0.5"]
                 [yesql "0.5.3"]
                 [environ "1.1.0"]
                 [prismatic/schema "1.1.3"]
                 [buddy/buddy-auth "1.4.1"]
                 [peridot "0.5.0"]]

  :profiles {:test {:env {:artstor-ccollection-db-url "jdbc:h2:/tmp/artstor-ccollection-test.db"}
                    :dependencies [[szew/h2 "0.1.1"]
                                   [org.clojure/tools.nrepl "0.2.12"]
                                   [org.clojure/test.check "0.9.0"]
                                   [ragtime "0.6.3"]]}}

  :ring {:handler artstor-collection-service-os.core/app :port 8080}
  :plugins [[lein-ring "0.10.0"]
            [lein-environ "1.1.0"]
            [lein-marginalia "0.9.0"]])
