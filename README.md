# conjurernix/malli-pojo

Generating POJO(-like) classes from malli schemas using `gen-class` voodoo

## Usage (TODO)

An example of using the library

```clojure

(defpojo user
  {:class-name User}
  [:map
    [:id :int]
    [:name :string]])

; expands to something like =>
(do
  (clojure.core/gen-class
    :name
    User
    :prefix
    "-G__25083-"
    :state
    conjurernix.malli-pojo.api/state
    :init
    conjurernix.malli-pojo.api/init
    :constructors
    ([] [])
    :main
    false
    :methods
    [[getId [] int] [getName [] java.lang.String] [setId [int] void] [setName [java.lang.String] void]])
  (clojure.core/defn -G__25083-init [] [[] (clojure.core/atom {})])
  (clojure.core/defn -G__25083-getId [this] (clojure.core/get (clojure.core/deref (.state this)) :id))
  (clojure.core/defn -G__25083-getName [this] (clojure.core/get (clojure.core/deref (.state this)) :name))
  (clojure.core/defn -G__25083-setId [this value] (clojure.core/swap! (.state this) clojure.core/assoc :id value))
  (clojure.core/defn -G__25083-setName [this value] (clojure.core/swap! (.state this) clojure.core/assoc :name value))
  (def user [:map [:id :int] [:name :string]]))
```

or simply use `pojo` instead of `defpojo` if you don't need to `def` the schema to a `var`.

## License

Copyright © 2024 Nikolaspafitis

Distributed under the Eclipse Public License version 1.0.
