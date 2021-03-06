(ns ring-jetty.adapter.jetty
  "A Ring adapter that uses the Jetty 9 embedded web server.

  Adapters are used to convert Ring handlers into running web servers.
  Derived from ring.adapter.jetty and info.sunng/ring-jetty9-adapter"
  (:import (org.eclipse.jetty.server Handler Server Request ServerConnector HttpConfiguration HttpConnectionFactory SslConnectionFactory ConnectionFactory)
           (org.eclipse.jetty.server.handler HandlerCollection AbstractHandler ContextHandler HandlerList)
           (org.eclipse.jetty.util.thread QueuedThreadPool ScheduledExecutorScheduler)
           (org.eclipse.jetty.util.ssl SslContextFactory)
           (org.eclipse.jetty.websocket.server WebSocketHandler)
           (org.eclipse.jetty.websocket.servlet WebSocketServletFactory WebSocketCreator ServletUpgradeRequest ServletUpgradeResponse)
           (org.eclipse.jetty.websocket.api WebSocketAdapter Session UpgradeRequest)
           (javax.servlet.http HttpServletRequest HttpServletResponse))
  (:require [ring-jetty.util.servlet :as servlet]
            [clojure.string :as string]))

(defn- do-nothing [& args])

(defn- resolve-handle-fn
  [handler key]
  (if-let [f (get @(resolve handler) key nil)]
    f
    do-nothing))

(defn- proxy-ws-adapter
  [ws-handler]
  (proxy [WebSocketAdapter] []
    (onWebSocketConnect [^Session session]
      (proxy-super onWebSocketConnect session)
      ((resolve-handle-fn ws-handler :on-connect) this))
    (onWebSocketError [^Throwable e]
      ((resolve-handle-fn ws-handler :on-error) this e))
    (onWebSocketText [^String message]
      ((resolve-handle-fn ws-handler :on-text) this message))
    (onWebSocketClose [statusCode ^String reason]
      (proxy-super onWebSocketClose statusCode reason)
      ((resolve-handle-fn ws-handler :on-close) this statusCode reason))
    (onWebSocketBinary [^bytes payload offset len]
      ((resolve-handle-fn ws-handler :on-bytes) this payload offset len))))

(defn- reify-ws-creator
  [ws-handler]
  (reify WebSocketCreator
    (createWebSocket [this _ _]
      (proxy-ws-adapter ws-handler))))

(defprotocol RequestMapDecoder
  (build-request-map [r]))

(extend-protocol RequestMapDecoder
  HttpServletRequest
  (build-request-map [request]
    (servlet/build-request-map request))
  UpgradeRequest
  (build-request-map [request]
    {:uri (.getRequestURI request)
     :query-string (.getQueryString request)
     :origin (.getOrigin request)
     :host (.getHost request)
     :request-method (keyword (.toLowerCase (.getMethod request)))
     :headers (reduce(fn [m [k v]]
                       (assoc m (string/lower-case k) (string/join "," v)))
                     {}
                     (.getHeaders request))}))

(defn- proxy-ws-handler
  "Returns a Jetty websocket handler"
  [ws-handler {:as options
               :keys [ws-max-idle-time]
               :or {ws-max-idle-time 500000}}]
  (proxy [WebSocketHandler] []
    (configure [^WebSocketServletFactory factory]
      (-> (.getPolicy factory)
          (.setIdleTimeout ws-max-idle-time))
      (.setCreator factory (reify-ws-creator ws-handler)))))

(defn- proxy-handler
  "Returns an Jetty Handler implementation for the given Ring handler."
  [handler]
  (proxy [AbstractHandler] []
    (handle [_ ^Request base-request request response]
      (let [request-map  (servlet/build-request-map request)
            response-map (handler request-map)]
        (when response-map
          (servlet/update-servlet-response response response-map)
          (.setHandled base-request true))))))

(defn- http-config
  [{:as options
    :keys [ssl-port secure-scheme output-buffer-size request-header-size
           response-header-size send-server-version? send-date-header?
           header-cache-size]
    :or {ssl-port 443
         secure-scheme "https"
         output-buffer-size 32768
         request-header-size 8192
         response-header-size 8192
         send-server-version? true
         send-date-header? false
         header-cache-size 512}}]
  "Creates jetty http configurator"
  (doto (HttpConfiguration.)
    (.setSecureScheme secure-scheme)
    (.setSecurePort ssl-port)
    (.setOutputBufferSize output-buffer-size)
    (.setRequestHeaderSize request-header-size)
    (.setResponseHeaderSize response-header-size)
    (.setSendServerVersion send-server-version?)
    (.setSendDateHeader send-date-header?)
    (.setHeaderCacheSize header-cache-size)))

