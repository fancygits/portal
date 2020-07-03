(ns portal.inspector
  (:require [portal.styled :as s]
            [portal.colors :as c]
            [clojure.string :as str]
            [cognitect.transit :as t]
            [lambdaisland.deep-diff2.diff-impl :as diff]))

(defn date? [value] (instance? js/Date value))
(defn url? [value] (instance? js/URL value))

(defn get-value-type [value]
  (cond
    (instance? diff/Deletion value)   :diff
    (instance? diff/Insertion value)  :diff
    (instance? diff/Mismatch value)   :diff

    (map? value)      :map
    (set? value)      :set
    (vector? value)   :vector
    (list? value)     :list
    (coll? value)     :coll
    (boolean? value)  :boolean
    (symbol? value)   :symbol
    (number? value)   :number
    (string? value)   :string
    (keyword? value)  :keyword
    (var? value)      :var

    (uuid? value)     :uuid
    (t/uuid? value)   :uuid
    (url? value)      :uri
    (t/uri? value)    :uri
    (date? value)     :date

    (t/tagged-value? value)
    (case (.-tag value)
      "portal.transit/var"        :var
      "portal.transit/exception"  :exception
      "portal.transit/object"     :object
      :tagged)))

(declare inspector)

(defn diff-added [settings value]
  (let [color (::c/diff-add settings)]
    [s/div
     {:style {:flex 1
              :background (str color "22")
              :border (str "1px solid " color)
              :border-radius (:border-radius settings)}}
     [inspector settings value]]))

(defn diff-removed [settings value]
  (let [color (::c/diff-remove settings)]
    [s/div
     {:style {:flex 1
              :background (str color "22")
              :border (str "1px solid " color)
              :border-radius (:border-radius settings)}}
     [inspector settings value]]))

(defn inspect-diff [settings value]
  (let [removed (get value :- ::not-found)
        added   (get value :+ ::not-found)]
    [s/div
     {:style {:display :flex :width "100%"}}
     (when-not (= removed ::not-found)
       [s/div {:style
               {:flex 1
                :margin-right
                (when-not (= added ::not-found)
                  (:spacing/padding settings))}}
        [diff-removed settings removed]])
     (when-not (= added ::not-found)
       [s/div {:style {:flex 1}}
        [diff-added settings added]])]))

(defn get-background [settings]
  (if (even? (:depth settings))
    (::c/background settings)
    (::c/background2 settings)))

(defn tagged-tag [settings tag]
  [s/div
   [s/span {:style {:color (::c/tag settings)}} "#"]
   tag])

(defn tagged-value [settings tag value]
  [s/div
   {:style {:display :flex :align-items :center}}
   [tagged-tag settings tag]
   [s/div {:style {:margin-left (:spacing/padding settings)}}
    [s/div
     {:style {:margin (str "-" (:spacing/padding settings))}}
     [inspector settings value]]]])

(defn preview-coll [open close]
  (fn [_settings value]
    [s/div {:style {:color "#bf616a"}} open (count value) close]))

(def preview-map    (preview-coll "{" "}"))
(def preview-vector (preview-coll "[" "]"))
(def preview-list   (preview-coll "(" ")"))
(def preview-set    (preview-coll "#{" "}"))

(defn preview-exception [settings value]
  (let [value (.-rep value)]
    [s/span {:style {:font-weight :bold
                     :color (::c/exception settings)}}
     (:class-name (first value))]))

(defn preview-object [settings value]
  [s/span {:style {:color (::c/text settings)}} (:type (.-rep value))])

(defn preview-tagged [settings value]
  [tagged-tag settings (.-tag value)])

(defn container-map [settings child]
  [s/div
   {:style
    {:width "100%"
     :display :grid
     :background (get-background settings)
     :grid-gap (:spacing/padding settings)
     :padding (:spacing/padding settings)
     :box-sizing :border-box
     :color (::c/text settings)
     :font-size  (:font-size settings)
     :border-radius (:border-radius settings)
     :border (str "1px solid " (::c/border settings))}} child])

