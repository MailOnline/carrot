(ns carrot.core (:require
            [dire.core :refer [with-handler!]]
            [langohr.basic :as lb]
            [langohr.queue :as lq]
            [langohr.exchange :as le]
            [langohr.consumers :as lc]
            [carrot.exp-backoff :as exp-backoff]
            [carrot.delayed-retry :as delayed-retry]))

(def ^:private log-ns "carrot.core")

(defn- retry-exception?
  "returns true if the exception has to be retried, false otherwise"
  [exception logger-fn message-id]
  (let [reject? (:reject? (ex-data exception))] ;; reject? = don't retry
    (when (and logger-fn reject?)
      (logger-fn {:type :metric
                  :log-ns log-ns
                  :txid message-id
                  :name "rejectingRetry"}))
    (not reject?)))

(defn- add-exception-data
  "given an exception e, returns a ClojureInfo exceptionthat contains the extra data d.
  previous data of e should be kept."
  [e d]
  ;; TODO figure out if we can keep the stacktrace as well
  (ex-info (or (.getMessage e) "do not retry me")
           (-> (or (ex-data e) {})
               (merge d))))

(defn- throw-with-extra!
  "given a function f attaches an error handler
  that will catch all exceptions and rethrow them with added data {:reject? true}"
  [f extra]
  (with-handler! f
    Exception
    (fn [e & args]
      (throw (add-exception-data e extra)))))

(defn do-not-retry!
  "attach a supervisor that will not retry the functions in fn-coll"
  [fn-coll]
  (doall (map #(throw-with-extra! % {:reject? true}) fn-coll)))

(defn throw-do-not-retry-exception [msg meta]
  (throw (ex-info msg
                  (assoc meta :reject? true ))))

(defn- nack [ch message meta routing-key retry-attempts carrot-system logger-fn]
  (if (= :exp-backoff (get-in carrot-system [:retry-config :strategy]))
    (exp-backoff/nack ch message meta routing-key retry-attempts carrot-system logger-fn)
    (delayed-retry/nack ch message meta routing-key retry-attempts carrot-system logger-fn)))

(defn- message-handler [message-handler routing-key carrot-system logger-fn ch meta ^bytes payload]
  (try
    (let [carrot-map {:channel ch
                      :meta meta
                      :payload payload}]
      (-> carrot-map
          message-handler)
      (lb/ack ch (:delivery-tag meta))
      (when logger-fn
        (logger-fn {:type :ack :log-ns log-ns :txid (:message-id meta)})))
    (catch Exception e
      (when logger-fn
        (logger-fn {:type :error
                    :log-ns log-ns
                    :txid (:message-id meta)
                    :exception e}))
      (if (retry-exception? e logger-fn (:message-id meta))
        (nack ch payload meta routing-key (or (get (:headers meta) "retry-attempts") 0) carrot-system logger-fn)
        (lb/ack ch (:delivery-tag meta))))))

(defn create-message-handler-function
  "Creates message-handler function. Parameters:
  handler: function with your business logoc handling th eincoming message. Input config contains channel, meta and payload
  routing-key: routing key of the message
  carrot-system: the carrot config
  logger-fn: function for logging strings (optional)"
  ([handler routing-key carrot-system logger-fn]
   (partial message-handler handler routing-key carrot-system logger-fn))
  ([handler routing-key carrot-system]
   (create-message-handler-function message-handler handler routing-key carrot-system nil)))

(defn declare-system
  "declares carrot system based on the given connection and config"
  [channel carrot-system]
  (case (get-in carrot-system [:retry-config :strategy])
    :simple-backoff (delayed-retry/declare-system channel carrot-system)
    :exp-backoff (exp-backoff/declare-system channel carrot-system)))

(defn- get-dead-letter-queue-name [queue-name]
  (str queue-name ".dead"))

(defn destroy-system
  "Destroys system (used especially for tests)"
  [channel {:keys [retry-exchange dead-letter-exchange retry-queue message-exchange]} queue-name-coll]
  (map #(lq/delete channel %) queue-name-coll)
  (map #(lq/delete channel get-dead-letter-queue-name) queue-name-coll)
  (lq/delete channel retry-queue)
  (le/delete channel retry-exchange)
  (le/delete channel dead-letter-exchange)
  (le/delete channel message-exchange))

(defn subscribe
  "Subscribe for a message having a retry mechanism provided by carrot"
  ([channel
    {:keys [dead-letter-exchange]}
    queue-name
    message-handler
    queue-config
    dead-queue-config-function]
   (lc/subscribe channel queue-name message-handler queue-config)
   (lq/declare channel (get-dead-letter-queue-name queue-name)
               (merge-with merge (dead-queue-config-function queue-name)
                           {:durable true
                            :auto-delete true}))
   (lq/bind channel (get-dead-letter-queue-name queue-name) dead-letter-exchange {:routing-key queue-name}))
  ([channel
    carrot-config
    queue-name
    message-handler
    queue-config]
   (subscribe channel
              carrot-config
              queue-name
              message-handler
              queue-config
              {})))

(defmacro compose-payload-handler-function
  [& args]
  (list 'fn ['payload]
          (concat (list '-> 'payload)
                args)))
