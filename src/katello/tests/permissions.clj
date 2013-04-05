(ns katello.tests.permissions
  (:refer-clojure :exclude [fn])
  (:require [katello :as kt]
            (katello [ui :as ui]
                     [rest :as rest]
                     [ui-common :as common]
                     [login :refer [login logged-in?]]
                     [navigation :as nav]
                     [conf :as conf]
<<<<<<< HEAD
                     [tasks :refer [with-unique uniqueify expecting-error random-string]]
                     [systems :as system])
            [test.tree.script :refer [deftest defgroup]]
=======
                     [tasks :refer :all]
                     [providers :as providers]
                     [environments :as environment]
                     [system-templates :as template]
                     [activation-keys :as ak]
                     [roles :as role]
                     [login :as login]
                     [systems :as system]
                     [fake-content :as fake]                    
                     [repositories :as repo]
                     [gpg-keys :as gpg-key]
                     [users :as user]
                     [organizations :as organization])
            [test.tree.script :refer :all] 
>>>>>>> master
            [serializable.fn :refer [fn]]
            [test.assert :as assert]
            [bugzilla.checker :refer [open-bz-bugs]])
  (:import [com.thoughtworks.selenium SeleniumException]))

;; Functions

(def denied-access? (fn [r] (-> r class (isa? Throwable))))

(def has-access? (complement denied-access?))

(defn- try-all [fs]
  (zipmap fs (doall (for [f fs]
                      (try (f)
                           (catch Exception e e))))))

