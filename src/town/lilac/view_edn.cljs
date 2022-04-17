(ns town.lilac.view-edn
  (:require
   [helix.core :refer [$ defnc]]
   [helix.dom :as d]))


(declare view)


(defnc map-view
  [{:keys [data]}]
  (d/div
   (for [[k v] data]
     (d/div 
      {:key (str (hash k) (hash v))}
      ($ view {:data k})
      ($ view {:data v})))))


(defnc list-view
  [{:keys [data]}]
  (d/div
   (for [v data]
     (d/div
      {:key (hash v)}
      ($ view {:data v})))))


(defnc view
  [{:keys [data]}]
  (cond
    (map? data) ($ map-view {:data data})
    (coll? data) ($ list-view {:data data})
    :else (str data)))

(comment
  (require '["react-dom" :as rdom])
  (def root (rdom/createRoot (js/document.getElementById "app")))

  (.render root (d/div "hi"))
  
  (.render root ($ view {:data {:foo "bar" :baz [1 2 3 (range 4 10)]}}))
  )
  