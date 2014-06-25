(ns rc2.web.server.task-test
  (:use midje.sweet
        rc2.web.server.task))

(facts "About make-task"
  (fact "make-task adds type and affinity fields to the task and carries options forward"
    (make-task :move {:destination {:x 1 :y 2 :z 3}})
    => (contains {:type :move :destination {:x 1 :y 2 :z 3} :affinity :serial}))
  (fact "make-task adds a creation timestamp"
    (nil? (:created (make-task :move {:destination {:x 1 :y 2 :z 3}}))) => false))

(facts "About do-task!"
  (let [task {:type :test}]
    (fact "do-task! returns an error if no handler is available"
      (do-task! task {}) => (contains {:state :failed :errors vector?}))
    (fact "do-task! returns an error if no handler is available"
      (do-task! task {:test identity}) => (contains {:state :complete}))
    (fact "do-task! returns an error if no handler is available"
      (do-task! task {:test (constantly false)}) => (contains {:state :failed}))))
