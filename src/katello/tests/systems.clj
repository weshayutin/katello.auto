(ns katello.tests.systems
  (:refer-clojure :exclude [fn])
<<<<<<< HEAD
  (:require [katello :as kt] 
            (katello [navigation :as nav]
                     [ui :as ui]
                     [rest :as rest]
=======
  (:require (katello [navigation :as nav]
                     [api-tasks :as api]
                     [activation-keys :as ak]
>>>>>>> master
                     [validation :as val]
                     [organizations :as org]
                     [environments :as env]
                     [client :as client]
                     [providers :as provider]
                     [repositories :as repo]
                     [ui-common :as common]
                     [changesets :as changeset]
                     [tasks :refer :all]
                     [systems :as system]                   
                     [gpg-keys :as gpg-key]
                     [conf :refer [*session-user* *session-org* config *environments*]])
            [katello.client.provision :as provision]
<<<<<<< HEAD
            [katello.tests.useful :refer [create-series create-recursive]]
=======
            [clojure.string :refer [blank?]]
            [com.redhat.qe.auto.selenium.selenium :as sel :refer [browser]]
>>>>>>> master
            (test.tree [script :refer [defgroup deftest]]
                       [builder :refer [union]])

            [serializable.fn :refer [fn]]
            [test.assert :as assert]
            [bugzilla.checker :refer [open-bz-bugs]]))


;; Functions

(defn create-test-environment []
  (def test-environment (first *environments*))
  (create-recursive test-environment))

(defn register-new-test-system []
  (-> {:name "newsystem"
       :env test-environment
       :facts (system/random-facts)}
      katello/newSystem
      uniqueify
      rest/create))

(defn verify-system-rename [system]
  (nav/go-to (ui/update system update-in [:name] uniqueify)))

(defn verify-system-appears-on-env-page
  [system]
  (nav/go-to ::system/named-by-environment-page
             {:env (:env system)
              :org (-> system :env :org)
              :system system})
  (assert/is (= (:environment_id system)
<<<<<<< HEAD
                (-> test-environment rest/query :id))))

(defn configure-product-for-pkg-install
  "Creates and promotes a product with fake content repo, returns the
  product."
  [target-env]
  (with-unique [provider (katello/newProvider (:name "custom_provider" :org *session-org*))
                product (katello/newProduct {:name "fake" :provider provider})
                testkey (katello/newGPGKey {:name "mykey" :org *session-org*
                                            :contents (slurp
                                                       "http://inecas.fedorapeople.org/fakerepos/zoo/RPM-GPG-KEY-dummy-packages-generator")})
                repo (katello/newRepository {:name "zoo_repo"
                                             :product product
                                             :gpg-key testkey})]
    (create-recursive target-env)
    (ui/create-all testkey provider product repo)
    (when (rest/is-katello?)
      (changeset/sync-and-promote (list repo) target-env))
    product))
=======
                (api/get-id-by-name :environment test-environment))))

(defn verify-new-system-tooltip
  "Confirms that tooltips in the New System form appear and show the correct messages"
  [icon-locator expected-text]
  (nav/go-to ::system/new-page)
  (browser mouseOver icon-locator)
  (Thread/sleep 2000)
  (assert/is (browser isTextPresent expected-text))
  (browser mouseOut icon-locator)
  (Thread/sleep 2000)
  (assert/is (false? (browser isTextPresent expected-text))))

(defn validate-new-system-link
  [env? env org-name system-name]
  (browser clickAndWait ::system/sys-tab)
  (if env?
    (do
      (env/create env {:org-name org-name})
      (system/create system-name {:sockets "1"
                                  :system-arch "x86_64"}))
    (do
      (assert (not (= "disabled" (subs (browser getAttribute ::system/new-class-attrib) 1 8))))
      (assert (not (blank? (browser getAttribute ::system/new-title-attrib)))))))

