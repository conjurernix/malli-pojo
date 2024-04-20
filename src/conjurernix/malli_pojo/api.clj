(ns conjurernix.malli-pojo.api
  (:require [camel-snake-kebab.core :as csk]
            [hashp.core]
            [malli.core :as m]
            [malli.error :as me])
  (:import (java.util List Set UUID)))

(def def-pojo-args
  [:schema {:registry
            {:props         [:map
                             [:pojo-name :symbol]]
             :def-pojo-args [:catn
                             [:name :symbol]
                             [:props :props]
                             [:schema [:fn (fn [s]
                                             (-> s
                                                 (m/schema)
                                                 (m/type)
                                                 (= :map)))]]]}}
   :def-pojo-args])

(defn- -seq-schema? [st]
  (contains? #{:vector :sequential} st))

(defn- -set-schema? [st]
  (= :set st))

(def -prim-types
  {:string            String
   :int               'int
   :double            'double
   :boolean           'boolean
   :keyword           String
   :symbol            String
   :qualified-keyword String
   :qualified-symbol  String
   :uuid              UUID})

(defn- -prim? [t]
  (contains? -prim-types (m/type t)))

(defn malli-schema->java-type [s]
  (let [schema-type (m/type s)]
    (cond
      (-seq-schema? schema-type) List

      (-set-schema? schema-type) Set
      (-prim? schema-type) (get -prim-types schema-type))))



(defmacro defpojo [& args]
  (let [parsed-args (m/parse def-pojo-args args)]
    (when (= ::m/invalid parsed-args)
      (let [explanation (m/explain def-pojo-args parsed-args)
            human-explanation (first (me/humanize explanation))]
        (throw (ex-info human-explanation explanation))))
    (let [{:keys [name props schema]} parsed-args
          {:keys [pojo-name interface-name]} props
          pojo-name (or pojo-name (csk/->PascalCaseSymbol name))
          interface-name (or interface-name (symbol (str "I" pojo-name)))
          attributes (->> schema
                          (m/children)
                          (map (fn [[k _props v]]
                                 [k {:field-name        (csk/->camelCaseString k)
                                     :getter-method-sym (symbol (str "get" (csk/->PascalCaseString k)))
                                     :setter-method-sym (symbol (str "set" (csk/->PascalCaseString k)))
                                     :field-type        (malli-schema->java-type v)}]))
                          (into {}))
          interface-getters (->> attributes
                                 (map (fn [[_k {:keys [getter-method-sym field-type]}]]
                                        (list (with-meta getter-method-sym {:tag field-type}) []))))

          interface-setters (->> attributes
                                 (map (fn [[_k {:keys [setter-method-sym field-type]}]]
                                        (list (with-meta setter-method-sym {:tag 'void})
                                              [(with-meta 'val {:tag field-type})]))))

          getters-impl (->> attributes
                            (map (fn [[k {:keys [getter-method-sym]}]]
                                   `(~getter-method-sym [~'_this]
                                      ~(symbol k)))))

          setters-impl (->> attributes
                            (map (fn [[k {:keys [setter-method-sym]}]]
                                   `(~setter-method-sym [~'_this ~'val]
                                      (set! ~(symbol k) ~'val)))))

          deftype-props (->> attributes
                             (map (fn [[_k {:keys [field-name]}]]
                                    (with-meta (symbol field-name)
                                               {:unsynchronized-mutable true}))))]
      `(do
         (definterface ~interface-name
           ~@interface-getters
           ~@interface-setters)

         (deftype ~pojo-name [~@deftype-props]
           ~interface-name
           ~@getters-impl
           ~@setters-impl)

         (def ~name ~schema)))))



(comment

  (m/children
    [:map
     [:id :int]
     [:name :string]])

  (macroexpand-1
    '(defpojo user
       {:pojo-name User}
       [:map
        [:id :int]
        [:name :string]]))


  (compile 'conjurernix.malli-pojo.api)
  )

(defpojo user
  {:pojo-name User}
  [:map
   [:id :int]
   [:name :string]])

(deftype Account [^String name
                  ^int balance
                  ^boolean active])

(definterface IAccount
  (^String getName [])
  (^void setName [^String s]))

(defrecord Person [id ^String name])


