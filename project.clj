(defproject comment-trees "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [clj-http "2.1.0"]
                 [cheshire "5.5.0"]
                 [org.clojure/java.jdbc "0.7.0-alpha2"]
                 [com.h2database/h2 "1.4.194"]
                 [yesql "0.5.3"]
                 [overtone/at-at "1.2.0"]
                 [yogthos/config "0.8"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.25"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]] ]
  :main comment-trees.core)
