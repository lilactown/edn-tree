(ns town.lilac.edn-tree
  (:require
   [helix.core :refer [$ defnc defhook]]
   [helix.dom :as d :refer [$d]]
   [helix.hooks :as hooks]))


(def focus-tree-context
  (helix.core/create-context nil))


(defn- index-of
  [f coll]
  (loop [i 0
         coll coll]
    (when-let [hd (first coll)]
      (if (f hd)
        i
        (recur
         (inc i)
         (rest coll))))))


(comment
  (index-of even? [1 2 3])
  (nth [1 2 3] 1))


(defn- compare-pos
  [e1 e2]
  ;; see docs for compareDocumentPosition
  (condp (comp pos? bit-and) (.compareDocumentPosition e1 e2)
    16 -1 ; e1 contains e2
    8 1 ; e1 is contained in e2
    4 -1 ; e1 before e2
    2 1 ; e1 after e2
    0))


(defn focus-reducer
  "state :: {:focus #helix/ref [DOMElement]
             :nodes [{:level 0
                      :ref #helix/ref [DOMElement]}]}
   action :: {:type :add | :remove | ,,,
              :value #helix/ref [DOMElement]}"
  [state action]
  (case (:type action)
    :add (let [{:keys [level value] :or {level 0}} action]
           (update state :nodes
                   (fn [nodes]
                     (sort-by
                      (comp deref :ref)
                      compare-pos
                      (conj nodes {:level level
                                   :ref value})))))
    :remove (let [{:keys [value]} action]
              (update state :nodes
                      (fn [nodes]
                        (remove #(= value (:ref %)) nodes))))
    :set-focus (let [{:keys [value]} action]
                 (assoc state :focus value))
    :move-focus-prev
    (let [index (index-of #(= (:focus state) (:ref %))
                          (:nodes state))
          next-index (if (<= 0 (dec index)) (dec index) index)
          next-ref (:ref (nth (:nodes state) next-index))]
      (assoc state :focus next-ref))
    :move-focus-next
    (let [index (index-of #(= (:focus state) (:ref %))
                          (:nodes state))
          next-index (if (< (inc index) (count (:nodes state)))
                       (inc index)
                       index)
          next-ref (:ref (nth (:nodes state) next-index))]
      (assoc state :focus next-ref))))


(defhook use-focus-tree
  [auto-focus?]
  (let [[tree dispatch] (hooks/use-reducer
                         focus-reducer
                         {:focus nil
                          :level 0
                          :nodes []})
        initial-focus? (hooks/use-ref true)]
    (hooks/use-effect
     [(:focus tree)]
     (when-let [el (and (:focus tree) 
                        ;; either it's not the initial focus,
                        ;; or auto-focus? is true
                        (or (not @initial-focus?)
                            auto-focus?)
                        @(:focus tree))]
       (.focus el))
     (when (and @initial-focus?
                (some? (:focus tree))
                (some? @(:focus tree)))
       (reset! initial-focus? false)))
    [tree dispatch]))


(defhook use-focus-leaf
  ([ref] (use-focus-leaf ref {}))
  ([ref {:keys [initial-focus?]}]
   (let [[tree dispatch] (hooks/use-context focus-tree-context)
         {:keys [focus level]} tree]
     (hooks/use-effect
      [ref]
      (dispatch {:type :add :value ref})
      (when initial-focus?
        (dispatch {:type :set-focus :value ref}))
      #(dispatch {:type :remove :value ref}))
     {:context [tree
                (fn dispatch-with-level
                  [action]
                  (dispatch (assoc action :level (inc level))))]
      :tabindex (if (= focus ref) "0" "-1")
      :handle-click (fn set-focus
                      [_e]
                      (dispatch {:type :set-focus
                                 :value ref}))
      :handle-key-down (fn move-focus
                         [e]
                         (case (.-keyCode e)
                           (37 38) (dispatch {:type :move-focus-prev})
                           (39 40) (dispatch {:type :move-focus-next})
                           nil))})))


(defn- maybe-call
  [f & args]
  (when (some? f)
    (apply f args)))


(declare view)


(defnc map-entry-view
  [{:keys [k v initial-realize treeitem-as
           on-click on-realize on-expand on-focus on-blur
           initial-focus?]}]
  (let [[expanded? set-expanded] (hooks/use-state false)
        focus-ref (hooks/use-ref nil)
        {:keys [context
                tabindex
                handle-click
                handle-key-down]} (use-focus-leaf focus-ref)]
    ($d treeitem-as
        {:role "treeitem"
         :aria-expanded expanded?
         :tab-index tabindex
         :ref focus-ref
         :on-focus (hooks/use-callback
                    [on-focus k v]
                    #(do (.stopPropagation %)
                         (maybe-call on-focus % [k v])))
         :on-blur (hooks/use-callback
                   [on-blur k v]
                   #(do (.stopPropagation %)
                        (maybe-call on-blur % [k v])))
         :on-key-down (hooks/use-callback
                       :auto-deps
                       #(do (.stopPropagation %)
                            (handle-key-down %)
                            (when (= 13 (.-keyCode %)) ; enter
                              (set-expanded not)
                              (when (not expanded?)
                                (maybe-call on-expand % [k v])))))
         :on-click (hooks/use-callback
                    :auto-deps
                    #(do (.stopPropagation %)
                         (handle-click %)
                         (set-expanded not)
                         (maybe-call on-click % [k v])
                         (when (not expanded?)
                           (maybe-call on-expand % [k v]))))}
        (helix.core/provider
         {:context focus-tree-context
          :value context}
         (d/ul
          {:class ["town_lilac_edn-tree__view"
                   "town_lilac_edn-tree__map-entry"]
           :role "group"}
          ($ view {:data k
                   :initial-realize initial-realize
                   :treeitem-as treeitem-as
                   :on-click on-click
                   :on-realize on-realize
                   :on-expand on-expand
                   :on-focus on-focus
                   :on-blur on-blur})
          ($ view {:data v
                   :initial-realize initial-realize
                   :treeitem-as treeitem-as
                   :on-click on-click
                   :on-realize on-realize
                   :on-expand on-expand
                   :on-focus on-focus
                   :on-blur on-blur}))))))


(defnc map-view
  [{:keys [data initial-realize treeitem-as
           on-click on-realize on-expand on-focus on-blur
           initial-focus?]}]
  (let [initial-realized? (or (true? initial-realize)
                              (pos? initial-realize))
        [realized? set-realized] (hooks/use-state initial-realized?)
        [expanded? set-expanded] (hooks/use-state false)
        focus-ref (hooks/use-ref nil)
        {:keys [context
                tabindex
                handle-click
                handle-key-down]} (use-focus-leaf
                                   focus-ref
                                   {:initial-focus? initial-focus?})]
    ($d treeitem-as
        {:role "treeitem"
         :aria-expanded expanded?
         :tab-index tabindex
         :ref focus-ref
         :class (when (or (not realized?) (> 2 (count data)))
                  "town_lilac_edn-tree__no-expand")
         :on-focus (hooks/use-callback
                    [on-focus data]
                    #(do (.stopPropagation %)
                         (maybe-call on-focus % data)))
         :on-blur (hooks/use-callback
                   [on-blur data]
                   #(do (.stopPropagation %)
                        (maybe-call on-blur % data)))
         :on-key-down (hooks/use-callback
                       :auto-deps
                       #(do (.stopPropagation %)
                            (handle-key-down %)
                            (case (.-keyCode %)
                         ;; enter
                              13 (do (set-realized true)
                                     (set-expanded not)
                                     (when (not expanded?)
                                       (maybe-call on-expand % data))
                                     (when (not realized?)
                                       (maybe-call on-realize % data)))
                              nil)))
         :on-click (hooks/use-callback
                    :auto-deps
                    #(do (.stopPropagation %)
                         (handle-click %)
                         (set-expanded not)
                         (maybe-call on-click % data)
                         (when (not expanded?)
                           (maybe-call on-expand % data))))}
        (helix.core/provider
         {:context focus-tree-context
          :value context}
         (d/ul
          {:class ["town_lilac_edn-tree__view"
                   "town_lilac_edn-tree__map-view"]
           :role "group"
           :on-click #(do (.stopPropagation %)
                          (handle-click %)
                          (set-realized true)
                          (maybe-call on-click % data)
                          (when (not realized?)
                            (maybe-call on-realize % data)))}
          (d/span {:class "town_lilac_edn-tree__map_begin"} "{")
          (if realized?
            (for [[k v] data]
              ($ map-entry-view {:key (str (hash k) (hash v))
                                 :initial-realize (if (boolean? initial-realize)
                                                    initial-realize
                                                    (dec initial-realize))
                                 :k k
                                 :v v
                                 :treeitem-as treeitem-as
                                 :on-click on-click
                                 :on-realize on-realize
                                 :on-expand on-expand
                                 :on-focus on-focus
                                 :on-blur on-blur}))
            "...")
          (d/span {:class "town_lilac_edn-tree__map_end"} "}"))))))