(defn- navigate-fn [page & [org]]
  (fn [] (nav/go-to page {:org (or org conf/*session-org*)})))

(defn- navigate-all [pages & [org]]
  (for [page pages]
    (navigate-fn page org)))


(defn verify-role-access
  [& {:keys [role allowed-actions disallowed-actions]}]
  (with-unique [user (kt/newUser {:name "user-perm"
                                  :password "password"
                                  :email "foo@my.org"})]
    (let [try-all-with-user (fn [actions]
                              (binding [conf/*session-user* user]
                                (login)
                                (try-all actions)))]
      (rest/create user)
      (when role
        (ui/update role assoc :users (list user)))
      (try
        (let [with-perm-results (try-all-with-user allowed-actions)
              no-perm-results (try-all-with-user disallowed-actions)]
          (assert/is (and (every? denied-access? (vals no-perm-results))
                          (every? has-access? (vals with-perm-results)))))
        (finally
          (login)))))) ;;as original session user

(defn verify-access
  "Assigns a new user to a new role with the given permissions. That
   user is logs in, and tries the allowed-actions to ensure they all
   succeed, finally tries disallowed-actions to make sure they all
   fail. If any setup needs to be done to set up an action, a no-arg
   function can be passed in as setup. (for instance, if you're
   testing a permission to modify users, you need a test user to
   attempt to modify)."
  [& {:keys [permissions allowed-actions disallowed-actions setup]}] {:pre [permissions]}
  (with-unique [role (kt/newRole {:name "permtest"})]
    (when setup (setup))
    (ui/create role)
    (ui/update role assoc :permissions (map kt/newPermission permissions))
    (apply verify-role-access [:role role :allowed-actions allowed-actions :disallowed-actions disallowed-actions])))

(def create-an-env
  (fn [] (-> {:name "blah" :org conf/*session-org*} kt/newEnvironment uniqueify ui/create)))

(def create-an-org
  (fn [] (-> {:name "org"} kt/newOrganization uniqueify ui/create)))

(def create-an-ak ;;FIXME
  (fn [] (ui/create (kt/newActivationKey {:name (uniqueify "blah")
                                          :env (first conf/*environments*)}))))

(def create-a-st ;;FIXME
  (fn [] (ui/create {:name (uniqueify "blah")})))

(def create-a-user
  (fn [] (-> {:name "blah" :password "password" :email "me@me.com"} kt/newUser uniqueify ui/create)))

(def global (kt/newOrganization {:name "Global Permissions"}))

(defn- delete-system-data [sysverb]
  (fn [] (with-unique [system (kt/newSystem {:sockets "1", :system-arch "x86_64", :facts (system/random-facts)})]
           [:permissions [[{:org global, :name "blah2", :resource-type "Organizations", :verbs [sysverb]}]]
            :setup (fn [] (rest/create system))
            :allowed-actions [(fn [] (ui/delete system))]])))

(def access-test-data
<<<<<<< HEAD
  (let [baseuser (kt/newUser {:name "user" :password "password" :email "me@me.com"})
        baseorg (kt/newOrganization {:name "org"})]
    [(fn [] [:permissions [{:org global, :resource-type "Organizations", :verbs ["Read Organization"], :name "orgaccess"}]
             :allowed-actions [(fn [] (nav/go-to conf/*session-org*))]
             :disallowed-actions (conj (navigate-all [:katello.systems/page :katello.sync-management/status-page
                                                      :katello.providers/custom-page :katello.system-templates/page
                                                      :katello.changesets/page])
                                       (fn [] (ui/create (uniqueify baseorg)))
                                       create-an-env)])

     (fn [] (with-unique [org (kt/newOrganization {:name "org-create-perm"})
                          prov (kt/newProvider {:name "myprov" :org org})]
              [:permissions [{:org global, :resource-type "Organizations", :verbs ["Administer Organization"], :name "orgcreate"}]
               :allowed-actions [(fn [] (ui/create org)) (fn [] (ui/delete org)) create-an-env]
               :disallowed-actions (conj (navigate-all [:katello.systems/page :katello.sync-management/status-page
                                                        :katello.providers/custom-page :katello.system-templates/page
                                                        :katello.changesets/page]
                                                       org )
                                         (fn [] (ui/create prov))
                                         (fn [] (rest/create prov)))]))

     (vary-meta
      (fn [] [:permissions [{:org global, :resource-type "Organizations", :verbs ["Register Systems"], :name "systemreg"}]
              :allowed-actions [(fn [] (-> {:name "system"
                                            :env (first conf/*environments*)
                                            :facts (system/random-facts)}
                                           kt/newSystem uniqueify rest/create))
                                (navigate-fn :katello.systems/page)]
              :disallowed-actions (conj (navigate-all [:katello.providers/custom-page :katello.organizations/page])
                                        create-an-org)])
      assoc :blockers (open-bz-bugs "757775"))

     (vary-meta
      (fn [] [:permissions [{:org global, :resource-type "Activation Keys", :verbs ["Read Activation Keys"], :name "akaccess"}]
              :allowed-actions [(navigate-fn :katello.activation-keys/page)]
              :disallowed-actions (conj (navigate-all [:katello.organizations/page
                                                       :katello.systems/page :katello.systems/by-environments-page
                                                       :katello.repositories/redhat-page])
                                        create-an-ak)])
      assoc :blockers (open-bz-bugs "757817"))

     (vary-meta
      (fn [] [:permissions [{:org global, :resource-type "Activation Keys", :verbs ["Administer Activation Keys"], :name "akmang"}]
              :allowed-actions [create-an-ak]
              :disallowed-actions (conj (navigate-all [:katello.organizations/page
                                                       :katello.systems/page :katello.systems/by-environments-page
                                                       :katello.repositories/redhat-page])
                                        create-an-org)])
      assoc :blockers (open-bz-bugs "757817"))

     (fn [] [:permissions [{:org global, :resource-type "System Templates", :verbs ["Read System Templates"], :name "stread"}]
             :allowed-actions [(navigate-fn :katello.system-templates/page)]
             :disallowed-actions (conj (navigate-all [:katello.systems/page :katello.organizations/page
                                                      :katello.providers/custom-page :katello.sync-management/status-page
                                                      :katello.changesets/page])
                                       create-a-st
                                       create-an-org
                                       create-an-env)])

     (fn [] [:permissions [{:org global
                            :resource-type "System Templates"
                            :verbs ["Administer System Templates"]
                            :name "stmang"}]
             :allowed-actions [create-a-st]
             :disallowed-actions (conj (navigate-all [:katello.systems/page :katello.organizations/page
                                                      :katello.providers/custom-page :katello.sync-management/status-page
                                                      :katello.changesets/page])
                                       create-an-org
                                       create-an-env)])

     (fn [] [:permissions [{:org global, :resource-type "Users", :verbs ["Read Users"], :name "userread"}]
             :allowed-actions [(navigate-fn :katello.users/page)]
             :disallowed-actions (conj (navigate-all [:katello.systems/page :katello.organizations/page :katello.roles/page
                                                      :katello.changesets/page])
                                       create-an-org
                                       create-an-env
                                       create-a-user)])

     (fn [] (with-unique [user baseuser]
              [:setup (fn [] (rest/create user))
               :permissions [{:org global, :resource-type "Users", :verbs ["Modify Users"], :name "usermod"}]
               :allowed-actions [(fn [] (ui/update user assoc :email "blah@me.com"))]
               :disallowed-actions (conj (navigate-all [:katello.systems/page :katello.organizations/page :katello.roles/page
                                                        :katello.changesets/page])
                                         (fn [] (with-unique [cannot-delete baseuser]
                                                  (ui/create cannot-delete)
                                                  (ui/delete cannot-delete))))]))

     (fn [] (with-unique [user baseuser]
              [:permissions [{:org global, :resource-type "Users", :verbs ["Delete Users"], :name "userdel"}]
               :setup (fn [] (rest/create user))
               :allowed-actions [(fn [] (ui/delete user))]
               :disallowed-actions (conj (navigate-all [:katello.systems/page :katello.organizations/page :katello.roles/page
                                                        :katello.changesets/page])
                                         create-a-user)]))

     (fn [] (with-unique [org baseorg]
              [:permissions [{:org conf/*session-org*, :resource-type "Organizations", :verbs ["Read Organization"], :name "orgaccess"}]
               :setup (fn [] (rest/create org))
               :allowed-actions [(fn [] (nav/go-to conf/*session-org*))]
               :disallowed-actions (conj (navigate-all [:katello.systems/page :katello.sync-management/status-page
                                                        :katello.providers/custom-page :katello.system-templates/page
                                                        :katello.changesets/page] )
                                         (fn [] (nav/go-to org)))]))

     (fn [] (with-unique [org baseorg
                          env (kt/newEnvironment {:name "blah" :org org})]
              [:permissions [{:org org, :resource-type :all, :name "orgadmin"}]
               :setup (fn [] (rest/create org))
               :allowed-actions (conj (navigate-all [:katello.systems/page :katello.sync-management/status-page
                                                     :katello.providers/custom-page :katello.system-templates/page
                                                     :katello.changesets/page]
                                                    org)
                                      (fn [] (nav/go-to org))
                                      (fn [] (ui/create env)))
               :disallowed-actions [(fn [] (nav/go-to conf/*session-org*))]]))

     (fn [] (let [url-fn #(str "/katello/" %)
                  nav-fn (comp partial partial nav/returns-403? url-fn)]
              [:permissions []
               :allowed-actions (map nav-fn ["users"])
               :disallowed-actions (map nav-fn ["subscriptions" "systems" "systems/environments" "system_groups"
                                                "roles" "sync_management/index" "content_search" "system_templates"
                                                "organizations" "providers"])]))

     (delete-system-data "Read Systems")
     (delete-system-data "Delete Systems")]))
=======
  [(fn [] [:permissions [{:org "Global Permissions"
                         :permissions [{:resource-type "Organizations"
                                        :verbs ["Read Organization"]
                                        :name "orgaccess"}]}]
          :allowed-actions [(access-org (@conf/config :admin-org))]
          :disallowed-actions (conj (navigate-all :katello.systems/page :katello.sync-management/status-page
                                                  :katello.providers/custom-page :katello.system-templates/page
                                                  :katello.changesets/page )
                                    (fn [] (organization/create (uniqueify "cantdothis")))
                                    create-an-env)])


   
   (fn [] (let [org-name (uniqueify "org-create-perm")] ;;due to bz 756252 'create' means manage
           [:permissions [{:org "Global Permissions"
                           :permissions [{:resource-type "Organizations"
                                          :verbs ["Administer Organization"]
                                          :name "orgcreate"}]}]
            :allowed-actions [(fn [] (organization/create org-name {:description "mydescription"}))
                              (fn [] (organization/delete org-name))
                              create-an-env]
            :disallowed-actions (conj (navigate-all :katello.systems/page :katello.sync-management/status-page
                                                    :katello.providers/custom-page :katello.system-templates/page
                                                    :katello.changesets/page )
                                      (fn [] (providers/create {:name "myprov"}))
                                      (fn [] (api/create-provider "myprov")))]))
   
   
   (vary-meta
    (fn [] [:permissions [{:org "Global Permissions"
                          :permissions [{:resource-type "Organizations"
                                         :verbs ["Register Systems"]
                                         :name "systemreg"}]}]
           :allowed-actions [(fn [] (api/with-env (first conf/*environments*)
                                     (api/create-system (uniqueify "system") {:facts (api/random-facts)})))
                             (navigate-fn :katello.systems/page)]
           :disallowed-actions (conj (navigate-all :katello.providers/custom-page :katello.organizations/page)
                                     (fn [] (organization/create (uniqueify "cantdothis"))))])
    assoc :blockers (open-bz-bugs "757775"))
   
   (vary-meta
    (fn [] [:permissions [{:org "Global Permissions"
                          :permissions [{:resource-type "Activation Keys"
                                         :verbs ["Read Activation Keys"]
                                         :name "akaccess"}]}]
           :allowed-actions [(navigate-fn :katello.activation-keys/page)]
           :disallowed-actions (conj (navigate-all :katello.organizations/page
                                                   :katello.systems/page :katello.systems/by-environments-page
                                                   :katello.repositories/redhat-page)
                                     create-an-ak)])
    assoc :blockers (open-bz-bugs "757817"))
   
   (vary-meta
    (fn [] [:permissions [{:org "Global Permissions"
                          :permissions [{:resource-type "Activation Keys"
                                         :verbs ["Administer Activation Keys"]
                                         :name "akmang"}]}]
           :allowed-actions [create-an-ak]
           :disallowed-actions (conj (navigate-all :katello.organizations/page
                                                   :katello.systems/page :katello.systems/by-environments-page
                                                   :katello.repositories/redhat-page)
                                     (fn [] (organization/create (uniqueify "cantdothis"))))])
    assoc :blockers (open-bz-bugs "757817"))

   (fn [] [:permissions [{:org "Global Permissions"
                         :permissions [{:resource-type "System Templates"
                                        :verbs ["Read System Templates"]
                                        :name "stread"}]}]
          :allowed-actions [(navigate-fn :katello.system-templates/page)]
          :disallowed-actions (conj (navigate-all :katello.systems/page :katello.organizations/page
                                                  :katello.providers/custom-page :katello.sync-management/status-page
                                                  :katello.changesets/page)
                                    create-a-st
                                    (fn [] (organization/create (uniqueify "cantdothis")))
                                    create-an-env)])

   (fn [] [:permissions [{:org "Global Permissions"
                         :permissions [{:resource-type "System Templates"
                                        :verbs ["Administer System Templates"]
                                        :name "stmang"}]}]
          :allowed-actions [create-a-st]
          :disallowed-actions (conj (navigate-all :katello.systems/page :katello.organizations/page
                                                  :katello.providers/custom-page :katello.sync-management/status-page :katello.changesets/page)
                                    (fn [] (organization/create (uniqueify "cantdothis")))
                                    create-an-env)])
   
   (fn [] [:permissions [{:org "Global Permissions"
                         :permissions [{:resource-type "Users"
                                        :verbs ["Read Users"]
                                        :name "userread"}]}]
          :allowed-actions [(navigate-fn :katello.users/page)]
          :disallowed-actions (conj (navigate-all :katello.systems/page :katello.organizations/page :katello.roles/page
                                                  :katello.changesets/page)
                                    (fn [] (organization/create (uniqueify "cantdothis")))
                                    create-an-env
                                    create-a-user)])

   (fn [] (let [user (uniqueify "user")]
           [:setup (fn [] (api/create-user user {:password "password" :email "me@me.com"}))
            :permissions [{:org "Global Permissions"
                           :permissions [{:resource-type "Users"
                                          :verbs ["Modify Users"]
                                          :name "usermod"}]}]
            :allowed-actions [(fn [] (user/edit user {:new-email "blah@me.com"}))]
            :disallowed-actions (conj (navigate-all :katello.systems/page :katello.organizations/page :katello.roles/page
                                                    :katello.changesets/page)
                                      (fn [] (let [username (uniqueify "deleteme")]
                                              (user/create username {:password "password" :email "mee@mee.com"})
                                              (user/delete username))))]))

   (fn [] (let [user (uniqueify "user")]
           [:permissions [{:org "Global Permissions"
                           :permissions [{:resource-type "Users"
                                          :verbs ["Delete Users"]
                                          :name "userdel"}]}]
            :setup (fn [] (api/create-user user {:password "password" :email "me@me.com"}))
            :allowed-actions [(fn [] (user/delete user))]
            :disallowed-actions (conj (navigate-all :katello.systems/page :katello.organizations/page :katello.roles/page
                                                    :katello.changesets/page)
                                      create-a-user)]))

   (fn [] (let [org (uniqueify "org")]
           [:permissions [{:org (@conf/config :admin-org)
                           :permissions [{:resource-type "Organizations"
                                          :verbs ["Read Organization"]
                                          :name "orgaccess"}]}]
            :setup (fn [] (api/create-organization org))
            :allowed-actions [(access-org (@conf/config :admin-org))]
            :disallowed-actions (conj (navigate-all :katello.systems/page :katello.sync-management/status-page
                                                    :katello.providers/custom-page :katello.system-templates/page
                                                    :katello.changesets/page )
                                      (fn [] (organization/switch org))
                                      (fn [] (nav/go-to :katello.organizations/named-page {:org-name org})))]))
   
   (fn [] (let [org (uniqueify "org")]
           [:permissions [{:org org
                           :permissions [{:resource-type :all 
                                          :name "orgadmin"}]}]
            :setup (fn [] (api/create-organization org))
            :allowed-actions (conj (navigate-all :katello.systems/page :katello.sync-management/status-page
                                                 :katello.providers/custom-page :katello.system-templates/page
                                                 :katello.changesets/page )
                                   (access-org org)
                                   (fn [] (environment/create (uniqueify "blah") {:org-name org})))
            :disallowed-actions [(access-org (@conf/config :admin-org))
                                 (fn [] (organization/switch (@conf/config :admin-org)))]]))
   
   (fn [] (let [gpg-key-name (uniqueify "test-key-text5")
                provider-name (uniqueify "provider")
                product-name (uniqueify "product")
                repo-name (uniqueify "repo")
                organization (@conf/config :admin-org)
                url (-> fake/custom-providers first :products first :repos second :url)]
            [:permissions [{:org organization
                            :permissions [{:name "blah1"
                                           :resource-type "Organizations"
                                           :verbs ["Administer GPG Keys"]}]}]
             :allowed-actions [(fn [] (gpg-key/create gpg-key-name {:contents (slurp (@conf/config :gpg-key))}))]
             :disallowed-actions (conj (navigate-all :katello.systems/page :katello.sync-management/status-page
                                                     :katello.system-templates/page :katello.changesets/page
                                                     :katello.providers/custom-page)
                                       (fn [] (providers/create {:name provider-name}))
                                       (fn [] (providers/add-product {:provider-name provider-name
                                                                     :name product-name}))                           
                                       (fn [] (repo/add-with-key {:provider-name provider-name
                                                                  :product-name product-name
                                                                  :name repo-name
                                                                  :url url
                                                                  :gpgkey gpg-key-name}))
                                       (fn [] (api/create-provider "myprov")))]))
   
    (fn [] (let [gpg-key-name (uniqueify "test-key-text3")
                 organization (@conf/config :admin-org)]
            [:permissions [{:org organization
                            :permissions [{:name "blah3"
                                           :resource-type "Organizations"
                                           :verbs ["Read Organization"]}]}]
             :allowed-actions [(navigate-fn :katello.gpg-keys/page)]
             :disallowed-actions (conj (navigate-all :katello.systems/page :katello.sync-management/status-page
                                                     :katello.providers/custom-page :katello.system-templates/page
                                                     :katello.changesets/page)
                                       (fn [] (gpg-key/create gpg-key-name {:contents (slurp (@conf/config :gpg-key))})))]))])

>>>>>>> master

;; Tests
(defn- create-role* [f name]
  (-> {:name name} kt/newRole f ui/create))

(def create-unique-role (partial create-role* uniqueify))
(def create-role (partial create-role* identity))

(defgroup permission-tests

  (deftest "Create a role"
    (create-unique-role "testrole"))

  (deftest "Create a role with i18n characters"
    :data-driven true

    create-unique-role
    [["صالح"] ["Гесер"] ["洪"]["標準語"]])

  (deftest "Role validation"
    :data-driven true

    (fn [rolename expected-err]
      (expecting-error (common/errtype expected-err)
                       (create-role rolename)))

    [[(random-string (int \a) (int \z) 129)  :katello.notifications/name-too-long]
     ["  foo" :katello.notifications/name-no-leading-trailing-whitespace]
     ["  foo   " :katello.notifications/name-no-leading-trailing-whitespace]
     ["foo " :katello.notifications/name-no-leading-trailing-whitespace]
     ["" :katello.notifications/name-cant-be-blank]
     (with-meta ["<a href='http://malicious.url/'>Click Here</a>" :katello.notifications/katello-error]
       {:blockers (open-bz-bugs "901657")}) ; TODO create more specific error after fix
     ])

  (deftest "Remove a role"
<<<<<<< HEAD
    (doto (uniqueify (kt/newRole {:name "deleteme-role"}))
      (ui/create)
      (ui/delete)))

  (deftest "Add a permission and user to a role"
    (with-unique [user (kt/newUser {:name "role-user" :password "abcd1234" :email "me@my.org"})
                  role (kt/newRole {:name "edit-role"})]
      (ui/create-all (list user role))
      (ui/update role assoc
                 :permissions [{:org global
                                :name "blah2"
                                :resource-type "Organizations"
                                :verbs ["Read Organization"]}]
                 :users [user]))

=======
    (let [role-name (uniqueify "deleteme-role")]
      (role/create role-name)
      (role/delete role-name)))
  
(deftest "Remove systems with appropriate permissions"
    :data-driven true
    :description "Allow user to remove system only when user has approriate permissions to remove system"
    
    (fn [sysverb]
      (with-unique [user-name "role-user"
                    role-name "myrole"
                    system-name "sys_perm"]
        (let [password "abcd1234"]
          (user/create user-name {:password password :email "me@my.org"})
          (role/create role-name)
          (role/edit role-name
                     {:add-permissions [{:org "Global Permissions"
                                         :permissions [{:name "blah2"
                                                        :resource-type "Organizations"
                                                        :verbs [sysverb]}]}]
                      :users [user-name]})
          (system/create system-name {:sockets "1"
                                      :system-arch "x86_64"})
          (login/logout)
          (login/login user-name password {:org "ACME_Corporation"})
          (try
            (system/delete system-name)
            (catch SeleniumException e)
            (finally
              (login))))))
    
    [["Read Systems"]
     ["Delete Systems"]])

  (deftest "Verify the Navigation of Roles, related to permissions"
    :data-driven true
    
    (fn [resource-type verbs tags]
      (let [role-name    (uniqueify "nav-roles")
            perm-name    (uniqueify "perm-namess")
            organization (@conf/config :admin-org)]
       
      (role/create role-name)  
      (assert/is (role/validate-permissions-navigation role-name perm-name organization resource-type verbs tags))))
    
    [["Environments" ["Administer Changesets in Environment"] ["Dev"]]])
     
  (deftest "Add a permission and user to a role"
    (with-unique [user-name "role-user"
                  role-name "edit-role"]
      (user/create user-name {:password "abcd1234" :email "me@my.org"})
      (role/create role-name)
      (role/edit role-name
                 {:add-permissions [{:org "Global Permissions"
                                     :permissions [{:name "blah2"
                                                    :resource-type "Organizations"
                                                    :verbs ["Read Organization"]}]}]
                  :users [user-name]}))

    (deftest "Verify user with no role has no access"
      :blockers (open-bz-bugs "915960")
      :data-driven true
      
      (fn [forbidden-url-list allowed-url-list]
           (let [username (uniqueify "user-perm")
                 pw "password"]
             (api/create-user username {:password pw :email (str username "@my.org")})
           (conf/with-creds username pw
             (login)
             (assert/is 
               (and
                 (every? nav/returns-403? forbidden-url-list)
                 (not-any? nav/returns-403? allowed-url-list)))))
           (login))
      
      [[(map #(str "/katello/" %)
           ["subscriptions"
		       "systems"
		       "systems/environments"
		       "system_groups"
		       "roles"
		       "sync_management/index"
		       "content_search"
		       "system_templates"
		       "organizations"
		       "providers"])
       (map #(str "/katello/" %)
           ["users"])]])
		          
      
  
>>>>>>> master
    (deftest "Verify user with specific permission has access only to what permission allows"
      :data-driven true
      :blockers (fn [_] (if (rest/is-headpin?)
                          ((open-bz-bugs "868179") _)
                          []))

      verify-access
      access-test-data) ))
