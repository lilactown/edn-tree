(ns town.lilac.edn-tree.demo
  (:require
   [helix.core :refer [$ defnc]]
   [helix.dom :as d]
   [helix.hooks :as hooks]
   [town.lilac.edn-tree :as edn-tree]
   ["react-dom/client" :as rdom]))


(defonce root nil)


(defn ^:dev/after-load render-app
  []
  (.render root
           ($ edn-tree/tree
              {:data {:foo #{"bar"}
                      :baz [1 2 3
                            {:arst {'neio (ex-info "foo" {})}}
                            (range 4 10)]}
               :initial-realize true
               :on-click #(prn "clicked" %2)
               :on-realize #(prn "realized" %2)
               :on-expand #(prn "expanded" %2)
               :on-focus #(prn "focus" %2)
               :on-blur #(prn "blur" %2)})))

(defn ^:export start
  []
  (set! root (rdom/createRoot (js/document.getElementById "app")))
  (render-app))