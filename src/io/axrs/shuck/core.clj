(ns io.axrs.shuck.core
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]))

(def ^:dynamic *print-out* false)
(def ^:dynamic *print-err* false)
(def ^:dynamic *print-command* false)
(def ^:private -lock)

(defn- sync-println [& more]
  (locking -lock (apply println more)))

;TODO sh! which throws run-time if cli fails
(defn sh
  "Executes shell command, capturing output, duration and exit-codes.
  Note: Remember to call `io.axrs.shuck.core/done` before process exit to cleanup any running agents"
  [& args]
  (when *print-command*
    (apply sync-println args))
  (let [start (System/currentTimeMillis)
        printerr (if *print-err*
                   (fn [& more]
                     (sync-println (str (char 27) "[31m" (apply str more) (char 27) "[0m")))
                   (constantly nil))
        printout (if *print-out* sync-println (constantly nil))
        p-builder (ProcessBuilder. args)
        process (.start p-builder)
        err (transient [])
        output (transient [])
        consume (fn [t-col print input-stream]
                  (future (with-open [output-reader (io/reader input-stream)]
                            (loop []
                              (when-let [line (.readLine output-reader)]
                                (print line)
                                (conj! t-col line)
                                (recur))))))
        f1 (consume output printout (.getInputStream process))
        f2 (consume err printerr (.getErrorStream process))]
    (.waitFor process)
    @f1
    @f2
    {:exit     (.exitValue process)
     :duration (- (System/currentTimeMillis) start)
     :err      (str/join \newline (persistent! err))
     :out      (str/join \newline (persistent! output))}))

(defn done []
  (shutdown-agents))

(defmacro with-print-out [& body]
  `(binding [*print-err* true
             *print-out* true]
     ~@body))

(defmacro with-print-command [& body]
  `(binding [*print-command* true]
     ~@body))

(comment
  (binding [*print-out* true
            *print-err* true]
    (-> (sh "npm" "install")
        :out
        (clojure.pprint/pprint))
    (done)))
