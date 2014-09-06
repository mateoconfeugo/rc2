(ns rc2.api-test
  (:require [specljs.core :as specljs]
            [rc2.api :as api])
  (:require-macros [specljs.core :refer [describe it should should= should-not]]))

(describe
 "keywordize"
 (it "converts all the keys in a map from strings to keywords"
     (should= {:foo "bar"} (api/keywordize {"foo" "bar"}))
     (should= {:foo "bar" :baz 3} (api/keywordize {"foo" "bar" :baz 3}))
     (should= {:foo "bar" :baz {:quux 3}} (api/keywordize {"foo" "bar" "baz" {"quux" 3}}))
     (should= {:foo "bar" :baz [{:quux 3}]} (api/keywordize {"foo" "bar" "baz" [{"quux" 3}]}))
     (should= {:foo "bar" :baz '({:quux 3})} (api/keywordize {"foo" "bar" "baz" '({"quux" 3})}))
     (should= {:foo "bar" :baz [1 2 3 4]} (api/keywordize {"foo" "bar" "baz" [1 2 3 4]}))
     (should= [{:foo "bar" :baz [1 2 3 4]}] [(api/keywordize {"foo" "bar" "baz" [1 2 3 4]})])))
