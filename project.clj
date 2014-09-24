(defproject ring-jetty "0.1.0-SNAPSHOT"
  :description "A fork of ring library, which support websocket and spdy but heavily depends on jetty"
  :url "https://github.com/federkasten/ring-jetty"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies
    [[org.clojure/clojure "1.3.0"]
     [ring/ring-core "1.3.1"]
     [ring/ring-devel "1.3.1"]
     [ring-jetty/ring-jetty-adapter "0.1.0-SNAPSHOT"]
     [ring-jetty/ring-servlet "0.1.0-SNAPSHOT"]
     [ring-jetty/ring-ws "0.1.0-SNAPSHOT"]]
  :plugins
    [[lein-sub "0.2.4"]
     [codox "0.8.5"]]
  :sub
    ["ring-core"
     "ring-devel"
     "ring-jetty-adapter"
     "ring-servlet"]
  :codox
    {:src-dir-uri "http://github.com/ring-clojure/ring/blob/1.3.1/"
     :src-linenum-anchor-prefix "L"
     :sources ["ring-core/src"
               "ring-devel/src"
               "ring-jetty-adapter/src"
               "ring-servlet/src"]})