(defn verify-sys-count
  "Verify system-count after deleting one or more systems"
  [sys-name system-names count]
  (assert/is (= count (Integer/parseInt (browser getText ::system/total-sys-count))))
  (system/delete sys-name)
  (browser clickAndWait ::system/sys-tab)
  (assert/is (= (dec count) (Integer/parseInt (browser getText ::system/total-sys-count))))
  (system/multi-delete system-names)
  (browser clickAndWait ::system/sys-tab) ; this is a workaround; hitting the systab will refesh the sys-count. See bz #859237
  (assert/is (= 0 (Integer/parseInt (browser getText ::system/total-sys-count)))))

(defn validate-system-facts
  [name cpu arch virt? env]
  (nav/go-to ::system/facts-page {:system-name name})
  (browser click ::system/cpu-expander)
  (assert/is (= cpu (browser getText ::system/cpu-socket)))
  (browser click ::system/network-expander)
  (assert/is (= name (browser getText ::system/net-hostname)))
  (browser click ::system/uname-expander)
  (assert/is (= arch (browser getText ::system/machine-arch)))
  (browser click ::system/virt-expander)
  (if virt?
      (assert/is (= "true" (browser getText ::system/virt-status)))
      (assert/is (= "false" (browser getText ::system/virt-status))))
  (let [details (system/get-details name)]
    (assert/is (= name (details "Name")))
    (assert/is (= arch (details "Arch")))
    (assert/is (= env  (details "Environment")))))

(defn step-to-configure-server-for-pkg-install [product-name target-env]
  (let [provider-name (uniqueify "custom_provider")
        repo-name (uniqueify "zoo_repo")
        org-name "ACME_Corporation"
        testkey (uniqueify "mykey")]
    (org/switch)
    (api/ensure-env-exist target-env {:prior library})
    (let [mykey (slurp "http://inecas.fedorapeople.org/fakerepos/zoo/RPM-GPG-KEY-dummy-packages-generator")]
      (gpg-key/create testkey {:contents mykey}))
    (provider/create {:name provider-name})
    (provider/add-product {:provider-name provider-name
                           :name product-name})
    (repo/add-with-key {:provider-name provider-name
                        :product-name product-name
                        :name repo-name
                        :url "http://inecas.fedorapeople.org/fakerepos/zoo/"
                        :gpgkey testkey})
    (let [products [{:name product-name :repos [repo-name]}]]
      (when (api/is-katello?)
        (changeset/sync-and-promote products library target-env)))))
>>>>>>> master

;; Tests

