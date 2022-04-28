(ns town.lilac.view-edn
  (:require
   [helix.core :refer [$ <> defnc defhook fnc]]
   [helix.dom :as d]
   [helix.hooks :as hooks]))


(def focus-tree-context
  (helix.core/create-context nil))


(defn index-of
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


(defn focus-reducer
  [state action]
  (js/console.log
   (:focus state)
   (to-array (map #(-> % :ref deref) (:nodes state))))

  (case (:type action)
    :add (let [{:keys [level value] :or {level 0}} action]
           (update state :nodes conj {:level level
                                      :ref value}))
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
      (js/console.log "prev" index next-index)
      (assoc state :focus next-ref))
    :move-focus-next
    (let [index (index-of #(= (:focus state) (:ref %))
                          (:nodes state))
          next-index (if (< (inc index) (count (:nodes state)))
                       (inc index)
                       index)
          next-ref (:ref (nth (:nodes state) next-index))]
      (js/console.log "next" index next-index (count (:nodes state)) next-ref)
      (assoc state :focus next-ref))))


(defhook use-focus-tree
  []
  (let [[tree set-tree] (hooks/use-reducer
                         focus-reducer
                         {:focus nil
                          :level 0
                          :nodes []})]
    (hooks/use-effect
     [(:focus tree)]
     (prn (:focus tree))
     (when-let [el (and (:focus tree) @(:focus tree))]
       (.focus el)))
    [tree set-tree]))


(defhook use-focus-leaf
  [ref]
  (let [[tree set-tree] (hooks/use-context focus-tree-context)
        {:keys [focus level]} tree]
    (hooks/use-effect
     [ref]
     (set-tree {:type :add :value ref})
     #(set-tree {:type :remove :value ref}))
    {:context [tree
               (fn set-tree' [action]
                 (set-tree (assoc action :level (inc level))))]
     :tabindex (if (= focus ref) "0" "-1")
     :handle-click (fn set-focus
                     [e]
                     (set-tree {:type :set-focus
                                :value ref}))
     :handle-key-down (fn move-focus
                        [e]
                        (case (.-keyCode e)
                          (37 38) (set-tree {:type :move-focus-prev})
                          (39 40) (set-tree {:type :move-focus-next})
                          nil))}))


(declare view)


(defnc map-entry-view
  [{:keys [k v]}]
  (let [[expanded? set-expanded] (hooks/use-state false)
        focus-ref (hooks/use-ref nil)
        {:keys [context
                tabindex
                handle-click
                handle-key-down]} (use-focus-leaf focus-ref)]
    (d/li
     {:role "treeitem"
      :aria-expanded expanded?
      :tabindex tabindex
      :ref focus-ref
      :on-key-down #(do (.stopPropagation %)
                        (handle-key-down %))
      :on-click #(do (.stopPropagation %)
                     (handle-click %)
                     (set-expanded not))}
     (helix.core/provider
      {:context focus-tree-context
       :value context}
      (d/ul
       {:class ["town_lilac_view-edn__view"
                "town_lilac_view-edn__map-entry"]
        :role "group"}
       ($ view {:data k})
       ($ view {:data v}))))))


(defnc map-view
  [{:keys [data initial-realized?]}]
  (let [[realized? set-realized] (hooks/use-state initial-realized?)
        [expanded? set-expanded] (hooks/use-state false)
        focus-ref (hooks/use-ref nil)
        {:keys [context
                tabindex
                handle-click
                handle-key-down]} (use-focus-leaf focus-ref)]
    (d/li
     {:role "treeitem"
      :aria-expanded expanded?
      :tabindex tabindex
      :ref focus-ref
      :class (when (or (not realized?) (> 2 (count data)))
               "town_lilac_view-edn__no-expand")
      :on-key-down #(do (.stopPropagation %)
                        (handle-key-down %))
      :on-click #(do (.stopPropagation %)
                     (handle-click %)
                     (set-expanded not))}
     (helix.core/provider
      {:context focus-tree-context
       :value context}
      (d/ul
       {:class ["town_lilac_view-edn__view"
                "town_lilac_view-edn__map-view"]
        :role "group"
        :on-click #(do (.stopPropagation %)
                       (handle-click %)
                       (set-realized true))}
       (d/span {:class "town_lilac_view-edn__map_begin"} "{")
       (if realized?
         (for [[k v] data]
           ($ map-entry-view {:key (str (hash k) (hash v))
                              :k k
                              :v v}))
         "...")
       (d/span {:class "town_lilac_view-edn__map_end"} "}"))))))


(defnc list-view
  [{:keys [data initial-realized?]}]
  (let [[begin end] (if (vector? data) "[]" "()")
        [realized? set-realized] (hooks/use-state initial-realized?)
        [expanded? set-expanded] (hooks/use-state false)
        focus-ref (hooks/use-ref nil)
        {:keys [context
                tabindex
                handle-click
                handle-key-down]} (use-focus-leaf focus-ref)]
    (d/li
     {:role "treeitem"
      :aria-expanded expanded?
      :tabindex tabindex
      :ref focus-ref
      :class (when (or (not realized?) (> 2 (count data)))
               "town_lilac_view-edn__no-expand")
      :on-key-down #(do (.stopPropagation %)
                        (handle-key-down %))
      :on-click #(do (.stopPropagation %)
                     (handle-click %)
                     (set-expanded not))}
     (helix.core/provider
      {:context focus-tree-context
       :value context}
      (d/ul
       {:class ["town_lilac_view-edn__view"
                "town_lilac_view-edn__list-view"]
        :role "group"
        :on-click (fn [e]
                    (.stopPropagation e)
                    (handle-click e)
                    (set-realized true))}
       (d/span {:class "town_lilac_view-edn__list_begin"} begin)
       (if realized?
         (for [v data]
           ($ view {:key (hash v) :data v}))
         "...")
       (d/span {:class "town_lilac_view-edn__list_end"} end))))))


(defnc view
  [{:keys [data root?]}]
  (cond
    (map? data) ($ map-view {:data data :root? root?})
    (coll? data) ($ list-view {:data data :root? root?})
    (string? data) (d/li
                    {:role "none"
                     ;:tabindex "-1"
                     :on-click #(.stopPropagation %)}
                    (d/span
                     {:class "town_lilac_view-edn__view"}
                     "\"" data "\""))
    :else (d/li
           {:role "none"
            ;:tabindex "-1"
            :on-click #(.stopPropagation %)}
           (d/span
            {:class "town_lilac_view-edn__view"}
            (str data)))))


(defnc root-view
  [{:keys [data]}]
  (helix.core/provider
   {:context focus-tree-context
    :value (use-focus-tree)}
   (d/ul
    {:role "tree"
     :class "town_lilac_view-edn__root"}
    ($ view {:data data}))))


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
             (set! (str "http://localhost:8080/view-edn.css?r=" (gensym))))
         (-> (.-rel) (set! "stylesheet"))))))


  (require '["react-dom/client" :as rdom])
  (def root (rdom/createRoot (js/document.getElementById "app")))

  (.render root (d/div "hi"))

  (.render root ($ root-view {:data {:foo "bar" :baz [1 2 3 {:arst {'neio (ex-info "foo" {})}} (range 4 10)]}})))
