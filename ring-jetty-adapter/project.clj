(defproject ring-jetty/ring-jetty-adapter "0.1.0-SNAPSHOT"
  :description "Ring Jetty adapter that support websocket."
  :url ""
  :scm {:dir ".."}
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [ring/ring-core "1.3.1"
                  :exclusions [javax.servlet/servlet-api]]
                 [ring-jetty/ring-servlet "0.1.0-SNAPSHOT"
                  :exclusions [javax.servlet/servlet-api
                               javax.servlet/javax.servlet-api]]
                 [org.eclipse.jetty/jetty-server "9.2.3.v20140905"]
                 [org.eclipse.jetty.websocket/websocket-server "9.2.3.v20140905"]
                 [org.eclipse.jetty.websocket/websocket-servlet "9.2.3.v20140905"]]
  :profiles
  {:dev {:dependencies [[clj-http "0.6.4"]]}
   :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
   :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
   :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}})