(defgroup system-tests
  :group-setup create-test-environment
  :blockers (open-bz-bugs "717408" "728357")

  (deftest "Rename an existing system"
    :blockers (open-bz-bugs "729364")
    (verify-system-rename (register-new-test-system)))

  (deftest "System-details: Edit system-name"
    :data-driven true
    ;:blockers (open-bz-bugs "917033")
    
    (fn [new-system save? & [expected-err]]
      (let [system (uniqueify "newsystem")]
        (system/create system {:sockets "1"
                               :system-arch "x86_64"})
        (if expected-err
          (expecting-error (common/errtype expected-err)
                           (system/edit-sysname system new-system save?))
          (system/edit-sysname system new-system save?))))
    
    [["yoursys" false]
     ["test.pnq.redhat.com" true]
     [(random-string (int \a) (int \z) 256) true :katello.notifications/system-name-255-char-limit]
     [(random-string (int \a) (int \z) 255) true]])

  (deftest "System-details: Edit Description"
    :data-driven true
    
    (fn [new-description save? & [expected-err]]
      (let [system (uniqueify "newsystem")]
        (system/create system {:sockets "1"
                               :system-arch "x86_64"})
        (if expected-err
          (expecting-error (common/errtype expected-err)
                           (system/edit-sys-description system new-description save?))
          (system/edit-sys-description system new-description save?))))
    
    [["cancel description" false]
     ["System Registration Info" true]
     [(random-string (int \a) (int \z) 256) true :katello.notifications/sys-description-255-char-limit]
     [(random-string (int \a) (int \z) 255) true]])

  (deftest "Verify system appears on Systems By Environment page in its proper environment"
    :blockers (open-bz-bugs "738054")

    (verify-system-appears-on-env-page (register-new-test-system)))


  (deftest "Subscribe a system to a custom product"
    :blockers (union (open-bz-bugs "733780" "736547" "784701")
                     rest/katello-only)

    (with-unique [provider (katello/newProvider {:name "subscr-prov" :org *session-org*})
                  product (katello/newProduct {:name "subscribe-me"
                                               :provider provider})]
      (rest/create provider)
      (rest/create product)
      (ui/update (register-new-test-system) assoc :products (list product))))

  (deftest "Set a system to autosubscribe with no SLA preference"
    :blockers (open-bz-bugs "845261")
    (ui/update (register-new-test-system) assoc
               :auto-attach true
               :service-level "No Service Level Preference"))

  (deftest "Remove System"
    (ui/delete (register-new-test-system)))

  (deftest "Remove multiple systems"
<<<<<<< HEAD
    (let [systems (->> {:name "mysys"
                        :sockets "1"
                        :system-arch "x86_64"
                        :env test-environment} katello/newSystem uniques (take 3))]
      (rest/create-all systems)
      (system/multi-delete systems)))
=======
    (let [system-names (take 3 (unique-names "mysys"))]
      (create-multiple-systems system-names)
      (system/multi-delete system-names)))
  
  (deftest "Remove systems and validate sys-count"
    (with-unique [env  "dev"
                  org-name "test-sys"
                  system-name "mysystem"]
      (org/create org-name)
      (env/create env {:org-name org-name})
      (org/switch org-name)
      (let [system-names (take 3 (unique-names "mysys"))]
        (create-multiple-systems system-names)
        (system/create system-name {:sockets "1"
                                    :system-arch "x86_64"})
        (let [syscount (-> system-names count inc)]
          (verify-sys-count system-name system-names syscount)))))
  
  (deftest "Remove System: with yes-no confirmation"
    :data-driven true
    
    (fn [del?]
      (with-unique [system-name "mysystem"]
        (system/create system-name {:sockets "1"
                                    :system-arch "x86_64"})
        (system/confirm-yes-no-to-delete system-name del?)))
    
    [[false]
     [true]])
  
  (deftest "System Details: Add custom info"
    :blockers (open-bz-bugs "919373")
    (with-unique [system-name "mysystem"]
      (let [key-name "Hypervisor"
            key-value "KVM"]
        (system/create system-name {:sockets "1"
                                    :system-arch "x86_64"})
        (system/add-custom-info system-name key-name key-value))))
  
  (deftest "System Details: Update custom info"
    :blockers (open-bz-bugs "919373")
    (with-unique [system-name "mysystem"]
      (let [key-name "Hypervisor"
            key-value "KVM"
            new-key-value "Xen"]
        (system/create system-name {:sockets "1"
                                    :system-arch "x86_64"})
        (system/add-custom-info system-name key-name key-value)
        (system/update-custom-info system-name key-name new-key-value))))
