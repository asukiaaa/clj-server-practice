(ns front.view.log.index
  (:require ["react" :as react]
            [reagent.core :as r]
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
   :show-graph false})

(defn core []
  (let [query-params (read-params)
        [logs set-logs] (react/useState [])
        [total set-total] (react/useState)
        [limit set-limit] (react/useState 100)
        [str-renderer set-str-renderer] (react/useState (or (:str-renderer query-params) (:str-renderer defaults)))
        [str-draft-renderer set-str-draft-renderer] (react/useState str-renderer)
        [str-where set-str-where] (react/useState (or (:str-where query-params) (:str-where defaults)))
        [str-draft-where set-str-draft-where] (react/useState str-where)
        [str-order set-str-order] (react/useState (or (:str-order query-params) (:str-order defaults)))
        [str-draft-order set-str-draft-order] (react/useState str-order)
        [show-graph set-show-graph] (react/useState (= "true" (or (:show-graph query-params) (:show-graph defaults))))
        [show-config set-show-config] (react/useState false)
        [draft-show-graph set-draft-show-graph] (react/useState show-graph)
        parse-renderer #(.parse js/JSON str-renderer)
        parsed-renderer (try (parse-renderer) (catch js/Error _ nil))
        config-renderer (when (not (nil? parsed-renderer)) (js->clj parsed-renderer))
        parse-error-renderer (when (nil? parsed-renderer) (try (parse-renderer) (catch js/Error e e)))
        parse-error-where (try (.parse js/JSON str-where) nil (catch js/Error e e))
        parse-error-order (try (.parse js/JSON str-order) nil (catch js/Error e e))
        fetch-device-logs (fn [str-where str-order]
                            (model.log/fetch-list {:str-order str-order
                                                   :str-where str-where
                                                   :limit limit
                                                   :on-receive (fn [logs total]
                                                                 (set-logs logs)
                                                                 (set-total total))}))
        on-click-apply
        (fn []
          (let [is-different-renderer (not (= str-renderer str-draft-order))
                is-different-where (not (= str-where str-draft-where))
                is-different-order (not (= str-order str-draft-order))]
            (when is-different-renderer (set-str-renderer str-draft-renderer))
            (when is-different-where (set-str-where str-draft-where))
            (when is-different-order (set-str-order str-draft-order))
            (when (not (= show-graph draft-show-graph)) (set-show-graph draft-show-graph))
            (push-params {:str-renderer str-draft-renderer
                          :str-where str-draft-where
                          :str-order str-draft-order
                          :show-graph (str draft-show-graph)})
            #_(fetch-logs {:str-where str-draft-where :str-order str-draft-order :on-receive on-receive})
            #_(when (or is-different-order is-different-where)
                (update-device-logs str-draft-where str-draft-order))))
        on-pop-state
        (fn []
          #_(println "on pop")
          (let [query-params (read-params)
                query-str-renderer (or (:str-renderer query-params) (:str-renderer defaults))
                query-str-where (or (:str-where query-params) (:str-where defaults))
                query-str-order (or (:str-order query-params) (:str-order defaults))
                query-show-graph (= "true" (or (:show-graph query-params) (:show-graph defaults)))]
            (set-str-renderer query-str-renderer)
            (set-str-draft-renderer query-str-renderer)
            (set-str-draft-order query-str-order)
            (set-str-order query-str-order)
            (set-str-draft-where query-str-where)
            (set-str-where query-str-where)
            (set-show-graph query-show-graph)
            (set-draft-show-graph query-show-graph)))]
    (react/useEffect
     (fn []
       (.addEventListener js/window "popstate" on-pop-state)
       (fetch-device-logs str-where str-order)
       (fn [] ;; destructor
         (.removeEventListener js/window "popstate" on-pop-state)))
     #js [])
    [:div
     [:h1 "device logs"]
     [:a.btn.btn-outline-primary.btn-sm.m-2 {:on-click #(set-show-config (not show-config))}
      (if show-config "hide config" "show config")]
     [:form.form-control {:style {:display (if show-config "block" "none")}}
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
      [:div
       [:input {:id "show-graph"
                :type "checkbox"
                :checked draft-show-graph
                :on-change (fn [] (set-draft-show-graph (not draft-show-graph)))}]
       [:label.p-2 {:for "show-graph"} "show graph"]]
      [:a.btn.btn-outline-primary.btn-sm {:on-click on-click-apply} "apply"]]
     (when show-graph
       [:f> log.graph/render-graphs (str str-where str-order config-renderer) logs config-renderer])
     [:div.m-1 "showing: " (count logs) ", total: " total]
     [:f> log.list/render-table-logs logs config-renderer]]))