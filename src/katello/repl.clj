(ns katello.repl
  (:require [com.redhat.qe.auto.selenium.selenium :refer [browser]]
            [clojure.pprint :refer [pp pprint]]
            fn.trace
            selenium-server
            katello.conf
            katello.setup
            test.tree.debug))


(defn new-browser [& [optmap]]
  (katello.setup/new-selenium (-> katello.conf/config deref :browser-types first) true)
  (katello.setup/start-selenium optmap))


;;-------------

(defmacro trace [& body]
  `(fn.trace/dotrace (katello.conf/trace-list)
     ~@body))

(defn debug [tree]
  (test.tree.debug/debug tree {:trace-list (katello.conf/trace-list)}))

(defn print-name-result [resulttree]
  (doseq [result @(second resulttree)]
    (println 
      (:name(first result)) 
      (:parameters (deref (:report (second result))))
      (:result (deref (:report (second result)))))))

(defn name-result [resulttree]
  (for [result @(second resulttree)]
    [(:name(first result)) 
     (:parameters (:report (second result)))
     (:result (:report (second result)))]))


(defn start-session []
  (katello.conf/init {:selenium-address "localhost:4444"})

  (com.redhat.qe.tools.SSLCertificateTruster/trustAllCerts)
  (com.redhat.qe.tools.SSLCertificateTruster/trustAllCertsForApacheXMLRPC)

  (selenium-server/start)
  (if-let [locale (@katello.conf/config :locale)]
    (do (selenium-server/create-locale-profile locale)
        (new-browser {:browser-config-opts (katello.setup/config-with-profile
                                            locale)}))
    (new-browser)))




