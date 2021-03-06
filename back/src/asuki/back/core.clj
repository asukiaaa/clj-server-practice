(ns asuki.back.core
  (:gen-class)
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [ns-tracker.core :refer [ns-tracker]]
            [asuki.back.models.common :refer [init-db]]
            [asuki.back.route :refer [main] :rename {main routes-main}]))

(def modified-namespaces (ns-tracker "src"))

(def routes-target routes-main)

(defn watched-routes-target []
  (doseq [ns-sym (modified-namespaces)]
    (require ns-sym :reload))
  (route/expand-routes routes-target))

(defn start-server [port & args]
  (let [host "0.0.0.0"
        relodable (when-not (nil? args) (.contains args :relodable))]
    ;; TODO hande relodable
    (println (str "in start-server " host ":" port))
    (-> {::http/routes routes-target
         ::http/port port
         ::http/host host
         ::http/resource-path "public"
         ::http/type :jetty
         ::http/secure-headers {:content-security-policy-settings {:object-src "'none'"}}}
        (merge (if relodable
                 {::http/routes watched-routes-target
                  ::http/join? false}
                 nil))
        http/create-server
        http/start)
    (println (str "server starts on http://" host ":" port))))

(defn run-relodable
  [& args]
  (init-db)
  (start-server 3000 :relodable))

(defn -main
  [& args]
  (init-db)
  (let [port (if-let [str-port (System/getenv "PORT")]
               (read-string str-port)
               80)]
    (start-server port)))
