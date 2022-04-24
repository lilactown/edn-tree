(ns town.lilac.view-edn
  (:require
   [helix.core :refer [$ <> defnc]]
   [helix.dom :as d]
   [helix.hooks :as hooks]))


(declare view)


(defnc map-entry-view
  [{:keys [k v]}]
  (let [[expanded? set-expanded] (hooks/use-state false)]
    (d/li
     {:role "treeitem"
      :aria-expanded expanded?
      :on-click #(do (.stopPropagation %)
                     (set-expanded not))}
     (d/ul
      {:class "town_lilac_view-edn__view"
       :role "group"}
      ($ view {:data k})
      ($ view {:data v})))))


(defnc map-view
  [{:keys [data initial-realized?]}]
  (let [[realized? set-realized] (hooks/use-state initial-realized?)
        [expanded? set-expanded] (hooks/use-state false)]
    (d/li
     {:role "treeitem"
      :aria-expanded expanded?
      :on-click #(do (.stopPropagation %)
                     (set-expanded not))}
     (d/ul
      {:class ["town_lilac_view-edn__view"]
       :role "group"
       :on-click #(do (.stopPropagation %)
                      (set-realized true))}
      (d/span {:class "town_lilac_view-edn__map_begin"} "{")
      (if realized?
        (for [[k v] data]
          ($ map-entry-view {:key (str (hash k) (hash v))
                             :k k
                             :v v}))
        "...")
      (d/span {:class "town_lilac_view-edn__map_end"} "}")))))


(defnc list-view
  [{:keys [data initial-realized? root?]}]
  (let [[begin end] (if (vector? data) "[]" "()")
        [realized? set-realized] (hooks/use-state initial-realized?)
        [expanded? set-expanded] (hooks/use-state false)]
    (d/li
      {:role "treeitem"
       :aria-expanded expanded?
       :on-click #(do (.stopPropagation %)
                      (set-expanded not))}
     (d/ul
      {:class ["town_lilac_view-edn__view"]
       :role "group"
       :on-click (fn [e]
                   (.stopPropagation e)
                   (set-realized true))}
      (d/span {:class "town_lilac_view-edn__list_begin"} begin)
      (if realized?
        (for [v data]
          ($ view {:key (hash v) :data v}))
        "...")
      (d/span {:class "town_lilac_view-edn__list_begin"} end)))))


(defnc view
  [{:keys [data root?]}]
  (cond
    (map? data) ($ map-view {:data data :root? root?})
    (coll? data) ($ list-view {:data data :root? root?})
    (string? data) (d/li
                    {:role "none"
                     :on-click #(.stopPropagation %)}
                    (d/span
                     {:class "town_lilac_view-edn__view"}
                     "\"" data "\""))
    :else (d/li
           {:role "none"
            :on-click #(.stopPropagation %)}
           (d/span
            {:class "town_lilac_view-edn__view"}
            (str data)))))


(defnc root-view
  [{:keys [data]}]
  (d/ul
   {:class "town_lilac_view-edn__view"
    :role "tree"}
   ($ view {:data data})))


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

  (.render root ($ root-view {:data {:foo "bar" :baz [1 2 3 (range 4 10)]}}))
  )
