(ns clj-watson.diplomat.github.advisory
  (:require
   [clj-http.client :as http]
   [clojure.java.io :as io]
   [clojure.string :as string]))

(def ^:private token (System/getenv "GITHUB_TOKEN"))

(def ^:private query-template (-> "github/query-package-vulnerabilities" io/resource slurp))

(def ^:private endpoint "https://api.github.com/graphql")

(defn ^:private build-query [package-name]
  (let [package-name (string/replace package-name #"/" ":")]
    (format query-template package-name)))

(defn vulnerabilities-by-package* [package-name]
  (if token
    (let [request-body {:form-params  {:query (build-query package-name)}
                        :headers      {"Authorization" (format "Bearer %s" token)}
                        :as           :json
                        :content-type :json}]
      (-> (http/post endpoint request-body)
          (get-in [:body :data :securityVulnerabilities :nodes])))
    (throw (Exception. "environment GITHUB_TOKEN variable not set."))))

(def vulnerabilities-by-package (memoize vulnerabilities-by-package*))
