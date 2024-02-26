(ns front.views.logs.list
  (:require [clojure.string :refer [escape]]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            ["react" :as react]
            [goog.string]
            goog.string.format
            [re-graph.core :as re-graph]))

(defn push-params [query-params]
  ;; (println "push-url")
  (let [url js/window.location.href
        url-object (new js/URL url)
        url-search-params (.-searchParams url-object)]
    (doseq [[k v] query-params]
      #_(println "in for" k v)
      (.set url-search-params (name k) v))
    ;; (println "url is" url)
    ;; (println "url-object" url-object)
    ;; (println "url-search-params" url-search-params)
    (js/history.pushState nil nil (str "?" url-search-params))))

(defn read-params []
  ;; (println "read-params")
  (let [url js/window.location.href
        url-object (new js/URL url)
        url-search-params (.-searchParams url-object)]
    ;; (println "url is" url)
    ;; (println "url-object" url-object)
    ;; (println "url-search-params" url-search-params)
    ;; (println "cljs params" (js->clj url-search-params))
    (into {} (for [key (.keys url-search-params)]
               [(keyword key) (.get url-search-params key)]))))

(defn get-col-label [col]
  (or (get col "label")
      (let [key (get col "key")]
        (if (string? key) key (last key)))))

(defn get-by-json-key [data json-key]
  (when-not (nil? data)
    (cond
      (string? json-key) (get data json-key)
      (or (vector? json-key) (seq? json-key))
      (let [key (first json-key)
            new-data (get data key)
            new-json-key (rest json-key)]
        #_(println #_data json-key)
        #_(print key new-data new-json-key (type new-json-key))
        (if (= 1 (count json-key))
          new-data
          (get-by-json-key new-data new-json-key)))
      :else data)))

(defn get-col-val [record col-setting & data _]
  (let [data (or data (js->clj (.parse js/JSON (:data record))))
        bare-key (get col-setting "key")
        first-key (if (string? bare-key) bare-key (first bare-key))
        target-field (when-not (empty? first-key)
                       (case first-key
                         "data" data
                         "created_at" (:created_at record)
                         "id" (:id record)
                         :else nil))
        json-key (when-not (string? bare-key) (rest bare-key))]
    (get-by-json-key target-field json-key)))