>>>>>>> master

  (deftest "System Details: Delete custom info"
    :blockers (open-bz-bugs "919373")
    (with-unique [system-name "mysystem"]
      (let [key-name "Hypervisor"
            key-value "KVM"]
        (system/create system-name {:sockets "1"
                                    :system-arch "x86_64"})
        (system/add-custom-info system-name key-name key-value)
        (system/remove-custom-info system-name key-name))))

  (deftest "System name is required when creating a system"
    (expecting-error val/name-field-required
                     (system/create "")))

  (deftest "New System Form: tooltips pop-up with correct information"
    :data-driven true
    verify-new-system-tooltip
    [[::system/ram-icon "The amount of RAM memory, in megabytes (MB), which this system has"]
     [::system/sockets-icon "The number of CPU Sockets or LPARs which this system uses"]])
  
  (deftest "Add system from UI"
    :data-driven true
    
    (fn [virt?]
      (with-unique [env "dev"
                    system-name "mysystem"]
        (let [arch "x86_64"
              cpu "2"]
          (env/create env {:org-name (@config :admin-org)})
          (system/create-with-details system-name {:sockets cpu
                                                   :system-arch arch :type-is-virtual? virt? :env env})
          (validate-system-facts system-name cpu arch virt? env))))
    
    [[false]
     [true]])
  
  (deftest "Add system when no env is available for selected org"
    :data-driven true
    (fn [env?]
      (with-unique [env "dev"
                    org-name "test-sys"
                    system-name "mysystem"]
        (org/create org-name)
        (org/switch org-name)
        (validate-new-system-link env? env org-name system-name)
        (org/switch (@config :admin-org))))
  
  [[true]
   [false]])
  
  (deftest "Check whether the details of registered system are correctly displayed in the UI"
    ;;:blockers no-clients-defined

    (provision/with-client "sys-detail"
      ssh-conn
      (client/register ssh-conn
                       {:username (:name *session-user*)
                        :password (:password *session-user*)
                        :org (:name *session-org*)
                        :env (:name test-environment)
                        :force true})
      (let [hostname (client/my-hostname ssh-conn)
            details (system/get-details hostname)]
        (assert/is (= (client/get-distro ssh-conn)
                      (details "OS")))
<<<<<<< HEAD
        (assert/is (every? not-empty (vals details))))))
=======
        (assert/is (= (client/get-ip-address ssh-conn)
                            (system/get-ip-addr hostname)))
        (assert/is (every? (complement empty?) (vals details))))))
  
  (deftest "Review Facts of registered system"
    ;;:blockers no-clients-defined
    (provision/with-client "sys-facts"
      ssh-conn
      (let [target-env (first *environments*)]
        (client/register ssh-conn
                         {:username *session-user*
                          :password *session-password*
                          :org (@config :admin-org)
                          :env target-env
                          :force true})
        (let [hostname (client/my-hostname ssh-conn)
              facts (system/get-facts hostname)]
          (system/expand-collapse-facts-group hostname)
          (assert/is (every? (complement empty?) (vals facts)))))))
  
  (deftest "System-Details: Validate Activation-key link"
    (with-unique [system-name "mysystem"
                  key-name "auto-key"]
      (let [target-env (first *environments*)]
        (api/ensure-env-exist target-env {:prior library})
        (ak/create {:name key-name
                    :description "my description"
                    :environment target-env})
        (provision/with-client "ak-link" ssh-conn
          (client/register ssh-conn
                           {:org "ACME_Corporation"
                            :activationkey key-name})
          (let [mysys (client/my-hostname ssh-conn)]
            (system/validate-activation-key-link mysys key-name))))))
>>>>>>> master

  (deftest "Install package group"
    :data-driven true
    :description "Add package and package group"
    :blockers rest/katello-only

    (fn [package-name]
      (let [target-env (first *environments*)
            
            system (uniqueify {kt/newSystem {:name "pkg_install", :env target-env}})
            product (configure-product-for-pkg-install target-env)]
        
        (provision/with-client (:name system)
          ssh-conn
          (client/register ssh-conn
                           {:username (:name *session-user*)
                            :password (:password *session-user*)
                            :org (-> product :provider :org :name)
                            :env (:name target-env)
                            :force true})
          (let [mysys (client/my-hostname ssh-conn)]
            (client/subscribe ssh-conn (system/pool-id system product))
            (client/run-cmd ssh-conn "rpm --import http://inecas.fedorapeople.org/fakerepos/zoo/RPM-GPG-KEY-dummy-packages-generator")
            (system/add-package mysys package-name)))))

    [[{:package "cow"}]
     [{:package-group "birds"}]])
  
  (deftest "Re-registering a system to different environment"
<<<<<<< HEAD
    (let [[env-dev env-test :as envs] (->> {:name "env" :org *session-org*}
                                           katello/newEnvironment
                                           create-series
                                           (take 2))]
      (provision/with-client "reg-with-env-change"
        ssh-conn
        (let [mysys (-> {:name (client/my-hostname ssh-conn)}
                        katello/newSystem
                        rest/read)]
          (doseq [env [env-dev env-test]]
            (client/register ssh-conn
                             {:username (:name *session-user*)
                              :password (:password *session-user*)
                              :org (-> env :org :name)
                              :env (:name env)
                              :force true})
            (assert/is (= {:name env} (system/environment mysys))))
          (assert/is (not= (:environment_id mysys)
                           (rest/get-id env-dev)))))))
