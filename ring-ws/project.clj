(defproject ring-jetty/ring-ws "0.1.0-SNAPSHOT"
  :description "Ring websocket utilities based on jetty."
  :url "https://github.com/federkasten/ring-jetty"
  :scm {:dir ".."}
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.eclipse.jetty/jetty-server "9.2.3.v20140905"]
                 [org.eclipse.jetty.websocket/websocket-server "9.2.3.v20140905"]
                 [org.eclipse.jetty.websocket/websocket-servlet "9.2.3.v20140905"]]
  :profiles
  {:provided {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]]}
   :dev {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]]}
   :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
   :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
   :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}})