(defn- ssl-context-factory
  "Creates a new SslContextFactory instance from a map of options."
  [{:as options
    :keys [keystore keystore-type key-password client-auth
           truststore trust-password truststore-type]}]
  (let [context (SslContextFactory.)]
    (if (string? keystore)
      (.setKeyStorePath context keystore)
      (.setKeyStore context ^java.security.KeyStore keystore))
    (.setKeyStorePassword context key-password)
    (when keystore-type
      (.setKeyStoreType context keystore-type))
    (when truststore
      (.setTrustStore context ^java.security.KeyStore truststore))
    (when trust-password
      (.setTrustStorePassword context trust-password))
    (when truststore-type
      (.setTrustStoreType context truststore-type))
    (case client-auth
      :need (.setNeedClientAuth context true)
      :want (.setWantClientAuth context true)
      nil)
    context))

(defn- create-server
  "Construct a Jetty Server instance."
  [{:as options
    :keys [port max-threads daemon? max-idle-time host ssl? ssl-port]
    :or {port 80
         max-threads 50
         daemon? false
         max-idle-time 200000
         ssl? false}}]
  (let [pool (let [p (doto (QueuedThreadPool. (int max-threads))
                       (.setDaemon daemon?)
                       (.setMinThreads (options :min-threads 8)))]
               (when-let [max-queued (:max-queued options)]
                 (.setMaxQueued p max-queued))
               p)
        server (doto (Server. pool)
                 (.addBean (ScheduledExecutorScheduler.)))
        http-configuration (http-config options)
        http-connector (doto (ServerConnector.
                              ^Server server
                              (into-array ConnectionFactory [(HttpConnectionFactory. http-configuration)]))
                         (.setPort port)
                         (.setHost host)
                         (.setIdleTimeout max-idle-time))
        https-connector (when (or ssl? ssl-port)
                          (doto (ServerConnector.
                                 ^Server server
                                 (ssl-context-factory options)
                                 (into-array ConnectionFactory [(HttpConnectionFactory. http-configuration)]))
                            (.setPort ssl-port)
                            (.setHost host)
                            (.setIdleTimeout max-idle-time)))
        connectors (if https-connector
                     [http-connector https-connector]
                     [http-connector])
        connectors (into-array connectors)]
    (.setConnectors server connectors)
    server))

(defn ^Server run-jetty
  "Start a Jetty webserver to serve the given handler according to the
  supplied options:

  :configurator   - a function called with the Jetty Server instance
  :port           - the port to listen on (defaults to 80)
  :host           - the hostname to listen on
  :join?          - blocks the thread until server ends (defaults to true)
  :daemon?        - use daemon threads (defaults to false)
  :ssl?           - allow connections over HTTPS
  :ssl-port       - the SSL port to listen on (defaults to 443, implies :ssl?)
  :keystore       - the keystore to use for SSL connections
  :key-password   - the password to the keystore
  :truststore     - a truststore to use for SSL connections
  :trust-password - the password to the truststore
  :max-threads    - the maximum number of threads to use (default 50)
  :min-threads    - the minimum number of threads to use (default 8)
  :max-queued     - the maximum number of requests to queue (default unbounded)
  :max-idle-time  - the maximum idle time in milliseconds for a connection (default 200000)
  :ws-max-idle-time  - the maximum idle time in milliseconds for a websocket connection (default 500000)
  :client-auth    - SSL client certificate authenticate, may be set to :need,
                    :want or :none (defaults to :none)
  :websockets     - a map from context path to a map of handler fns"
  [handler options]
  (let [^Server s (create-server (dissoc options :configurator))
        ring-app-handler (proxy-handler handler)
        ws-handlers (map (fn [[context-path handler]]
                           (doto (ContextHandler.)
                             (.setContextPath context-path)
                             (.setHandler (proxy-ws-handler handler options))))
                         (options :websockets))
        contexts (doto (HandlerList.)
                   (.setHandlers
                    (into-array Handler (reverse (conj ws-handlers ring-app-handler)))))]
    (.setHandler s contexts)
    (when-let [configurator (:configurator options)]
      (configurator s))
    (.start s)
    (when (:join? options true)
      (.join s))
    s))
