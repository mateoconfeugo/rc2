(ns test.main_test
  (:require [specljs.core :as specljs]
            [rc2.main :as main])
  (:require-macros [specljs.core :refer [describe it should should= should-not]]))

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
           results (main/apply-state-transforms state transforms)]
       (should= 2 (get-in results [:a :b]))
       (should= [5 6 7 2] (get-in results [:c :d :e]))
       (should= (reduce + [5 6 7 2]) (get-in results [:c :d :sum]))
       (should= "foo" (get-in results [:f]))))
 (it "does nothing if there are no transforms"
     (let [state {:a {:b 1}
                  :c {:d {:e [4 5 6]}}}
           transforms []
           results (main/apply-state-transforms state transforms)]
       (should= state results))))

(describe
 "distance"
 (it "calculates the cartesian distance between two points"
     (should= 2 (main/distance (main/->world 0 0) (main/->world 2 0)))
     (should= 2 (main/distance (main/->world 0 0) (main/->world 0 2)))
     (should= 5 (main/distance (main/->world 0 0) (main/->world 4 3)))))