(defn container-map-k [_settings child]
  [s/div {:style {:grid-column "1"
                  :display :flex
                  :align-items :center}} child])

(defn container-map-v [_settings child]
  [s/div {:style {:grid-column "2"
                  :display :flex
                  :align-items :center}} child])

(defn inspect-map [settings values]
  [container-map
   settings
   (take
    (:limits/max-length settings)
    (filter
     some?
     (for [[k v] values]
       [:<>
        {:key (hash k)}
        [container-map-k
         settings
         [inspector (assoc settings :coll values) k]]
        [container-map-v
         settings
         [inspector (assoc settings :coll values :k k) v]]])))])

(defn container-coll [settings child]
  [s/div
   {:style
    {:width "100%"
     :text-align :left
     :display :grid
     :background (get-background settings)
     :grid-gap (:spacing/padding settings)
     :padding (:spacing/padding settings)
     :box-sizing :border-box
     :color (::c/text settings)
     :font-size  (:font-size settings)
     :border-radius (:border-radius settings)
     :border (str "1px solid " (::c/border settings))}} child])

(defn inspect-coll [settings values]
  [container-coll
   settings
   (->> values
        (map-indexed
         (fn [idx itm]
           ^{:key idx}
           [inspector (assoc settings :coll values :k idx) itm]))
        (filter some?)
        (take (:limits/max-length settings)))])

(defn trim-string [settings s]
  (let [max-length (:limits/string-length settings)]
    (if-not (> (count s) max-length)
      s
      (str (subs s 0 max-length) "..."))))

(defn inspect-number [settings value]
  [s/span {:style {:color (::c/number settings)}} value])