(defn build-badge-item [record badge data id]
  (let [req-when (get badge "when")
        text (or (get badge "text") "no text")
        val (get-col-val record req-when data)
        action (get req-when "action")
        hours-action (when (string? action)
                       (-> (re-seq #"(in|not-in)-hours-(\d+)$" action)
                           first
                           #_((fn [x] (println "parsing hours-action" x) x))
                           rest))]
    ;; https://github.com/andrewmcveigh/cljs-time
    (when (cond
            (seq hours-action)
            (let [[in-or-not-in str-hours] hours-action
                  record-time (tf/parse (tf/formatter "YYYY-MM-dd HH:mm:ss.0") val)
                  ;; formatter (tf/formatter "YYYY-MM-dd HH:mm:ss")
                  threshold-time (t/minus (t/now) (t/hours (js/parseInt str-hours)))]
              ;; (println (tf/unparse formatter threshold-time) (tf/unparse formatter record-time))
              ;; (println str-hours (js/parseInt str-hours))
              (if (= in-or-not-in "in")
                (or (t/after? record-time threshold-time) (t/equal? record-time threshold-time))
                (t/before? record-time threshold-time))
              #_(println (tf/show-formatters))
              #_(println js/date-fns.addHours)
              #_(println dfns))
            :else false)
      [:span.badge.bg-secondary.mx-1 {:key (str "badge" text id)} text])))

(defn build-badge [record col-setting data id]
  (when-let [raw-badge (get col-setting "badge")]
    (if (map? raw-badge)
      (build-badge-item record raw-badge data id)
      (for [badge raw-badge] (build-badge-item record badge data id)))))

(defn component-device-log [log col-settings]
  (let [[requested-to-open set-requested-to-open] (react/useState false)
        id (:id log)
        data (js->clj (.parse js/JSON (:data log)))
        window-width (. js/window -innerWidth)]
    [:<>
     [:tr
      [:td id]
      (for [col col-settings]
        [:td {:key (get-col-label col)}
         (get-col-val log col data)
         (build-badge log col data id)])
      [:td (:created_at log)]
      [:td [:a ;; .btn.btn-outline-primary.btn-sm
            {:href "#"
             :on-click (fn [e]
                         (.preventDefault e)
                         (set-requested-to-open (not requested-to-open)))}
            (if requested-to-open "close" "open")]]]
     (when requested-to-open
       [:tr
        [:td {:colSpan (+ 3 (count col-settings))}
         [:pre {:style {:overflow :auto :max-width (- window-width 40)}}
          (.stringify js/JSON (.parse js/JSON (:data log)) nil 2)]]])]))

(defn escape-str [text]
  (when-not (nil? text)
    (escape text {\" "\\\""
                  \\ "\\\\"})))

(declare goog.string.format) ; to avoid error of clj-kond
(defn fetch-logs [{:keys [str-where str-order on-receive]}]
  (let [query (goog.string.format "{ raw_device_logs(where: \"%s\", order: \"%s\") { total list { id created_at data } } }"
                                  (escape-str str-where) (escape-str str-order))]
    (re-graph/query query {} on-receive)))

(defn log-list [{:keys [where order col-settings]}]
  (let [[total set-total] (react/useState 0)
        [logs set-logs] (react/useState [])
        on-receive (fn [{:keys [data]}]
                     #_(println "receive data for log-list" data)
                     (let [received-total (-> data :raw_device_logs :total)
                           received-logs  (-> data :raw_device_logs :list)]
                       (set-total received-total)
                       (set-logs received-logs)))]
    (react/useEffect
     (fn []
       (fetch-logs {:str-where where :str-order order :on-receive on-receive})
       (fn []))
     #js [where order])
    [:div
     [:div.m-1 "total: " total]
     [:table.table.table-sm
      [:thead
       [:tr
        [:th "id"]
        (for [col col-settings]
          (let [label (get-col-label col)]
            [:th {:key label} label]))
        [:th "created_at"]
        [:th "actions"]]]
      [:tbody
       (for [log logs]
         [:<> {:key (:id log)}
          [:f> component-device-log log col-settings]])]]]))

(def defaults
  {:str-renderer "[{\"key\": [\"data\", \"camera_id\"], \"badge\": [{\"text\": \"not wakeup\", \"when\": {\"key\": \"created_at\", \"action\": \"not-in-hours-24\"}}]}, {\"label\": \"battery\", \"key\": [\"data\", \"readonly_state\", \"volt_battery\"]}, {\"label\": \"panel\", \"key\": [\"data\", \"readonly_state\", \"volt_panel\"]}]"
   :str-where "[{\"key\": \"created_at\", \"action\": \"in-hours-24\"}]"
   :str-order "[{\"key\": [\"data\", \"camera_id\"], \"dir\": \"desc\"},{\"key\":\"created_at\",\"dir\":\"desc\"}]"})

(defn core []
  (let [query-params (read-params)
        query-str-renderer (:str-renderer query-params)
        query-str-where (:str-where query-params)
        query-str-order (:str-order query-params)
        [str-renderer set-str-renderer] (react/useState (or query-str-renderer (:str-renderer defaults)))
        [str-draft-renderer set-str-draft-renderer] (react/useState str-renderer)
        [str-where set-str-where] (react/useState (or query-str-where (:str-where defaults)))
        [str-draft-where set-str-draft-where] (react/useState str-where)
        [str-order set-str-order] (react/useState (or query-str-order (:str-order defaults)))
        [str-draft-order set-str-draft-order] (react/useState str-order)
        parse-setting #(.parse js/JSON str-renderer)
        parsed-setting (try (parse-setting) (catch js/Error _ nil))
        col-settings (when (not (nil? parsed-setting)) (js->clj parsed-setting))
        parse-error-renderer (when (nil? parsed-setting) (try (parse-setting) (catch js/Error e e)))
        parse-error-where (try (.parse js/JSON str-where) nil (catch js/Error e e))
        parse-error-order (try (.parse js/JSON str-order) nil (catch js/Error e e))
        on-click-apply
        (fn []
          (let [is-different-renderer (not (= str-renderer str-draft-order))
                is-different-where (not (= str-where str-draft-where))
                is-different-order (not (= str-order str-draft-order))]
            (when is-different-renderer (set-str-renderer str-draft-renderer))
            (when is-different-where (set-str-where str-draft-where))
            (when is-different-order (set-str-order str-draft-order))
            (push-params {:str-renderer str-draft-renderer :str-where str-draft-where :str-order str-draft-order})
            #_(fetch-logs {:str-where str-draft-where :str-order str-draft-order :on-receive on-receive})
            #_(when (or is-different-order is-different-where)
                (update-device-logs str-draft-where str-draft-order))))
        on-pop-state
        (fn []
          #_(println "on pop")
          (let [query-params (read-params)
                query-str-renderer (or (:str-renderer query-params) (:str-renderer defaults))
                query-str-where (or (:str-where query-params) (:str-where defaults))
                query-str-order (or (:str-order query-params) (:str-order defaults))]
            (set-str-renderer query-str-renderer)
            (set-str-draft-renderer query-str-renderer)
            (set-str-draft-order query-str-order)
            (set-str-order query-str-order)
            (set-str-draft-where query-str-where)
            (set-str-where query-str-where)))]
    (react/useEffect
     (fn []
       (.addEventListener js/window "popstate" on-pop-state)
       (fn [] ;; destructor
         (.removeEventListener js/window "popstate" on-pop-state)))
     #js [])
    [:div
     [:h1 "device logs"]
     [:form.form-control
      [:div "renderer"]
      [:div (str parse-error-renderer)]
      [:textarea.form-control.mb-1
       {:type :text :default-value str-renderer :key str-renderer
        :on-change (fn [e] (set-str-draft-renderer (-> e .-target .-value)))}]
      [:div "where"]
      [:div (str parse-error-where)]
      [:textarea.form-control.mb-1
       {:type :text :default-value str-where :key str-where
        :on-change (fn [e] (set-str-draft-where (-> e .-target .-value)))}]
      [:div "order"]
      [:div (str parse-error-order)]
      [:textarea.form-control.mb-1
       {:type :text :default-value str-order :key str-order
        :on-change (fn [e] (set-str-draft-order (-> e .-target .-value)))}]
      [:a.btn.btn-outline-primary.btn-sm {:on-click on-click-apply} "apply"]]
     [:f> log-list {:where str-where :order str-order :col-settings col-settings}]]))
