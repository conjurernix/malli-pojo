(ns conjurernix.malli-pojo.api
  (:require [camel-snake-kebab.core :as csk]
            [hashp.core]
            [malli.core :as m]
            [malli.error :as me])
  (:import (java.util List Set UUID)))

(def defpojo-args
  [:schema {:registry
            {:annotation-map :map
             :annotations    [:map
                              [:class {:optional true} :map]
                              [:getter {:optional true} [:map-of :keyword :annotation-map]]
                              [:setter {:optional true} [:map-of :keyword :annotation-map]]]
             :props          [:map
                              [:class-name :symbol]
                              [:annotations {:optional true} :annotations]]
             :defpojo-args   [:catn
                              [:name :symbol]
                              [:props :props]
                              [:schema [:fn {:error/fn (fn [s]
                                                         (str s " is not a valid map schema"))}
                                        m/schema?]]]}}
   :defpojo-args])

(defmulti schema->pojo
  "Given a schema, retrieve the generated pojo class"
  m/schema)

(defmulti pojo->schema
  "Given a Pojo class generated by `malli-pojo` retrieve the schema"
  identity)

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


(defn pojo* [{:keys [name props schema]}]
  (let [prefix (str "-" (gensym) "-")
        with-prefix (fn [name]
                      (symbol (str prefix name)))

        {:keys [class-name annotations]} props
        {class-annotations  :class
         getter-annotations :getter
         setter-annotations :setter} annotations
        class-name (or class-name (csk/->PascalCaseSymbol name))
        init-name (with-prefix 'init)

        attributes (->> schema
                        (m/children)
                        (map (fn [[k _props v]]
                               [k {:field-name        (csk/->camelCaseString k)
                                   :getter-method-sym (symbol (str "get" (csk/->PascalCaseString k)))
                                   :setter-method-sym (symbol (str "set" (csk/->PascalCaseString k)))
                                   :field-type        (malli-schema->java-type v)}]))
                        (into {}))

        getter-defs (->> attributes
                         (mapv (fn [[k {:keys [getter-method-sym field-type]}]]
                                 [(with-meta getter-method-sym
                                             (get getter-annotations k)) [] field-type])))

        setter-defs (->> attributes
                         (mapv (fn [[k {:keys [setter-method-sym field-type]}]]
                                 [(with-meta setter-method-sym
                                             (get setter-annotations k)) [field-type] 'void])))


        getters-impl (->> attributes
                          (map (fn [[k {:keys [getter-method-sym]}]]
                                 `(defn ~(with-prefix getter-method-sym) ~'[this]
                                    (get @(.state ~'this) ~k)))))

        setters-impl (->> attributes
                          (map (fn [[k {:keys [setter-method-sym]}]]
                                 `(defn ~(with-prefix setter-method-sym) ~'[this value]
                                    (swap! (.state ~'this) assoc ~k ~'value)))))]
    `(do

       (gen-class
         :name ~(with-meta class-name
                           class-annotations)
         :prefix ~prefix
         :state "state"
         :init "init"
         :constructors ([])
         :main false
         :methods [~@getter-defs
                   ~@setter-defs])

       (defn ~init-name []
         [[] (atom {})])

       ~@getters-impl

       ~@setters-impl

       (defmethod schema->pojo ~schema [_]
         ~class-name)

       (defmethod pojo->schema ~class-name [_]
         ~schema))))

(defmacro pojo [& args]
  (let [parsed-args (m/parse defpojo-args args)]
    (when (= ::m/invalid parsed-args)
      (let [explanation (m/explain defpojo-args parsed-args)
            human-explanation (first (me/humanize explanation))]
        (throw (ex-info human-explanation explanation))))
    (pojo* parsed-args)))

(defmacro defpojo [& [name _props schema :as args]]
  (let [schema (m/schema schema)]
    `(do
       (def ~name ~schema)
       (pojo ~@args))))



