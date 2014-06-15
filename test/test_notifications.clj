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


(defmacro temp-directory []
  `(doto (file (System/getProperty "java.io.tmpdir")
               ~(str (gensym "dirwatch")))
     (.mkdirs)))

(defn notifications-fn []
  (let [log (atom [])]
    (fn
      ([ev] (swap! log conj ev))
      ([] @log))))

(deftest test-notifications
  (let [dir (temp-directory)
        log (notifications-fn)
        ag (watch-dir log dir)]
    (try
      (let [f (file dir "foo.txt")]
        (spit f "A test file")
        (delete-file f)

        (await ag)
        (await-for 2000 ag) ;; Sometimes an extra call to await-for is
                            ;; needed to receive all events.
        (is (= (count (log)) 3))
        (is (every? map? (log)))
        (is (= (map :action (log)) [:create :modify :delete])))
      (finally (delete-file dir)))))

(deftest cannot-mistakenly-call-close-in-another-agent
  (let [watcher (watch-dir (constantly nil) (temp-directory))
        another-agent-mistake (agent 0)]
    (try
      (is (thrown? java.lang.AssertionError
                   (close-watcher another-agent-mistake)))
      (finally (close-watcher watcher)))))

(defn rotate-fn
  "Returns a function which for each invocation executes one of the
  provided functions. Once all the provided functions have been
  invoked, it starts from the first one again."
  [& fns]
  (let [a (atom fns)]
    (fn [& args]
      (let [f (first @a)]
        (swap! a (comp (fnil identity fns)
                       next))
        (apply f args)))))

(deftest if-the-reporting-function-sends-an-exception-it-must-keep-watching
  (let [dir (temp-directory)
        log (notifications-fn)
        watcher (watch-dir (apply rotate-fn
                                  #(throw (ex-info "Intended error" %))
                                  (repeat log))
                           dir)
        f (file dir "foo.txt")]
    (try
      (spit f "A test file")
      (delete-file f)

      (await-for 250 watcher)
      (await-for 2000 watcher) ;; Sometimes an extra call to await-for
                               ;; is needed to receive all events.

      (is (= 2 (count (log)))) ;; In the first event an exception was thrown
      (is (every? map? (log)))
      (is (= (map :action (log)) [:modify :delete]))
      (finally (close-watcher watcher)
               (delete-file dir)))))
