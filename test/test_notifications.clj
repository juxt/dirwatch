;; Copyright © 2013, JUXT. All Rights Reserved.
;;
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;;
;; By using this software in any fashion, you are agreeing to be bound by the
;; terms of this license.
;;
;; You must not remove this notice, or any other, from this software.

(ns test-notifications
  (:require
   [clojure.java.io :refer (file delete-file) :as io]
   [clojure.test :refer :all]
   [juxt.dirwatch :refer (watch-dir close-watcher)]))

(deftest test-notifications
  (let [dir (file "tmp" (str (gensym "dirwatch")))
        log (atom [])]
    (try
      (.mkdirs dir)
      (let [ag (watch-dir (fn [ev] (swap! log conj ev)) dir)]
        (let [f (file dir "foo.txt")]
          (spit f "A test file")
          (delete-file f))
        (await ag)
        (await ag) ; to have to await the agent again because it sends to itself
        (is (= 3 (count @log) 3))
        (is (every? map? @log))
        (is (= (map :action @log) [:create :modify :delete]))
        )
      (finally (delete-file dir)))))

(deftest cannot-mistakenly-call-close-in-another-agent
  (let [watcher (watch-dir (constantly nil) (file ""))
        another-agent-mistake (agent 0)]
    (try
      (is (thrown? java.lang.AssertionError
                   (close-watcher another-agent-mistake)))
      (finally (close-watcher watcher)))))
