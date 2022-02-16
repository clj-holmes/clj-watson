(ns clj-watson.diplomat.github
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clj-http.client :as http]))

(def ^:private token (System/getenv "GITHUB_TOKEN"))

(def ^:private endpoint "https://api.github.com/graphql")

(defn ^:private build-query [package-name]
  (let [query (-> "github/query-package-vulnerabilities" io/resource slurp)
        package-name (string/replace package-name #"/" ":")]
    (format query package-name)))

(defn vulnerabilities-by-package [package-name]
  (let [request-body {:form-params  {:query (build-query package-name)}
                      :headers      {"Authorization" (format "Bearer %s" token)}
                      :as           :json
                      :content-type :json}]
    (-> (http/post endpoint request-body)
        (get-in [:body :data :securityVulnerabilities :nodes]))))

(comment
  (vulnerabilities-by-package 'org.postgresql/postgresql))