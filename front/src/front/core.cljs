(ns front.core
  (:require [reagent.dom :as dom]
            [clojure.string :refer [escape]]
            [goog.string :as string]
            goog.string.format
            ["react" :as react]
            [re-graph.core :as re-graph]))

(re-graph/init {:http {:url "/graphql"
                       :supported-operations #{:query :mutate}}
                :ws nil})

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

(defn component-device-log [log col-settings]
  (let [[requested-to-open set-requested-to-open] (react/useState false)
        id (:id log)
        data (js->clj (.parse js/JSON (:data log)))
        window-width (. js/window -innerWidth)]
    [:<>
     [:tr
      [:td id]
      (for [col col-settings]
        (let [label (get col "label")
              bare-key (get col "key")
              first-key (if (string? bare-key) bare-key (first bare-key))
              target-field (when-not (empty? first-key)
                             (case first-key
                               "data" data
                               "created_at" (:created_at log)
                               "id" (:id log)
                               :else nil))
              json-key (when-not (string? bare-key) (rest bare-key))]
          [:td {:key label} (get-by-json-key target-field json-key)]))
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

(defn component-device-logs []
  (let [[logs set-logs] (react/useState nil)
        [count-total set-count-total] (react/useState nil)
        on-receive (fn [{:keys [data]}]
                     (set-logs (-> data :raw_device_logs :list))
                     (set-count-total (-> data :raw_device_logs :total)))
        query-params (read-params)
        query-str-renderer (:str-renderer query-params)
        query-str-where (:str-where query-params)
        query-str-order (:str-order query-params)
        default-str-renderer "[{\"label\": \"camera_id\", \"key\": [\"data\", \"camera_id\"]}, {\"label\": \"battery\", \"key\": [\"data\", \"readonly_state\", \"volt_battery\"]}, {\"label\": \"panel\", \"key\": [\"data\", \"readonly_state\", \"volt_panel\"]}]"
        ;; default-str-renderer "[{\"label\": \"camera_id\", \"key\": \"data\"}]"
        [str-renderer set-str-renderer] (react/useState (or query-str-renderer default-str-renderer))
        [str-draft-renderer set-str-draft-renderer] (react/useState str-renderer)
        ;; default-str-where "[{\"key\": \"created_at\", \"action\": \"gt\", \"value\": \"2022-03-10 00:00:00\"}]}]"
        default-str-where "[{\"key\": \"created_at\", \"action\": \"in-hours-24\"}]"
        [str-where set-str-where] (react/useState (or query-str-where default-str-where))
        [str-draft-where set-str-draft-where] (react/useState str-where)
        default-str-order "[{\"key\": [\"data\", \"camera_id\"], \"dir\": \"desc\"},{\"key\":\"created_at\",\"dir\":\"desc\"}]"
        [str-order set-str-order] (react/useState (or query-str-order default-str-order))
        [str-draft-order set-str-draft-order] (react/useState str-order)
        parse-setting #(.parse js/JSON str-renderer)
        parsed-setting (try (parse-setting) (catch js/Error _ nil))
        col-settings (when (not (nil? parsed-setting)) (js->clj parsed-setting))
        parse-error-renderer (when (nil? parsed-setting) (try (parse-setting) (catch js/Error e e)))
        parse-error-where (try (.parse js/JSON str-where) nil (catch js/Error e e))
        parse-error-order (try (.parse js/JSON str-order) nil (catch js/Error e e))
        update-device-logs
        (fn [str-where str-order]
          (println "update device logs")
          (let [query (goog.string.format "{ raw_device_logs(where: \"%s\", order: \"%s\") { total list { id created_at data } } }"
                                          (escape-str str-where) (escape-str str-order))]
            (println query)
            (re-graph/query query {} on-receive)))
        on-click-apply
        (fn []
          (let [is-different-renderer (not (= str-renderer str-draft-order))
                is-different-where (not (= str-where str-draft-where))
                is-different-order (not (= str-order str-draft-order))]
            (when is-different-renderer (set-str-renderer str-draft-renderer))
            (when is-different-where (set-str-where str-draft-where))
            (when is-different-order (set-str-order str-draft-order))
            (push-params {:str-renderer str-draft-renderer :str-where str-draft-where :str-order str-draft-order})
            (update-device-logs str-draft-where str-draft-order)
            #_(when (or is-different-order is-different-where)
                (update-device-logs str-draft-where str-draft-order))))
        on-pop-state
        (fn []
          #_(println "on pop")
          (let [query-params (read-params)
                query-str-renderer (or (:str-renderer query-params) default-str-renderer)
                query-str-where (or (:str-where query-params) default-str-where)
                query-str-order (or (:str-order query-params) default-str-order)]
            (set-str-renderer query-str-renderer)
            (set-str-draft-renderer query-str-renderer)
            (set-str-draft-order query-str-order)
            (set-str-order query-str-order)
            (set-str-draft-where query-str-where)
            (set-str-where query-str-where)
            (update-device-logs query-str-where query-str-order)))]
    (react/useEffect
     (fn []
       (.addEventListener js/window "popstate" on-pop-state)
       (update-device-logs str-where str-order)
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
     [:div.m-1 "renderer: " str-renderer]
     [:div.m-1 "where: " str-where]
     [:div.m-1 "order: " str-order]
     [:div.m-1 "total: " count-total]
     [:table.table.table-sm
      [:thead
       [:tr
        [:th "id"]
        (for [col col-settings]
          (let [label (get col "label")]
            [:th {:key label} label]))
        [:th "created_at"]
        [:th "actions"]]]
      [:tbody
       (for [log logs]
         [:<> {:key (:id log)}
          [:f> component-device-log log col-settings]])]]]))

(defn component-app []
  [:div
   [:f> component-device-logs]])

(dom/render [component-app] (.getElementById js/document "app"))