=======
    (with-unique [env-dev  "dev"
                  env-test  "test"
                  product-name "fake"]
      (let [org-name "ACME_Corporation"]
        (doseq [env [env-dev env-test]]
          (env/create env {:org-name org-name}))
        (org/switch org-name)
        (provision/with-client "reg-with-env-change"
          ssh-conn
          (let [mysys (client/my-hostname ssh-conn)]
            (doseq [env [env-dev env-test]]
              (client/register ssh-conn
                               {:username *session-user*
                                :password *session-password*
                                :org org-name
                                :env env
                                :force true})
              (assert/is (= env (system/environment mysys))))
            (assert/is (not= (map :environment_id (api/get-by-name :system mysys))
                             (map :id (api/get-by-name :environment env-dev)))))))))
  
  
  (deftest  "Registering a system from CLI and consuming contents from UI"
    (let [target-env (first *environments*)
          org-name "ACME_Corporation"
          product-name (uniqueify "fake")]
      (step-to-configure-server-for-pkg-install product-name target-env)
      (provision/with-client "consume-content"
        ssh-conn
        (client/register ssh-conn
                         {:username *session-user*
                          :password *session-password*
                          :org org-name
                          :env target-env
                          :force true})
        (let [mysys (client/my-hostname ssh-conn)]
          (system/subscribe {:system-name mysys
                             :add-products product-name})
          (client/sm-cmd ssh-conn :refresh)
          (let [cmd (format "subscription-manager list --consumed | grep -o %s" product-name)
                result (client/run-cmd ssh-conn cmd)]
            (assert/is (->> result :exit-code (= 0))))))))
>>>>>>> master
    
  
  (deftest "Install package after moving a system from one env to other"
    (let [[env-dev env-test :as envs] (->> {:name "env" :org *session-org*}
                                           katello/newEnvironment
                                           create-series
                                           (take 2))
          product (configure-product-for-pkg-install env-dev)
          package (katello/newPackage {:name "cow" :product product})]
      (provision/with-client "env_change"
        ssh-conn
        (client/register ssh-conn
                         {:username (:name *session-user*)
                          :password (:password *session-user*)
                          :org (-> env-dev :org :name)
                          :env (:name env-dev)
                          :force true})
        (let [mysys (-> {:name (client/my-hostname ssh-conn)}
                        katello/newSystem
                        rest/read)]
          (assert/is (= (:name env-dev) (system/environment mysys)))
          (ui/update mysys assoc :env env-test)
          (assert/is (= (:name env-test) (system/environment mysys)))
          (client/subscribe ssh-conn (system/pool-id mysys product))
          (client/run-cmd ssh-conn "rpm --import http://inecas.fedorapeople.org/fakerepos/zoo/RPM-GPG-KEY-dummy-packages-generator")
          (client/sm-cmd ssh-conn :refresh)
          (client/run-cmd ssh-conn "yum repolist")
          (expecting-error [:type :katello.systems/package-install-failed]
                           (ui/update mysys update-in [:packages] (fnil conj #{}) package))
          (let [cmd_result (client/run-cmd ssh-conn "rpm -q cow")]
            (assert/is (->> cmd_result :exit-code (= 1)))))))))
