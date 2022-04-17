(ns town.lilac.view-edn
  (:require
   [helix.core :refer [$ defnc]]))


(defnc viewer
  [{:keys [data]}])