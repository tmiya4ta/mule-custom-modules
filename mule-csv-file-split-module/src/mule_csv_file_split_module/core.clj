(ns mule-csv-file-split-module.core
  (:gen-class)
  (:import [java.nio.file Paths Path StandardOpenOption]
           [java.nio.channels FileChannel])
  (:require
   [silvur.nio]
   [clojure.tools.cli :refer (parse-opts)]
   [clojure.string :as str]
   [clojure.java.io :as io]))

(def cli-options [["-h" "--help" "This Help"]])

(defn usage [summary]
  (println)
  (println "Usage:")
  (println)
  (println summary)
  (println))

(defn -main [& args]
  (let [{:keys [options arguments summary errors]} (parse-opts
                                                    args
                                                    cli-options)]

    (if (:help options)
      (usage summary)
      (println "Hello World"))))
