(ns rc2.state-test
  (:require [specljs.core :as specljs]
            [rc2.state :as state]
            [rc2.util :as util])
  (:require-macros [specljs.core :refer [describe it should should= should-not with]]))

(describe
 "apply-state-transforms"
 (it "applies the transforms in sequence"
     (let [state {:a {:b 1}
                  :c {:d {:e [4 5 6]}}}
           transforms [
                       [[:a :b] [:a :b] inc]
                       [[:c :d :e] [:c :d :e] #(mapv inc %1)]
                       [[:a :b] [:c :d :e] #(conj %2 %1)]
                       [[:c :d :e] [:c :d] #(assoc %2 :sum (reduce + %1))]
                       [[] [:f] (constantly "foo")]
                       [[:a :b] [] (fn [in out] out)]
                       ]
           results (state/apply-state-transforms state transforms)]
       (should= 2 (get-in results [:a :b]))
       (should= [5 6 7 2] (get-in results [:c :d :e]))
       (should= (reduce + [5 6 7 2]) (get-in results [:c :d :sum]))
       (should= "foo" (get-in results [:f]))))
 (it "does nothing if there are no transforms"
     (let [state {:a {:b 1}
                  :c {:d {:e [4 5 6]}}}
           transforms []
           results (state/apply-state-transforms state transforms)]
       (should= state results))))

(describe
 "update-task-state"
 (it "does nothing if the task is not complete"
     (let [app-state {:tasks {:pending [1] :complete []}}]
       (should= app-state (state/update-task-state app-state {"state" "processing", "id" 1}))))
 (it "moves the task ID to the complete list if the task is complete"
     (let [app-state {:tasks {:pending [1] :complete []}}]
       (should= {:tasks {:pending [] :complete [1]}}
                (state/update-task-state app-state {"state" "complete", "id" 1})))))

(describe
 "update-plan-animation"
 (it "makes a single step in the direction of the current path vector"
     (let [plan [{:location (util/->world 1 2 0)} {:location (util/->world 1 7 0)}]
           anim-state state/default-animation-state]
       (should= (assoc anim-state :offsets (util/->world 0 5))
                (state/update-plan-animation plan anim-state))))
 (it "continues by a single step in the direction of the current path vector"
     (let [plan [{:location (util/->world 1 2 0)} {:location (util/->world 1 12 0)}]
           anim-state (assoc state/default-animation-state :offsets (util/->world 0 5))]
       (should= (assoc anim-state :offsets (util/->world 0 10))
                (state/update-plan-animation plan anim-state))))
 (it "moves to the next path segment once it oversteps"
     (let [plan [{:location (util/->world 0 0 0)}
                 {:location (util/->world 0 7 0)}
                 {:location (util/->world 0 15 0)}]
           anim-state (assoc state/default-animation-state :offsets (util/->world 0 5))]
       (should= (assoc anim-state :offsets util/origin :index 1)
                (state/update-plan-animation plan anim-state))))
 (it "moves to the first path segment once it completes the route"
     (let [plan [{:location (util/->world 0 0 0)}
                 {:location (util/->world 0 7 0)}
                 {:location (util/->world 0 15 0)}]
           anim-state (assoc state/default-animation-state
                        :offsets (util/->world 0 5) :index 1)]
       (should= (assoc anim-state :offsets util/origin :index 0)
                (state/update-plan-animation plan anim-state)))))