(defnc list-view
  [{:keys [data initial-realize treeitem-as
           on-click on-realize on-expand on-focus on-blur
           initial-focus?]}]
  (let [[begin end] (cond
                      (vector? data) "[]"
                      (set? data) ["#{" "}"]
                      :else "()")
        initial-realized? (or (true? initial-realize)
                              (pos? initial-realize))
        [realized? set-realized] (hooks/use-state initial-realized?)
        [expanded? set-expanded] (hooks/use-state false)
        focus-ref (hooks/use-ref nil)
        {:keys [context
                tabindex
                handle-click
                handle-key-down]} (use-focus-leaf
                                   focus-ref
                                   {:initial-focus? initial-focus?})]
    ($d treeitem-as
        {:role "treeitem"
         :aria-expanded expanded?
         :tab-index tabindex
         :ref focus-ref
         :class (when (or (not realized?) (> 2 (count data)))
                  "town_lilac_edn-tree__no-expand")
         :on-focus (hooks/use-callback
                    [on-focus data]
                    #(do (.stopPropagation %)
                         (maybe-call on-focus % data)))
         :on-blur (hooks/use-callback
                   [on-blur data]
                   #(do (.stopPropagation %)
                        (maybe-call on-blur % data)))
         :on-key-down (hooks/use-callback
                       :auto-deps
                       #(do (.stopPropagation %)
                            (handle-key-down %)
                            (case (.-keyCode %)
                             ;; enter
                              13 (do (set-realized true)
                                     (set-expanded not)
                                     (when (not expanded?)
                                       (maybe-call on-expand % data))
                                     (when (not realized?)
                                       (maybe-call on-realize % data)))
                              nil)))
         :on-click (hooks/use-callback
                    :auto-deps
                    #(do (.stopPropagation %)
                         (handle-click %)
                         (set-expanded not)
                         (maybe-call on-click % data)
                         (when (not expanded?)
                           (maybe-call on-expand % data))))}
        (helix.core/provider
         {:context focus-tree-context
          :value context}
         (d/ul
          {:class ["town_lilac_edn-tree__view"
                   (if (set? data)
                     "town_lilac_edn-tree__set-view"
                     "town_lilac_edn-tree__list-view")]
           :role "group"
           :on-click (fn [e]
                       (.stopPropagation e)
                       (handle-click e)
                       (set-realized true)
                       (maybe-call on-click e data)
                       (when (not realized?)
                         (maybe-call on-realize e data)))}
          (d/span {:class "town_lilac_edn-tree__list_begin"} begin)
          (if realized?
            (for [v data]
              ($ view {:key (hash v)
                       :initial-realize (if (boolean? initial-realize)
                                          initial-realize
                                          (dec initial-realize))
                       :data v
                       :treeitem-as treeitem-as
                       :on-click on-click
                       :on-realize on-realize
                       :on-expand on-expand
                       :on-focus on-focus
                       :on-blur on-blur}))
            "...")
          (d/span {:class "town_lilac_edn-tree__list_end"} end))))))


