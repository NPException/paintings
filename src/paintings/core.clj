(ns paintings.core
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [hiccup.core :as hiccup]
            [clojure.core.memoize :as memo]
            [ring.adapter.jetty]))


(defn image-url-size [image]
  (let [data (select-keys image [:width :height])
        url (get-in image [:tiles 0 :url])]
    (assoc data :url url)))

(defn take-image-data [image-data object-number]
  (->> image-data
       (sort-by :name)
       (last)
       (image-url-size)
       (merge {:object-number object-number})))

(defn assoc-image [object-number]
  (-> (client/get (str "https://www.rijksmuseum.nl/api/nl/collection/" object-number "/tiles")
                  {:query-params {:key "14OGzuak"
                                  :format "json"}})
      (:body)
      (json/parse-string true)
      (:levels)
      (take-image-data object-number)))

(defn take-data [api-data]
  (->> api-data
       (map :objectNumber)
       (map assoc-image)))

(defn display-data []
  (-> (client/get "https://www.rijksmuseum.nl/api/nl/collection"
                  {:query-params {:key "14OGzuak"
                                  :format "json"
                                  :type "schilderij"
                                  :toppieces "True"}})
      :body
      (json/parse-string true)
      :artObjects
      (take-data)))


(def memo-display-data
  (memo/ttl display-data :ttl/threshold (* 60 60 1000)))    ;; memoize display-data result for 60 minutes (in milliseconds)


(defn create-image-element [{:keys [object-number width height url]}]
  [:div {:class "photocard"}
   [:img {:src url}]])


(defn convert-to-hiccup [data]
  (let [images  ( map create-image-element data)]
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:title " Most popular paintings from the Rijksmuseum "]
    [:link {:href "/css/styles.css", :rel "stylesheet"}]]
   [:body
    [:div {:class "scene"}
     [:div {:class "roll-camera"}
      [:div {:class "move-camera"}
       [:div {:class "wallpaper"}]
       [:div {:class "shelf top"}
        [:div {:class "face top"}]
        [:div {:class "face front"}
         (take 3 images)]
        [:div {:class "face back"}]
        [:dic {:class "face left"}]
        [:div {:class= "face bottom"}]]]]]]]))

(defroutes app
  (GET "/" [] (-> (memo-display-data)
                  (convert-to-hiccup)
                  (hiccup/html)))
  (route/resources "/")
  (GET "/favicon.ico" [] "")

  (defonce server (atom nil))

  (defn stop-server []
    (when @server
      (.stop @server)
      (reset! server nil)))

  (defn start-server []
    (stop-server)
    (reset! server
            (ring.adapter.jetty/run-jetty
             #'app {:port 8080, :join? false}))))
