(defproject carrot "2.1.0"
  :description "A Clojure library designed to providing the implementation of  RabbitMq delayed retry mechanism."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 ;; rabbitmq client
                 [com.novemberain/langohr "3.6.1"]

                 ;; logging
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-api "1.7.21"]
                 [ch.qos.logback/logback-classic "1.1.7" :exclusions [org.slf4j/slf4j-api]]
                 [ch.qos.logback/logback-access "1.1.7"]
                 [ch.qos.logback/logback-core "1.1.7"]

                 ;; errors
                 [dire "0.5.4"]]

  :aliases {"test" ["test" ":default"]
            "integration" ["test" ":integration"]}
  :test-selectors {:default (fn [m] (not (or (:integration m) (:regression m))))
                   :integration :integration})
