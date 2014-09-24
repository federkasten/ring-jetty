(ns ring-jetty.util.ws
  "A Ring websocket utilities.

  Derived from info.sunng/ring-jetty9-adapter"
  (:import (org.eclipse.jetty.websocket.api WebSocketAdapter)
           (java.nio ByteBuffer)
           (clojure.lang IFn)))

(defprotocol WebSocketProtocol
  (send! [this msg])
  (close! [this])
  (remote-addr [this])
  (idle-timeout! [this ms]))

(defprotocol WebSocketSend
  (-send! [x ws] "How to encode content sent to the WebSocket clients"))

(extend-protocol WebSocketSend

  (Class/forName "[B")
  (-send! [ba ws]
    (-send! (ByteBuffer/wrap ba) ws))

  ByteBuffer
  (-send! [bb ws]
    (-> ws .getRemote (.sendBytes ^ByteBuffer bb)))

  String
  (-send! [s ws]
    (-> ws .getRemote (.sendString ^String s)))

  IFn
  (-send! [f ws]
    (-> ws .getRemote f))

  Object
  (-send! [this ws]
    (-> ws .getRemote (.sendString (str this))))

  ;; "nil" could PING?
  ;; nil
  ;; (-send! [this ws] ()

  )

(extend-protocol WebSocketProtocol
  WebSocketAdapter
  (send! [this msg]
    (-send! msg this))
  (close! [this]
    (.. this (getSession) (close)))
  (remote-addr [this]
    (.. this (getSession) (getRemoteAddress)))
  (idle-timeout! [this ms]
    (.. this (getSession) (setIdleTimeout ^long ms))))
