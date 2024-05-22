(ns servico-clojure.servidor
  (:require [io.pedestal.http.route :as route]
            [io.pedestal.http :as http]
            [io.pedestal.test :as test]
            [servico-clojure.database :as database]))

;exemplo:
;{id {tarefa_id tarefa_nome tarefa_status}}

(defn assoc-store [context]
  (update context :request assoc :store database/store))

(def db-interceptor
  {:name  :db-interceptor
   :enter assoc-store})

(defn lista-tarefas [request]
  {:status 200 :body @(:store request)})

(defn criar-tarefa-mapa [uuid nome status]
  {:id uuid :nome nome :status status})

(defn criar-tarefa [request]
  (let [uuid (java.util.UUID/randomUUID)
        nome (get-in request [:query-params :nome])
        status (get-in request [:query-params :status])
        tarefa (criar-tarefa-mapa uuid nome status)
        store (:store request)]
    (swap! store assoc uuid tarefa)
    {:status 200 :body {:mensagem "Tarefa registrada com sucesso!"
                        :tarefa   tarefa}}
    ))

(defn funcao-hello [request]
  {:status 200 :body (str "Bem vindo " (get-in request [:query-params :name] "Everybody"))})

(defn delete-tarefa [request]
  (let [store (:store request)
        tarefa-id (get-in request [:path-params :id])
        tarefa-id-uuid (java.util.UUID/fromString tarefa-id)]
    (swap! store dissoc tarefa-id-uuid)
    {:status 200 :body {:mensagem "Removido com sucesso!"}}))

(defn atualiza-tarefa [request]
  (let [tarefa-id (get-in request [:path-params :id])
        tarefa-id-uuid (java.util.UUID/fromString tarefa-id)
        nome (get-in request [:query-params :nome])
        status (get-in request [:query-params :status])
        tarefa (criar-tarefa-mapa tarefa-id-uuid nome status)
        store (:store request)]
    (swap! store assoc tarefa-id-uuid tarefa)
    {:status 200 :body {:mensagem "Tarefa atualizada com sucesso!"
                        :tarefa   tarefa}}))

(def routes (route/expand-routes
              #{["/hello" :get funcao-hello :route-name :hello-world]
                ["/tarefa" :post [db-interceptor criar-tarefa] :route-name :criar-tarefa]
                ["/tarefa" :get [db-interceptor lista-tarefas] :route-name :lista-tarefas]
                ["/tarefa/:id" :delete [db-interceptor delete-tarefa] :route-name :delete-tarefa]
                ["/tarefa/:id" :patch [db-interceptor atualiza-tarefa] :route-name :atualiza-tarefa]}))

(def service-map {::http/routes routes
                  ::http/port   9999
                  ::http/type   :jetty
                  ::http/join?  false})

(defonce server (atom nil))

(defn start-server []
  (reset! server (http/start (http/create-server service-map))))

(defn test-request [verb url]
  (test/response-for (::http/service-fn @server) verb url))

(defn stop-server []
  (http/stop @server))

(defn restart-server []
  (stop-server)
  (start-server))

(try (start-server) (catch Exception e (println "Erro ao executar start" (.getMessage e))))
(try (restart-server) (catch Exception e (println "Erro ao executar restart" (.getMessage e))))

(println "Server started/restarted")

(test-request :get "/hello?name=Isaac")
(test-request :post "/tarefa?nome=Ler&status=pendente")
(test-request :post "/tarefa?nome=Correr&status=concluído")
(test-request :post "/tarefa?nome=Andar&status=cancelado")
(test-request :post "/tarefa?nome=Estudar&status=pendente")
(test-request :post "/tarefa?nome=Assistir&status=concluído")

(println "Listando todas as tarefas")
(clojure.edn/read-string (:body (test-request :get "/tarefa")))
(test-request :delete "/tarefa/4279aedb-41cf-4b42-b1f8-bb4a70d87e40")
(clojure.edn/read-string (:body (test-request :get "/tarefa")))
(test-request :patch "/tarefa/44ea4488-e626-419a-9742-de684761d0d3?nome=Leitura&status=concluída")
;(println @database/store)

