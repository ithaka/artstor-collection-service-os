(ns artstor-collection-service-os.schema
  (:require [schema.core :as s]
            [clojure.spec :as spec]))

(s/defschema RequestStatus
             {:success s/Bool :message s/Str})

(s/defschema ArtstorUser
             {:profile-id s/Str
              :institution-id s/Str
              :default-user s/Bool
              :username  s/Str})

(s/defschema PC-Metadata [{ :ssid s/Num,
                           :metadata [{:field_id s/Str,
                                       :value s/Str }]}])

(spec/def ::metadata_field_id #{"fd_68602_s" "fd_68603_s" "fd_68604_s" "fd_68605_s" "fd_68606_s" "fd_68607_s" "fd_68608_s"
                                "fd_68609_s" "fd_68610_s" "fd_68611_s" "fd_68612_s" "fd_68613_s" "fd_68614_s" "fd_68615_s"
                                "fd_68616_s" "fd_68617_s" "fd_68618_s" "fd_68619_s" "fd_68620_s" "fd_68621_s" "fd_68622_s"
                                "fd_68623_s" "fd_68624_s" "fd_68625_s" "fd_68626_s" "fd_68627_s" "fd_68628_s" "fd_68629_s"
                                "fd_68630_s" "fd_68631_s" "fd_68632_s" "fd_68633_s" "fd_68634_s" "fd_68635_s" "fd_68636_s"
                                "fd_68637_s"})