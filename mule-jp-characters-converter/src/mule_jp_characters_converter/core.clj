(ns mule-jp-characters-converter.core
  (:gen-class )
  (:refer-clojure :exclude [error-handler])
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.cli :refer (parse-opts)]
            [silvur.datetime :refer (datetime datetime*)]
            [silvur.util :refer (json->edn edn->json)]
            [mulify
             [core :as c :refer [defapi]]
             [http :as http]
             [os :as os]
             [vm :as vm]
             [ee :as ee]
             [db :as db]
             [tls :as tls]
             [dataweave :as dw]
             [batch :as batch]
             [apikit :as ak]
             [apikit :as ak-odata]
             [jms :as jms]
             [wsc :as wsc]
             [anypoint-mq :as amq]
             [api-gateway :as gw]
             [generic]
             [file :as f]]
            [mulify.utils :as utils]))
(def mule-app
  (mulify.core/mule
   {:xmlns:core "http://www.mulesoft.org/schema/mule/core",
    :xmlns:http "http://www.mulesoft.org/schema/mule/http",
    :xmlns:file "http://www.mulesoft.org/schema/mule/file",
    :xmlns:documentation "http://www.mulesoft.org/schema/mule/documentation",
    :xmlns:ee "http://www.mulesoft.org/schema/mule/ee/core",
    :xsi:schemaLocation "http://www.mulesoft.org/schema/mule/core http://www.mulesoft.org/schema/mule/core/current/mule.xsd http://www.mulesoft.org/schema/mule/http http://www.mulesoft.org/schema/mule/http/current/mule-http.xsd http://www.mulesoft.org/schema/mule/file http://www.mulesoft.org/schema/mule/file/current/mule-file.xsd http://www.mulesoft.org/schema/mule/ee/core http://www.mulesoft.org/schema/mule/ee/core/current/mule-ee.xsd",
    :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"}
   (c/configuration-properties {:file "config.yaml"})
   (http/request-config*
    {:host "app-eapi-y1-shop-front.jp-e1.cloudhub.io", :type :https})
   (c/flow
    {:name "main-flow"}
    (http/listener* {:config-ref "https_listener_config", :path "*"})
    (c/logger* :INFO (dw/fx "attributes.requestPath"))
    (http/request
     {:config-ref "https_request_config",
      :path (dw/fx "attributes.requestPath")}
     (http/query-params (dw/fx "attributes.queryParams"))))))
(comment
 (mulify.utils/transpile
  mule-app
  :g
  "pse"
  :a
  "mule-jp-characters-converter"
  :v
  "0.0.1"
  :f
  "mule-jp-characters-converter.xml"))
