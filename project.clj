(defproject my-webapp "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [com.walmartlabs/lacinia "1.2.1"]
                 [com.walmartlabs/lacinia-pedestal "1.2"]
                 [io.aviso/logging "1.0"]
                 [com.stuartsierra/component "1.1.0"]
                 [org.postgresql/postgresql "42.6.0"]
                 [com.mchange/c3p0 "0.9.5.5"]
                 [io.aviso/logging "1.0"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [buddy/buddy-sign "3.5.351"]
                 [clj-time "0.15.2"]
                 [cheshire "5.11.0"]]
  :plugins [[lein-pprint "1.3.2"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:resource-paths ["dev-resources"]
                   ;; :main user
                   :repl-options {:init-ns user}}
             :schema-test {:main my-webapp.schema
                           :repl-options {:init-ns my-webapp.schema}}}
  :main ^:skip-aot my-webapp.core)