(defnc view
  [{:keys [data initial-realize treeitem-as
           on-click on-realize on-expand on-focus on-blur
           initial-focus?]}]
  (cond
    (map? data) ($ map-view {:data data
                             :initial-focus? initial-focus?
                             :initial-realize initial-realize
                             :treeitem-as treeitem-as
                             :on-click on-click
                             :on-realize on-realize
                             :on-expand on-expand
                             :on-focus on-focus
                             :on-blur on-blur})
    (coll? data) ($ list-view {:data data
                               :initial-focus? initial-focus?
                               :initial-realize initial-realize
                               :treeitem-as treeitem-as
                               :on-click on-click
                               :on-realize on-realize
                               :on-expand on-expand
                               :on-focus on-focus
                               :on-blur on-blur})
    (string? data) ($d treeitem-as
                       {:role "none"
                        ;:tabindex "-1"
                        :on-click #(do (.stopPropagation %)
                                       (maybe-call on-click % data))}
                       (d/span
                        {:class "town_lilac_edn-tree__view"}
                        "\"" data "\""))
    :else ($d treeitem-as
              {:role "none"
               ;:tabindex "-1"
               :on-click #(do (.stopPropagation %)
                              (maybe-call on-click % data))}
              (d/span
               {:class "town_lilac_edn-tree__view"}
               (str data)))))


(defnc tree
  [{:keys [class data initial-realize treeitem-as
           on-click on-realize on-expand on-focus on-blur
           auto-focus?]
    :or {initial-realize 1
         treeitem-as "li"
         auto-focus? false}}]
  (helix.core/provider
   {:context focus-tree-context
    :value (use-focus-tree auto-focus?)}
   (d/ul
    {:role "tree"
     :class [class "town_lilac_edn-tree__root"]}
    ($ view {:data data
             :initial-focus? true
             :treeitem-as treeitem-as
             :initial-realize initial-realize
             :on-click on-click
             :on-realize on-realize
             :on-expand on-expand
             :on-focus on-focus
             :on-blur on-blur}))))


(comment
  ;; setup body
  (let [body (.. js/document -body)]
    (set! (.-style body) "")
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
             (set! (str "http://localhost:8080/edn-tree.css?r=" (gensym))))
         (-> (.-rel) (set! "stylesheet"))))))


  (require '["react-dom/client" :as rdom])
  (def root (rdom/createRoot (js/document.getElementById "app")))

  (.render root (d/div "hi"))

  (.render root ($ tree {:data {:foo #{"bar"}
                                :baz [1 2 3
                                      {:arst {'neio (ex-info "foo" {})}}
                                      (range 4 10)]}
                         :initial-realize true
                         :on-click #(prn "clicked" %2)
                         :on-realize #(prn "realized" %2)
                         :on-expand #(prn "expanded" %2)
                         :on-focus #(prn "focus" %2)
                         :on-blur #(prn "blur" %2)})))
