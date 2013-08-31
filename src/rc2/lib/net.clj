;;;; TODO Convert this into a library. It should be kept general as much as possible.
;;;; TODO Convert this so that the sockets are asynchronous - they should operate in go blocks and
;;;; push information into channels. Automatic serialization and deserialization of protocol buffers
;;;; should happen on either end of the input and output channels.
;;;; A macro to create new automatic sockets for a given protobuf class would be good.

(ns rc2.lib.net
  (:import (com.pascucci.s3p S3PTranslator)
           (java.io BufferedInputStream)
           (java.net Socket))
  (:use clojure.core.async
        protobuf.core))

(declare first-packet!
         read-to!)

(defn s3p-encode [data]
  "Encode a byte or int array into an S3P-encoded packet represented as a byte array."
  (let [translator (S3PTranslator.)
        data (into-array Byte/TYPE data)]
    (.setRawBuffer translator data)
    (.getEncodedBuffer translator)))

(defn s3p-decode [data]
  "Decode a byte or int array from an S3P-encoded packet into a byte array."
  (let [translator (S3PTranslator.)
        data (into-array Byte/TYPE data)]
    (.setEncodedBuffer translator data)
    (.getRawBuffer translator)))

;; TODO Test everthing below this comment.

(defn ensure-buffered [input-stream]
  (if (instance? java.io.BufferedInputStream input-stream)
    input-stream
    (BufferedInputStream. input-stream)))

(defn read-s3p-packet! [input-stream]
  "Read an S3P packet from the input-stream."
  (let [packet (first-packet! input-stream)]
    (if (nil? packet)
      packet
      (s3p-decode packet))))

(defn first-packet! [input-stream]
  "Consume the first full packet from input-stream. If a full packet cannot be read, this will set the
stream to start at the first start byte."
  (let [input-stream (ensure-buffered input-stream)]
    (read-to! S3PTranslator/S3P_START input-stream)
    (.mark input-stream 1024)
    (let [packet-data (map (fn [x] (if (<= 128 x) (- x 256) x))
                           (read-to! S3PTranslator/S3P_TERM input-stream))]
      (if (= (last packet-data) S3PTranslator/S3P_TERM)
        ;; The read to the start byte removes it from the stream. We need to add it back.
        (into [S3PTranslator/S3P_START] packet-data)
        ;; If no packet was found we should reset the stream so that we can retry later.
        (do (.reset input-stream) nil)))))

;; TODO Rewrite this to take a pattern to match against the stream
;; There may be a library call you can use
(defn read-to! [marker input-stream]
  "Read up to and including a marker in the input stream and return the data read."
  (let [data (atom [])
        marker (int marker)]
    (while (and (< 0 (.available input-stream))
                (not (= marker (last @data))))
      (swap! data conj (.read input-stream)))
    @data))

(defn connect-to [address port]
  "Connect to the given address and return a socket."
  (Socket. address port))

(defn sock-write! [socket data]
  "Write data to a socket."
  (let [out-stream (.getOutputStream socket)]
    (.write out-stream data)))

(defn sock-close! [socket]
  "Close a socket."
  (.close socket))

(defn s3p-sock-read! [socket]
  "Read an S3P packet from a socket."
  (read-s3p-packet! (.getInputStream socket)))

(defn sock-available [socket]
  (.available (.getInputStream socket)))

;; TODO What happens if the reference to the input channel is lost (due to programmer error)? Should
;; the connection time out and close itself?
(defn async-protobuf-connection [pb-type address port in-channel & {:keys [buffer-size] :or [10]}]
  "Connect to the given address and return a channel for received data. The connection will
  automatically handle serialization of inbound and outbound data. Set the protobuf class to
  deserialize to with pb-type."
  (let [socket (connect-to address port)
        out-channel (chan buffer-size)
        closed (atom false)]
    ;; This go block handles writing to the socket. It takes messages (non-serialized protobufs) and
    ;; serializes them into the socket.
    (go (loop []
          (let [msg (<! in-channel)]
            ;; If the input channel has been closed, we should close down the socket and output channel.
            (if (nil? msg)
              (do (sock-close! socket) (close! out-channel) (reset! closed true))
              (do (sock-write! socket (s3p-encode (protobuf-dump msg)))
                  (recur))))))
    ;; This go block handles reading from the socket. It attempts to read an S3P packet and
    ;; deserialize it.
    (go (loop []
          (let [packet (s3p-sock-read! socket)]
           (when (not (nil? packet))
             ;; TODO Push this down one level of abstraction so all the calls in this function are
             ;; the same level.
             (>! out-channel (protobuf-load pb-type packet))))
          (if @closed
            (close! out-channel)
            (recur))))
    out-channel))
