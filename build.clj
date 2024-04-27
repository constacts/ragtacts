(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.string :as string]
            [clojure.tools.deps :as t]
            [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'com.constacts/ragtacts)
(def version "0.1.0-SNAPSHOT")
#_; alternatively, use MAJOR.MINOR.COMMITS:
  (def version (format "1.0.%s" (b/git-count-revs nil)))
(def main-cls (string/join "." (filter some? [(name lib) "main"])))
(def target-dir "target")
(def class-dir (str target-dir "/" "classes"))
(def uber-file (format "%s/%s-standalone.jar" target-dir (name lib)))
(def basis (b/create-basis {:project "deps.edn"}))

(defn test "Run all the tests." [opts]
  (println "\nRunning tests...")
  (let [basis    (b/create-basis {:aliases [:test]})
        combined (t/combine-aliases basis [:test])
        cmds     (b/java-command
                  {:basis basis
                   :java-opts (:jvm-opts combined)
                   :main      'clojure.main
                   :main-args ["-m" "cognitect.test-runner"]})
        {:keys [exit]} (b/process cmds)]
    (when-not (zero? exit) (throw (ex-info "Tests failed" {}))))
  opts)

(defn- jar-opts [opts]
  (assoc opts
         :lib lib :version version
         :jar-file (format "target/%s-%s.jar" lib version)
         :scm {:tag (str "v" version)}
         :basis (b/create-basis {})
         :class-dir class-dir
         :target "target"
         :src-dirs ["src"]))

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (test opts)
  (b/delete {:path "target"})
  (let [opts (jar-opts opts)]
    (println "\nWriting pom.xml...")
    (b/write-pom opts)
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["resources" "src"] :target-dir class-dir})
    (println "\nBuilding JAR...")
    (b/jar opts))
  opts)

(defn install "Install the JAR locally." [opts]
  (let [opts (jar-opts opts)]
    (b/install opts))
  opts)

(defn uber [args]
  (println "Compiling Clojure...")
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (println "\nCopying source...")
  (b/copy-dir {:src-dirs ["resources" "src"] :target-dir class-dir})
  (println "Making uberjar...")
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :main main-cls
           :basis basis
           :exclude [#"^META-INF/license/LICENSE\.boringssl\.txt$"
                     #"^META-INF/license/LICENSE\.mvn-wrapper\.txt$"
                     #"^META-INF/license/LICENSE\.aix-netbsd\.txt$"
                     #"^META-INF/license/LICENSE\.tomcat-native\.txt$"]}))

(defn deploy "Deploy the JAR to Clojars." [opts]
  (let [{:keys [jar-file] :as opts} (jar-opts opts)]
    (dd/deploy {:installer :remote :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))}))
  opts)
