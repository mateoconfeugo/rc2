(ns rc2.web.server.task-test
  (:require [speclj.core :refer :all]
            [rc2.web.server.task :refer :all]))

(describe
 "make-task"
 (it "adds affinity to the task"
     (should= :serial (:affinity (make-task :move {:destination {:x 1 :y 2 :z 3}}))))
 (it "adds type to the task"
     (should= :move (:type (make-task :move {:destination {:x 1 :y 2 :z 3}}))))
 (it "adds a creation timestamp to the task"
     (should-not (nil? (:created (make-task :move {:destination {:x 1 :y 2 :z 3}}))))))

(describe "do-task!"
  (let [task {:type :test}]
    (it "returns an error if no handler is available"
        (should-contain {:state :failed :errors vector?} (do-task! task {})))
    (it "sets the task state to complete if no exceptions occur"
        (should-contain {:state :complete :result true} (do-task! task {:test (constantly true)})))
    (it "sets the task state to failed if an exception is thrown"
        (should= :failed (:state (do-task! task {:test (fn [_] (throw (Exception. "Testing")))}))))
    (it "includes an error message if an exception is thrown"
        (should= ["Testing"]
                 (:errors (do-task! task {:test (fn [_] (throw (Exception. "Testing")))}))))))

(run-specs)
