(ns carrot.exp-backoff (:require
            [dire.core :refer [with-handler!]]
            [langohr.basic :as lb]
            [langohr.queue :as lq]
            [langohr.exchange :as le]
            [langohr.consumers :as lc]))



(defn nack [ch message meta routing-key retry-attempts {:keys [waiting-exchange dead-letter-exchange message-exchange retry-config]} logger-fn]
  (let [retry-attempts (int retry-attempts)
        max-retry (:max-retry-count retry-config)
        exchange (if (> max-retry retry-attempts) waiting-exchange dead-letter-exchange)
        calculated-ttl (int (+ 300 (.pow (BigInteger. (str (:initial-ttl retry-config))) (int (+ 1 retry-attempts)))))
        exp-waiting-queue-name (str "waiting-" routing-key "-"  (str calculated-ttl))]
    (when logger-fn (logger-fn "LOGME ns=carrot.core name=current-retry-attempt message:" (:message-id meta) retry-attempts))
    (when logger-fn (logger-fn "Sending message " (:message-id meta)  " to the exchange: " exchange))
    (lq/declare ch exp-waiting-queue-name
                {:exlusive false
                 :auto-delete false
                 :durable true
                 :arguments {"x-message-ttl" calculated-ttl
                             "x-expires" (* 100 calculated-ttl)
                             "x-dead-letter-exchange" message-exchange
                             "x-dead-letter-routing-key" routing-key
                             }})
    (lq/bind ch exp-waiting-queue-name waiting-exchange {:routing-key exp-waiting-queue-name})
    (lb/publish ch
                exchange
                (if (= exchange dead-letter-exchange) routing-key exp-waiting-queue-name)
                message
                (merge
                 meta
                 {:persistent true
                  :headers {"retry-attempts" (inc retry-attempts)}})))
  (lb/ack ch (:delivery-tag meta)))


(defn declare-system [channel
                             {:keys [waiting-exchange dead-letter-exchange message-exchange]}
                             exchange-type
                             exchange-config
                                  waiting-queue-config]
  (le/declare channel waiting-exchange exchange-type exchange-config)
  (le/declare channel message-exchange exchange-type exchange-config)
  (le/declare channel dead-letter-exchange exchange-type exchange-config))