(defn hex-color? [s]
  (re-matches #"#[0-9a-f]{6}|#[0-9a-f]{3}gi" s))

(defn inspect-string [settings value]
  (if-let [color (hex-color? value)]
    [s/div
     {:style
      {:padding (* 0.65 (:spacing/padding settings))
       :box-sizing :border-box
       :background color}}
     [s/div
      {:style
       {:text-align :center
        :filter "contrast(500%) saturate(0) invert(1) contrast(500%)"
        :opacity 0.75
        :color color}}
      color]]

    [s/span {:style {:color (::c/string settings)}}
     (pr-str (trim-string settings value))]))

(defn inspect-namespace [settings value]
  (when-let [ns (namespace value)]
    [s/span {:style {:color (::c/namespace settings)}} ns "/"]))

(defn inspect-boolean [settings value]
  [s/span {:style {:color (::c/boolean settings)}}
   (pr-str value)])

(defn inspect-symbol [settings value]
  [s/span {:style {:color (::c/symbol settings) :white-space :nowrap}}
   [inspect-namespace settings value]
   (name value)])

(defn inspect-keyword [settings value]
  [s/span {:style {:color (::c/keyword settings) :white-space :nowrap}}
   ":"
   [inspect-namespace settings value]
   (name value)])

(defn inspect-date [settings value]
  [tagged-value settings "inst" (.toJSON value)])

(defn inspect-uuid [settings value]
  [tagged-value settings "uuid" (str value)])

(defn get-var-symbol [value]
  (cond
    (t/tagged-value? value) (.-rep value)
    :else                   (let [m (meta value)]
                              (symbol (name (:ns m)) (name (:name m))))))

(defn inspect-var [settings value]
  [s/span
   [s/span {:style {:color (::c/tag settings)}} "#'"]
   [inspect-symbol settings (get-var-symbol value)]])

(defn get-url-string [value]
  (cond
    (t/tagged-value? value) (.-rep value)
    :else                   (str value)))

(defn inspect-uri [settings value]
  (let [value (get-url-string value)]
    [s/a
     {:href value
      :style {:color (::c/uri settings)}
      :target "_blank"}
     value]))

(defn inspect-tagged [settings value]
  [tagged-value settings (.-tag value) (.-rep value)])

(defn inspect-default [settings value]
  [s/span {}
   (trim-string settings (pr-str value))])

(defn inspect-exception [settings value]
  (let [value (.-rep value)]
    [s/div
     {:style
      {:background (get-background settings)
       :padding (:spacing/padding settings)
       :box-sizing :border-box
       :color (::c/text settings)
       :font-size  (:font-size settings)
       :border-radius (:border-radius settings)
       :border (str "1px solid " (::c/border settings))}}
     (map
      (fn [value]
        (let [{:keys [class-name message stack-trace]} value]
          [:<>
           {:key (hash value)}
           [s/div
            {:style
             {:margin-bottom (:spacing/padding settings)}}
            [s/span {:style {:font-weight :bold
                             :color (::c/exception settings)}}
             class-name] ": " message]
           [s/div
            {:style {:display     :grid
                     :text-align :right}}
            (map-indexed
             (fn [idx line]
               (let [{:keys [names]} line]
                 [:<> {:key idx}
                  (if (:omitted line)
                    [s/div {:style {:grid-column "1"}} "..."]
                    [s/div {:style {:grid-column "1"}}
                     (if (empty? names)
                       [s/span
                        [s/span {:style {:color (::c/package settings)}}
                         (:package line) "."]
                        [s/span {:style {:font-style :italic}}
                         (:simple-class line) "."]
                        (str (:method line))]
                       (let [[ns & names] names]
                         [s/span
                          [s/span {:style {:color (::c/namespace settings)}} ns "/"]
                          (str/join "/" names)]))])
                  [s/div {:style {:grid-column "2"}} (:file line)]
                  [s/div {:style {:grid-column "3"}}
                   [inspect-number settings (:line line)]]]))
             stack-trace)]]))
      value)]))

(defn inspect-object [settings value]
  (let [value (.-rep value)]
    [s/span {:title (:type value)
             :style
             {:color (::c/text settings)}}
     (:string value)]))

(defn get-preview-component [type]
  (case type
    :diff       inspect-diff
    :map        preview-map
    :set        preview-set
    :vector     preview-vector
    :list       preview-list
    :coll       preview-list
    :boolean    inspect-boolean
    :symbol     inspect-symbol
    :number     inspect-number
    :string     inspect-string
    :keyword    inspect-keyword
    :date       inspect-date
    :uuid       inspect-uuid
    :var        inspect-var
    :exception  preview-exception
    :object     inspect-object
    :uri        inspect-uri
    :tagged     preview-tagged
    inspect-default))

(def preview-type?
  #{:map :set :vector :list :coll :tagged :exception})

(defn preview [settings value]
  (let [type (get-value-type value)
        component (get-preview-component type)]
    (when (preview-type? type)
      [component settings value])))

(defn get-inspect-component [type]
  (case type
    :diff       inspect-diff
    (:set :vector :list :coll) inspect-coll
    :map        inspect-map
    :boolean    inspect-boolean
    :symbol     inspect-symbol
    :number     inspect-number
    :string     inspect-string
    :keyword    inspect-keyword
    :date       inspect-date
    :uuid       inspect-uuid
    :var        inspect-var
    :exception  inspect-exception
    :object     inspect-object
    :uri        inspect-uri
    :tagged     inspect-tagged
    inspect-default))

(defn inspector [settings value]
  (let [preview? (> (:depth settings) (:limits/max-depth settings))
        component (or (get settings :component)
                      (let [type (get-value-type value)]
                        (if preview?
                          (get-preview-component type)
                          (get-inspect-component type))))
        settings (-> settings (update :depth inc) (dissoc :component))]
    [s/div
     {:on-click
      (fn [e]
        (when (= 2 (:depth settings))
          (.stopPropagation e)
          ((:portal/on-nav settings)
           (merge
            {:value value}
            (select-keys settings [:coll :k])))))
      :style {:cursor :pointer
              :width "100%"
              :padding (when (or preview? (not (coll? value)))
                         (* 0.65 (:spacing/padding settings)))
              :box-sizing :border-box
              :border-radius (:border-radius settings)
              :border "1px solid rgba(0,0,0,0)"}
      :style/hover {:border
                    (when (= 2 (:depth settings))
                      "1px solid #D8DEE9")}}
     [component settings value]]))

