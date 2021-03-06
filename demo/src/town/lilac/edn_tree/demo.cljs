(ns town.lilac.edn-tree.demo
  (:require
   [clojure.edn :as edn]
   [cljs.pprint :refer [pprint]]
   [helix.core :refer [$ defcomponent defnc]]
   [helix.dom :as d]
   [helix.hooks :as hooks]
   [town.lilac.edn-tree :as edn-tree]
   ["react-dom/client" :as rdom]
   ["react" :as react]))


(defnc tree
  [{:keys [edn-str initial-realize initial-expanded]}]
  ($ edn-tree/root
     {:data (edn/read-string edn-str)
      :initial-expanded initial-expanded
      :initial-realize initial-realize
      :on-click #(prn "clicked" %2)
      :on-realize #(prn "realized" %2)
      :on-expand #(prn "expanded" %2)
      :on-focus #(prn "focus" %2)
      :on-blur #(prn "blur" %2)}))


(defcomponent error-boundary
  (constructor
   [this]
   (set! (.-state this) #js {:error nil}))
  
  (componentDidUpdate
   [this prev-props prev-state]
   (when (and (not= (.. prev-props -deps) (.. this -props -deps))
              (some? (.. this -state -error)))
     (.setState this #js {:error nil})))

  ^:static 
  (getDerivedStateFromError
   [this error]
   #js {:error error})
  
  (render
   [^js this]
   (if-not (.. this -state -error)
     (.. this -props -children)
     (d/code (pr-str (.. this -state -error))))))


(defnc app 
  []
  (let [[-edn-str set-edn-str] (hooks/use-state
                                #(with-out-str
                                   (pprint
                                    '{:source-paths ["src" "demo/src"]
                                      :dependencies [[lilactown/helix "0.1.5"]
                                                     [binaryage/devtools "1.0.6"]]
                                      :dev-http {8080 "resources"
                                                 9090 "demo/public"}
                                      :builds {:demo {:target :browser
                                                      :output-dir "demo/public/js"
                                                      :asset-path "/js"
                                                      :modules {:main {:entries [town.lilac.edn-tree.demo]
                                                                       :init-fn town.lilac.edn-tree.demo/start}}}}})))
        [realize set-realize] (hooks/use-state 1)
        [expanded set-expand-all] (hooks/use-state 0)
        edn-str (react/useDeferredValue -edn-str)]
    (d/div
     {:style {:padding 10}}
     (d/h1
         {:style {:padding-left 15
                  :padding-right 15
                  :margin 0}}
      "edn-tree demo")
     (d/div
      {:style {:padding 15
               :overflow "scroll"
               :white-space "nowrap"}}
      (d/h2
       {:style {:margin-top 0}}
       "Tree")
      (d/div
       {:style {:margin-bottom 10
                :display "flex"
                :gap "0 10px"}}
       (d/label
        (d/input {:type "checkbox"
                  :style {:margin-right 5}
                  :checked (true? realize)
                  :on-change #(if (.. % -target -checked)
                                (set-realize true)
                                (set-realize 1))})
        "Initial realize all?")
       (d/label
        (d/input {:type "checkbox"
                  :style {:margin-right 5}
                  :checked (true? expanded)
                  :on-change #(if (.. % -target -checked)
                                (set-expand-all true)
                                (set-expand-all 0))})
        "Initial expand all?"))
       ($ error-boundary
          {:deps [realize edn-str]}
          ($ tree {:key (str realize expanded)
                   :edn-str edn-str
                   :initial-expanded expanded
                   :initial-realize realize})))
     (d/div
      {:style {:padding 15}}
      (d/h2 {:style {:margin-top 0}} "Source")
      (d/textarea
       {:value -edn-str
        :cols 80
        :rows 15
        :on-change #(set-edn-str (.. % -target -value))})))))


(defonce root nil)


(defn ^:dev/after-load render-app
  []
  (.render root ($ app)))

(defn ^:export start
  []
  (set! root (rdom/createRoot (js/document.getElementById "app")))
  (render-app))