(ns town.lilac.edn-tree.demo
  (:require
   [clojure.edn :as edn]
   [helix.core :refer [$ defnc]]
   [helix.dom :as d]
   [helix.hooks :as hooks]
   [town.lilac.edn-tree :as edn-tree]
   ["react-dom/client" :as rdom]))


(defnc app 
  []
  (let [[edn-str set-edn-str] (hooks/use-state
                               #(str {:foo #{"bar"}
                                      :baz [1 2 3
                                            {:arst {'neio "foo"}}
                                            (range 4 10)]}))]
    (d/div
     (d/div
      ($ edn-tree/root
         {:data (edn/read-string edn-str)
          :on-click #(prn "clicked" %2)
          :on-realize #(prn "realized" %2)
          :on-expand #(prn "expanded" %2)
          :on-focus #(prn "focus" %2)
          :on-blur #(prn "blur" %2)}))
     (d/div 
      (d/textarea
       {:value edn-str
        :cols 80
        :rows 10
        :on-change #(set-edn-str (.. % -target -value))})))))


(defonce root nil)


(defn ^:dev/after-load render-app
  []
  (.render root ($ app)))

(defn ^:export start
  []
  (set! root (rdom/createRoot (js/document.getElementById "app")))
  (render-app))