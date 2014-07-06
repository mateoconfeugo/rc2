(ns rc2.web.server.task-test
  (:require [speclj.core :refer :all]
            [rc2.web.server.task :refer :all]))

(register-task-type! :move (constantly true) :affinity :serial)
(register-task-type! :plan (constantly true) :affinity :parallel)

(describe
 "make-task"
 (it "adds affinity to the task"
     (should= :serial (:affinity (make-task :move {:destination {:x 1 :y 2 :z 3}}))))
 (it "uses type-specific affinity"
     (should= :parallel (:affinity (make-task :plan {}))))
 (it "adds type to the task"
     (should= :move (:type (make-task :move {:destination {:x 1 :y 2 :z 3}}))))
 (it "adds a creation timestamp to the task"
     (should-not (nil? (:created (make-task :move {:destination {:x 1 :y 2 :z 3}}))))))

(describe
 "do-task!"
 (with task {:type :test})
 (it "sets the task state to complete if no exceptions occur"
     (should= :complete (:state (apply hash-map (do-task! @task {:test (constantly true)})))))
 (it "sets the task result to the value of the handler"
     (should (:result (apply hash-map (do-task! @task {:test (constantly true)})))))
 (it "returns an error message if no handler is available"
     (should (vector? (:errors (apply hash-map (do-task! @task {}))))))
 (it "sets task state to failed if no handler is available"
     (should= :failed (:state (apply hash-map (do-task! @task {})))))
 (it "sets the task state to failed if an exception is thrown"
     (should= :failed
              (:state (apply hash-map
                             (do-task! @task {:test (fn [_] (throw (Exception. "Testing")))})))))
 (it "includes an error message if an exception is thrown"
     (should= ["Testing"]
              (:errors (apply hash-map
                              (do-task! @task {:test (fn [_] (throw (Exception. "Testing")))}))))))

(describe
 "get-handlers"
 (with handler (constantly true))
 (it "gets the handler functions associated with each task type as a map"
     (should= {:test handler} (get-handlers {:test {:handler handler :affinity :serial}}))))

(run-specs)
