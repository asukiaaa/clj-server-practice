(ns front.view.users.show
  (:require ["react" :as react]
            ["react-router-dom" :as router]
            [front.route :as route]
            [front.view.common.wrapper.show404 :as wrapper.show404]
            [front.view.common.wrapper.fetching :as wrapper.fetching]
            [front.view.util :as util]
            [front.model.user :as model.user]))

(defn- page []
  (let [params (js->clj (router/useParams))
        navigate (router/useNavigate)
        id-user (get params "id_user")
        [user set-user] (react/useState)
        info-wrapper-fetching (wrapper.fetching/build-info #(react/useState))]
    (react/useEffect
     (fn []
       (wrapper.fetching/start info-wrapper-fetching)
       (model.user/fetch-by-id {:id id-user
                                :on-receive (fn [user errors]
                                              (set-user user)
                                              (wrapper.fetching/finished info-wrapper-fetching errors))})
       (fn []))
     #js [])
    (wrapper.fetching/wrapper
     {:info info-wrapper-fetching
      :renderer
      (if (empty? user)
        [:div "no data"]
        [:div
         [:> router/Link {:to route/users} "index"]
         " "
         [:> router/Link {:to (route/user-edit id-user)} "edit"]
         " "
         [:f> util/btn-confirm-delete
          {:message-confirm (model.user/build-confirmation-message-for-deleting user)
           :action-delete #(model.user/delete {:id (:id user)
                                               :on-receive (fn [] (navigate route/users))})}]
         [:table.table.table-sm
          [:thead
           [:tr
            [:th "key"]
            [:th "value"]]]
          [:tbody
           (for [key [:id :email :name :permission :created_at :updated_at]]
             [:tr {:key key}
              [:td key]
              [:td (get user key)]])]]])})))

(defn core []
  (wrapper.show404/wrapper
   {:permission wrapper.show404/permission-admin
    :page page}))
