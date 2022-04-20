(ns town.lilac.view-edn
  (:require
   [helix.core :refer [$ defnc]]
   [helix.dom :as d]
   [helix.hooks :as hooks]))


(declare view)


(defnc map-view
  [{:keys [data]}]
  (d/div
   (for [[k v] data]
     (d/div
      {:key (str (hash k) (hash v))
       :class ["town_lilac_view-edn__view"
               "town_lilac_view-edn__view-coll"]}
      ($ view {:data k :realized? true})
      ($ view {:data v})))))


(defnc list-view
  [{:keys [data]}]
  (let [[begin end] (if (vector? data) "[]" "()")
        [realized? set-realized] (hooks/use-state false)
        [expanded? set-expanded] (hooks/use-state false)]
    (d/div
     {:class ["town_lilac_view-edn__view"
              "town_lilac_view-edn__view-coll"
              (when expanded?
                "town_lilac_view-edn__view-coll__expanded")]
      :on-click (fn [e]
                  (.stopPropagation e)
                  (if realized?
                    (set-expanded not)
                    (set-realized true)))}
     begin
     (if realized?
       (for [v data]
         (d/div
          {:key (hash v)}
          ($ view {:data v})))
       "...")
     end)))


(defnc view
  [{:keys [data]}]
  (cond
    (map? data) ($ map-view {:data data})
    (coll? data) ($ list-view {:data data})
    :else (d/span 
           {:class "town_lilac_view-edn__view"}
           (str data))))

(comment
  ;; setup body
  (let [body (.. js/document -body)]
    (set! (.-style body) "")
    (.setAttribute body "class" "town_lilac_view-edn__body")
    (set! (.-innerHTML body) "<div id=\"app\">"))


  ;; reload CSS
  (do
    (doseq [link (js/document.querySelectorAll "link[rel=\"stylesheet\"]")]
      (.remove link))
    (let [head (.. js/document -head)]
      (.appendChild
       head
       (doto (js/document.createElement "link")
         (-> (.-href) 
             (set! (str "http://localhost:8080/view-edn.css?r=" (gensym))))
         (-> (.-rel) (set! "stylesheet"))))))


  (require '["react-dom/client" :as rdom])
  (def root (rdom/createRoot (js/document.getElementById "app")))

  (.render root (d/div "hi"))

  (.render root ($ view {:data {:foo "bar" :baz [1 2 3 (range 4 10)]}}))
  )
