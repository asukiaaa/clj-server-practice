(ns front.view.log.index
  (:require ["react" :as react]
            [front.view.log.graph :as log.graph]
            [front.view.log.list :as log.list]
            [front.model.raw-device-log :as model.log]))

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

(def defaults
  {:str-renderer "[{\"key\": [\"data\", \"camera_id\"], \"badge\": [{\"text\": \"not wakeup\", \"when\": {\"key\": \"created_at\", \"action\": \"not-in-hours-24\"}}]}, {\"label\": \"battery\", \"key\": [\"data\", \"readonly_state\", \"volt_battery\"]}, {\"label\": \"panel\", \"key\": [\"data\", \"readonly_state\", \"volt_panel\"]}]"
   :str-where "[{\"key\": \"created_at\", \"action\": \"in-hours-24\"}]"
   :str-order "[{\"key\": [\"data\", \"camera_id\"], \"dir\": \"desc\"},{\"key\":\"created_at\",\"dir\":\"desc\"}]"
   :show-graph "false"
   :show-table "true"
   :limit "100"})

(defn parse-json [text]
  (let [parse-default-value #(.parse js/JSON text)
        parsed-js-value (try (parse-default-value) (catch js/Error _ nil))
        is-invalid-json (nil? parsed-js-value)
        parsed-value (when (not is-invalid-json) (js->clj parsed-js-value))
        error-message (when is-invalid-json (try (parse-default-value) (catch js/Error e e)))]
    [parsed-value error-message]))

(defn render-textarea-json [label str-json set-draft error-message]
  [:div
   [:div label]
   [:div (str error-message)]
   [:textarea.form-control.mb-1
    {:type :text :default-value str-json :key str-json
     :on-change (fn [e] (set-draft (-> e .-target .-value)))}]])

(defn render-textarea-with-info [label {:keys [default set-draft]} error-messate]
  (render-textarea-json label default set-draft error-messate))

(defn render-checkbox [label draft set-draft]
  [:span
   [:input.p-2
    {:id label
     :type "checkbox"
     :checked (= "true" draft)
     :on-change (fn [] (set-draft (if (= "true" draft) "false" "true")))}]
   [:label.p-2 {:for label} label]])

(defn render-checkbox-with-info [label {:keys [draft set-draft]}]
  (render-checkbox label draft set-draft))

(defn render-input [label {:keys [key draft set-draft]} {:keys [type wrapper-class]}]
  [:div {:class wrapper-class}
   [:div
    [:label {:for key} label]]
   [:input.form-control
    {:id label
     :value draft
     :type type
     :on-change (fn [e] (set-draft (-> e .-target .-value)))}]])

(defn get-param-str [key query-params]
  (or (get query-params key) (get defaults key)))

(defn build-state-info [key state-default state-draft]
  {:key key
   :default (first state-default)
   :set-default (second state-default)
   :draft (first state-draft)
   :set-draft (second state-draft)})

(defn set-all-val [val info]
  ((:set-draft info) val)
  ((:set-default info) val))

(defn get-default-as-bool [info]
  (= "true" (:default info)))

(defn core []
  (let [[logs set-logs] (react/useState)
        [logs-key-fetching set-logs-key-fetching] (react/useState)
        [logs-key-fetched set-logs-key-fetched] (react/useState)
        [total set-total] (react/useState)
        info-limit (build-state-info :limit (react/useState) (react/useState))
        info-str-renderer (build-state-info :str-renderer (react/useState) (react/useState))
        info-str-where (build-state-info :str-where (react/useState) (react/useState))
        info-str-order (build-state-info :str-order (react/useState) (react/useState))
        info-show-graph (build-state-info :show-graph (react/useState false) (react/useState false))
        info-show-table (build-state-info :show-table (react/useState true) (react/useState true))
        arr-info [info-limit info-str-renderer info-str-order info-str-where info-show-graph info-show-table]
        [show-config set-show-config] (react/useState false)
        [config-renderer parse-error-config-renderer] (parse-json (:default info-str-renderer))
        [_ parse-error-where] (parse-json (:default info-str-where))
        [_ parse-error-order] (parse-json (:default info-str-order))
        load-query-params #(let [query-params (read-params)]
                             (doseq [info arr-info]
                               (-> (get-param-str (:key info) query-params)
                                   (set-all-val info))))
        fetch-device-logs (fn [str-where str-order limit]
                            (let [logs-key (str str-where str-order limit)]
                              (set-logs-key-fetching logs-key)
                              (model.log/fetch-list
                               {:str-order str-order
                                :str-where str-where
                                :limit limit
                                :on-receive
                                (fn [logs total]
                                  (set-logs logs)
                                  (set-total total)
                                  (set-logs-key-fetched logs-key))})))
        on-click-apply (fn []
                         (->> (for [info arr-info] [(:key info) (:draft info)])
                              (into (sorted-map))
                              push-params)
                         (load-query-params))]
    (react/useEffect
     (fn []
       (load-query-params)
       (.addEventListener js/window "popstate" load-query-params)
       (fn [] ;; destructor
         (.removeEventListener js/window "popstate" load-query-params)))
     #js [])
    (let [str-where (:default info-str-where)
          str-order (:default info-str-order)
          limit (:default info-limit)]
      (react/useEffect
       (fn []
         (when-not (or (empty? str-where) (empty? str-order))
           #_(println "fetch logs" str-where str-order)
           (fetch-device-logs str-where str-order limit))
         (fn []))
       #js [str-where str-order limit]))
    [:div
     [:a.btn.btn-outline-primary.btn-sm.m-2 {:on-click #(set-show-config (not show-config))}
      (if show-config "hide config" "show config")]
     [:form.form-control {:style {:display (if show-config "block" "none")}}
      [render-textarea-with-info "renderer" info-str-renderer parse-error-config-renderer]
      [render-textarea-with-info "where" info-str-where parse-error-where]
      [render-textarea-with-info "order" info-str-order parse-error-order]
      [:div
       [render-input "limit" info-limit {:type "number"}]]
      [:div
       [render-checkbox-with-info "show graph" info-show-graph]
       [render-checkbox-with-info "show table" info-show-table]]
      [:a.btn.btn-outline-primary.btn-sm {:on-click on-click-apply} "apply"]]
     (if (or (empty? logs-key-fetched) (not (= logs-key-fetched logs-key-fetching)))
       [:div.m-1 "fetching"]
       [:div
        [:div.m-1 (str "requested " (str (:default info-limit)) " from " total)]
        (when (get-default-as-bool info-show-graph)
          [:f> log.graph/render-graphs logs-key-fetched logs config-renderer])
        (when (get-default-as-bool info-show-table)
          [:f> log.list/render-table-logs logs config-renderer])])]))
