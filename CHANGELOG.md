# Version History

### Version 2.5.4 (Jul 25, 2019)

-   [
    JENKINS-54864](https://issues.jenkins-ci.org/browse/JENKINS-54864) -
    Getting issue details... STATUS  Undeprecate the "Automatic branch
    project triggering" property for organization folders and disable
    the automated migration that replaced it with a "Named Branch" build
    strategy from Basic Branch Build Strategies Plugin starting in
    version 2.1.0 of this plugin because the migration caused an
    undesirable change in behavior in some cases.

### Version 2.5.3 

-   TODO

### Version 2.5.2 (May 24, 2019)

-   Typo fix from v2.5.1 and internal refactoring ([PR
    \#153](https://github.com/jenkinsci/branch-api-plugin/pull/153), [PR
    \#154](https://github.com/jenkinsci/branch-api-plugin/pull/154))

### Version 2.5.1 (May 22, 2019)

-   [
    JENKINS-57588](https://issues.jenkins-ci.org/browse/JENKINS-57588) -
    Getting issue details... STATUS  Prevent job storm on upgrade from
    2.4.x and earlier ([PR
    \#152](https://github.com/jenkinsci/branch-api-plugin/pull/152))

### Version 2.5.0 (May 21, 2019)

-   [PR \#145](https://github.com/jenkinsci/branch-api-plugin/pull/145)
    - Support for programmatically generating OrganizationFolders
-   [
    JENKINS-54992](https://issues.jenkins-ci.org/browse/JENKINS-54992) -
    Getting issue details... STATUS  Replaced misleading job pronoun on
    Delete action with base Job pronoun ([PR
    \#150](https://github.com/jenkinsci/branch-api-plugin/pull/150))
-   Java 11 readiness: build both on JDK8 and JDK11 ([PR
    \#148](https://github.com/jenkinsci/branch-api-plugin/pull/148))
-   [
    JENKINS-38552](https://issues.jenkins-ci.org/browse/JENKINS-38552) -
    Getting issue details... STATUS  Use a lastSeenRevision + lastBuilt
    ([PR
    \#149](https://github.com/jenkinsci/branch-api-plugin/pull/149))
-   [
    JENKINS-55597](https://issues.jenkins-ci.org/browse/JENKINS-55597) -
    Getting issue details... STATUS  Handle IOException in onOnline when
    failing to delete workspace, to prevent a node disconnection ([PR
    \#142](https://github.com/jenkinsci/branch-api-plugin/pull/142))
-   [
    JENKINS-49729](https://issues.jenkins-ci.org/browse/JENKINS-49729) -
    Getting issue details... STATUS  Indicate dead branch on job and run
    pages ([PR
    \#151](https://github.com/jenkinsci/branch-api-plugin/pull/151))

### Version 2.4.0 (Apr 8, 2019)

-   [
    JENKINS-56903](https://issues.jenkins-ci.org/browse/JENKINS-56903) -
    Getting issue details... STATUS  Add a health reporting metric for
    multibranch projects that only reports the health of the "primary"
    branch (as reported by the SCM)
-   [
    JENKINS-56917](https://issues.jenkins-ci.org/browse/JENKINS-56917) -
    Getting issue details... STATUS  Adds an extension point for
    organization folders that enabled the customization of the children
    of the organization folder on every organization folder scan. There
    are three implementations provided:  
    -   Child Health metrics which allows controlling the health metrics
        that the child multibranch projects will use. Previous behaviour
        was to always configure just the default health metrics relevant
        to a multi-branch project.
    -   Child Scan Triggers which enabled the customization of the scan
        triggers for the child multibranch projects. Previous behaviour
        was to initially configure this to once per day and never update
        it. The new behaviour will now enforce the triggers defined
        in Child Scan Triggers for the organization folder.   
        ![(warning)](docs/images/warning.svg)**NOTE **if
        you have been using some custom hack to change the multibranch
        scan triggers after initial creation, that hack is no longer
        needed... and in fact it will now cease to work
    -   Child Orphaned Item Strategy which allows the child multibranch
        projects to have a different orphaned item strategy from the
        parent organization folder. By default this property will use
        the *Inherited* strategy which retains the existing behaviour
        but you can configure a different strategy if you want branches
        to be retained on a different schedule from repositories.
-   Jenkins core version bump to 2.138 LTS line

### Version 2.3.0 (Apr 4, 2019)

-   Set the revision even if the build does not happen. Enabling  [
    JENKINS-38552](https://issues.jenkins-ci.org/browse/JENKINS-38552) -
    Getting issue details... STATUS
-   Migrated Chinese localization into localization-zh-cn
-   Updated some test dependencies

### Version 2.2.0 (Mar 21, 2019)

-   [
    JENKINS-56658](https://issues.jenkins-ci.org/browse/JENKINS-56658) -
    Getting issue details... STATUS  Changed API for BranchBuildStrategy
    to provide strategies with access to the task listener.  
    Impact assessment:
    -   Change is binary compatible. At run-time plugins implementing
        the older API will be transparently detected and the legacy API
        methods invoked as appropriate. 
    -   Change is not source compatible. Plugins implementing
        BranchBuildStrategy will need to update the overridden method
        when they update their compile time dependency on branch-api to
        2.2.0
-   [
    JENKINS-54968](https://issues.jenkins-ci.org/browse/JENKINS-54968) -
    Getting issue details... STATUS  “path sanitization ineffective when
    using legacy Workspace Root Directory” ending in slash 

### Version 2.1.2 (Dec 6, 2018)

-   [
    JENKINS-54654](https://issues.jenkins-ci.org/browse/JENKINS-54654) -
    Getting issue details... STATUS

### Version 2.1.1 (Nov 19, 2018)

-   [
    JENKINS-54640](https://issues.jenkins-ci.org/browse/JENKINS-54640) -
    Getting issue details... STATUS Index collision check was not
    working

### Version 2.1.0 (Nov 16, 2018)

-   [
    JENKINS-47859](https://issues.jenkins-ci.org/browse/JENKINS-47859) -
    Getting issue details... STATUS  Migrate "Automatic branch project
    triggering » Branch names to build automatically" hack to the branch
    build strategy implementation

### Version 2.0.21 (Nov 9, 2018)

-   [ JENKINS-2111](https://issues.jenkins-ci.org/browse/JENKINS-2111) -
    Getting issue details... STATUS   [
    JENKINS-34564](https://issues.jenkins-ci.org/browse/JENKINS-34564) -
    Getting issue details... STATUS [
    JENKINS-30148](https://issues.jenkins-ci.org/browse/JENKINS-30148) -
    Getting issue details... STATUS [
    JENKINS-38706](https://issues.jenkins-ci.org/browse/JENKINS-38706) -
    Getting issue details... STATUS [
    JENKINS-22240](https://issues.jenkins-ci.org/browse/JENKINS-22240) -
    Getting issue details... STATUS Managed workspace indices
-   [
    JENKINS-50561](https://issues.jenkins-ci.org/browse/JENKINS-50561) -
    Getting issue details... STATUS  Added rateLimitBuilds symbol
-   Code cleanup

### Version 2.0.20.1 (Nov 15, 2018)

-   Updated pom to fix the PCT for the Git Plugin

### Version 2.0.20 (Apr 20, 2018)

-   [
    JENKINS-50777](https://issues.jenkins-ci.org/browse/JENKINS-50777) -
    Getting issue details... STATUS

### Version 2.0.19 (Apr 5, 2018)

-   Remove usage restriction from OrganizationFolder

### Version 2.0.18 (Jan 10, 2018)

-   [
    JENKINS-48890](https://issues.jenkins-ci.org/browse/JENKINS-48890) -
    Getting issue details... STATUS

### Version 2.0.17 (Jan 2, 2018)

-   [JENKINS-48535](https://issues.jenkins-ci.org/browse/JENKINS-48535)
    Provide an API that enabled extension plugin to provide a branch
    build strategy that could do things like not-build merge PRs when
    only the target revision has changed
-   [JENKINS-48536](https://issues.jenkins-ci.org/browse/JENKINS-48536)
    Organization folder does not call afterSave on child multibranch
    projects

### Version 2.0.16 (Dec 5, 2017)

-   [JENKINS-44335](https://issues.jenkins-ci.org/browse/JENKINS-44335)
    Allow user-boosting option in rate limit throttle
-   [JENKINS-48214](https://issues.jenkins-ci.org/browse/JENKINS-48214)
    When a multibranch project in an organization folder has been
    disabled, the organization folder is responsible for handling events
-   [JENKINS-48090](https://issues.jenkins-ci.org/browse/JENKINS-48090)
    When a SCMSource provides branch actions that include CauseAction,
    merge the CauseActions
-   Add Chinese translations
    ([PR\#114](https://github.com/jenkinsci/branch-api-plugin/pull/114))

### Version 2.0.15 (Oct 26, 2017)

-   [JENKINS-47678](https://issues.jenkins-ci.org/browse/JENKINS-47678)
    If a BranchBuildStrategy is provided by an extension plugin,
    attempts to save a multibranch project with a BranchBuildStrategy
    configured will fail with a class cast exception.

### Version 2.0.14 (Oct 9, 2017)

-   [JENKINS-47311](https://issues.jenkins-ci.org/browse/JENKINS-47311)
    Ok, sometimes you have a bad day making simple fixes.
    [Fixed](https://github.com/jenkinsci/branch-api-plugin/commit/720206f89cc7d0caafe0b67dba23d8abc1b88275) now
    ![(tongue)](docs/images/tongue.svg).

### Version 2.0.13 (Oct 9, 2017)

-   [JENKINS-47340](https://issues.jenkins-ci.org/browse/JENKINS-47340)
    Fix NPE when saving organization folders

### Version 2.0.12 (Oct 6, 2017)

-   [JENKINS-47311](https://issues.jenkins-ci.org/browse/JENKINS-47311)
    Fix the broken form submission and add the missing form support for
    org folders

-   [JENKINS-47308](https://issues.jenkins-ci.org/browse/JENKINS-47308)
    Add the ability for branch build strategies to consider the revision

-   [JENKINS-46957](https://issues.jenkins-ci.org/browse/JENKINS-46957)
    Use new parent POM to fix PCT and update dependencies accordingly
-   [JENKINS-45814](https://issues.jenkins-ci.org/browse/JENKINS-45814)
    Fix javadoc

-   Update to SCM API 2.2.3

### Version 2.0.11 (Jul 17, 2017)

-   [JENKINS-38837](https://issues.jenkins-ci.org/browse/JENKINS-38837) Mutibranch
    project plugin does not respect "Workspace Root Directory" global
    configuration
-   [JENKINS-43433](https://issues.jenkins-ci.org/browse/JENKINS-43433) Allow
    SCMSource implementations to expose merge and origin of change
    request heads
-   [JENKINS-43507](https://issues.jenkins-ci.org/browse/JENKINS-43507) Allow
    SCMSource and SCMNavigator subtypes to share common traits
-   [JENKINS-44676](https://issues.jenkins-ci.org/browse/JENKINS-44676) Support
    for TAG\_NAME env variable
-   [JENKINS-45322](https://issues.jenkins-ci.org/browse/JENKINS-45322) Orphaned
    MultiBranchProject not properly disabled

### Version 2.0.10 (Jun 9, 2017)

-   [JENKINS-44784](https://issues.jenkins-ci.org/browse/JENKINS-44784)
    Perform workspace cleanup for deleted branch projects asynchronously
    and apply a timeout.

### Version 2.0.9 (May 2, 2017)

-   [JENKINS-41736](https://issues.jenkins-ci.org/browse/JENKINS-41736)
    Leverage the new event description API from SCM API to expose event
    descriptions
-   [JENKINS-34691](https://issues.jenkins-ci.org/browse/JENKINS-34691)
    On Jenkins 2.51+ veto attempts to copy branch projects outside of
    their multibranch container (as they will not function correctly
    outside of their container)

### Version 2.0.8 (Mar 8, 2017)

-   [JENKINS-37364](https://issues.jenkins-ci.org/browse/JENKINS-37364) Tabs
    should indicate the number of items they have
-   [JENKINS-34522](https://issues.jenkins-ci.org/browse/JENKINS-34522) On
    versions of Jenkins core with [this
    change](https://github.com/jenkinsci/jenkins/pull/2772) merged,
    provide the correct action text for Scan now
-   [JENKINS-42511](https://issues.jenkins-ci.org/browse/JENKINS-42511) When
    events are concurrent with scanning, ensure that events and scanning
    do not create shadow items resulting in duplicate builds with the
    same build number 

### Version 2.0.7 (Feb 22, 2017)

-   [JENKINS-34564](https://issues.jenkins-ci.org/browse/JENKINS-34564)
    Allow workspace paths to be less than 54 characters
-   [JENKINS-42009](https://issues.jenkins-ci.org/browse/JENKINS-42009)
    Update some test harness related code
-   [JENKINS-42151](https://issues.jenkins-ci.org/browse/JENKINS-42151)
    Pick up API changes and return event processing to multi-threaded
-   [JENKINS-42234](https://issues.jenkins-ci.org/browse/JENKINS-42234)
    A missing call to SCMHeadEvent.isMatch() could cause some events to
    trigger incorrect branches

### Version 2.0.6 (Feb 14, 2017)

-   [JENKINS-42000](https://issues.jenkins-ci.org/browse/JENKINS-42000) If
    there is a problem when scanning an Organization Folder, do not
    storm off in a huff and delete all the jobs in the organization
    folder!

### Version 2.0.5 (Feb 14, 2017)

-   [JENKINS-41948](https://issues.jenkins-ci.org/browse/JENKINS-41948) (workaround)
    Restore some binary compatibility by adding a bridge method that got
    removed with the upgrade to CloudBees Folders 5.17
-   [JENKINS-41980](https://issues.jenkins-ci.org/browse/JENKINS-41980)
    SCM events should be ignored when suppressing SCM triggering. 

### Version 2.0.4 (Feb 10, 2017)

-   [JENKINS-41927](https://issues.jenkins-ci.org/browse/JENKINS-41927)
    Orphaned branches should have name in strikethrough
-   [JENKINS-41883](https://issues.jenkins-ci.org/browse/JENKINS-41883)
    Global event logs were being overwritten on every event making them
    less useful than they should be

### Version 2.0.3 (Feb 8, 2017)

-   [JENKINS-41795](https://issues.jenkins-ci.org/browse/JENKINS-41795) Report
    the origin of SCM Events when available

### Version 2.0.2 (Feb 2, 2017)

-   [JENKINS-41517](https://issues.jenkins-ci.org/browse/JENKINS-41517) Branch
    API's event logging could be more consistent in reporting the event
    class
-   [JENKINS-41171](https://issues.jenkins-ci.org/browse/JENKINS-41171) Superfluous
    New Item added for "Organization Folder"
-   [JENKINS-41124](https://issues.jenkins-ci.org/browse/JENKINS-41124) Can't
    get a human readable job name anymore
-   [JENKINS-41255](https://issues.jenkins-ci.org/browse/JENKINS-41255) Upgrading
    from a navigator that did not assign consistent source ids to a
    version that does assign consistent source ids causes a build storm
    on first scan
-   [JENKINS-41121](https://issues.jenkins-ci.org/browse/JENKINS-41121) GitHub
    Branch Source upgrade can cause a lot of rebuilds
-   [JENKINS-41209](https://issues.jenkins-ci.org/browse/JENKINS-41209) NPE
    during loading of branch jobs when migrating from 1.x to 2.x

### Version 2.0.1 (Jan 17, 2017)

-   [JENKINS-41125](https://issues.jenkins-ci.org/browse/JENKINS-41125) Branch
    API 2.0.0 event processing doesn't consistently mangle names

### Version 2.0.0 (Jan 16, 2017)

-   ![(warning)](docs/images/warning.svg)
     Please read [this Blog
    Post](https://jenkins.io/blog/2017/01/17/scm-api-2/) before
    upgrading
-   [JENKINS-40865](https://issues.jenkins-ci.org/browse/JENKINS-40865) Org
    folders do not encode child project names
-   [JENKINS-40876](https://issues.jenkins-ci.org/browse/JENKINS-40876) ObjectMetadataAction
    objectUrl never gets populated for PRs or Branches
-   Log exceptions during scan/indexing with tracking details
-   Where the SCM Source reports tags (no known implementations yet),
    tags should not be built by default
-   Suppress scans when configuration unchanged but trigger if there has
    not been a scan with current configuration
-   [JENKINS-40832](https://issues.jenkins-ci.org/browse/JENKINS-40832) Primary
    branches should have their name in bold
-   [JENKINS-40829](https://issues.jenkins-ci.org/browse/JENKINS-40829) Provide
    an API to retrieve a SCMSource from a given Item
-   [JENKINS-40828](https://issues.jenkins-ci.org/browse/JENKINS-40828) Provide
    a way for tests using MockSCMController to inject failures
-   [JENKINS-40827](https://issues.jenkins-ci.org/browse/JENKINS-40827) Clarify
    the content of ObjectMetadataAction's getDescription() and
    getDisplayName()
-   [JENKINS-39355](https://issues.jenkins-ci.org/browse/JENKINS-39355) Pick
    up SCM API improvements
-   [JENKINS-39816](https://issues.jenkins-ci.org/browse/JENKINS-39816) Fix
    PCT against \>= 2.16
-   [JENKINS-39520](https://issues.jenkins-ci.org/browse/JENKINS-39520) CustomOrganizationFolderDescriptor
    breaks when multiple branch sources are added
-   [JENKINS-39026](https://issues.jenkins-ci.org/browse/JENKINS-39026) Add
    a ViewJobFilter specialized for filtering by Branch
-   [JENKINS-38987](https://issues.jenkins-ci.org/browse/JENKINS-38987) Use
    contextual naming for SCMHead/SCMSource/SCMNavigator instances

### Version 2.0.0-beta-1 (Dev 16, 2016)

-   Available in the experimental update center only
-   Pick up API changes from SCM API 2.0 (requires SCM API 2.0.1-beta-1
    and if you have either of the github-branch-source or
    bitbucket-branch-source plugins you must upgrade them to at least
    2.0.0-beta-1)

### Version 1.11.1 (Nov 04, 2016)

-   [JENKINS-39520](https://issues.jenkins-ci.org/browse/JENKINS-39520)
    Error when dynamically installing multiple branch source plugins.

### Version 1.11 (Sep 23, 2016)

-   [JENKINS-34564](https://issues.jenkins-ci.org/browse/JENKINS-34564)
    Branch projects now get custom workspace paths inside the node’s
    `workspace` directory, capped by default at 80 characters and using
    only ASCII letters, numbers, and simple punctuation (in particular,
    no `%`).
-   [JENKINS-37219](https://issues.jenkins-ci.org/browse/JENKINS-37219)
    Added a job property for overriding the implicit branch indexing
    trigger flag, allowing a multibranch `Jenkinsfile` to customize its
    own triggering behavior after the initial build.

Some projects running external processes that cannot handle even
moderately long pathnames will not work with the new default workspace
locations. The system property
`jenkins.branch.WorkspaceLocatorImpl.PATH_MAX` may be set to `0` to
restore the previous behavior (which will then break some processes
which cannot handle funny characters, or projects using long branch
names etc.). The default value is 80; values as low as 54 (but no lower)
are possible. When feasible, fix the external process to be more robust,
or on Windows use

``` syntaxhighlighter-pre
\\?\
```

as a prefix before the remote filesystem root.

Another workaround in Pipeline scripts is to use the `ws` step with an
absolute pathname. You can then choose any path, and concurrent builds
will still get distinct workspaces automatically; but you are on the
hook for finding a *valid* path on the node, unrelated projects might
overwrite each other’s workspaces between builds (reducing beneficial
caches of SCM checkouts and the like), and the custom workspaces will
not be automatically deleted if the branch project is deleted. The first
problem could be avoided by using a pathname like `../custom` rather
than an absolute path.

Note that the `sshagent` Pipeline step ([SSH Agent
Plugin](https://wiki.jenkins.io/display/JENKINS/SSH+Agent+Plugin)) when
used inside an `Image.inside` block ([Docker Pipeline
Plugin](https://wiki.jenkins.io/display/JENKINS/Docker+Pipeline+Plugin))
will not currently work when the workspace path exceeds 108 characters,
due to a poor choice of constant in most Linux kernels:
[JENKINS-36997](https://issues.jenkins-ci.org/browse/JENKINS-36997).

A full fix should probably come in
[JENKINS-2111](https://issues.jenkins-ci.org/browse/JENKINS-2111) for
all project types.

### Version 1.10.2 (Sep 03, 2016; 1.10.1 burned)

-   [JENKINS-34239](https://issues.jenkins-ci.org/browse/JENKINS-34239)
    Fix of
    [JENKINS-33106](https://issues.jenkins-ci.org/browse/JENKINS-33106)
    in 1.4 did not work in all cases.

### Version 1.10 (Jun 09, 2016)

-   [JENKINS-34246](https://issues.jenkins-ci.org/browse/JENKINS-34246)
    Improve organization folder API to allow project recognizers to
    indicate removed repositories or edited configuration.

### Version 1.9 (Jun 01, 2016)

-   [JENKINS-32178](https://issues.jenkins-ci.org/browse/JENKINS-32178)
    Broken links in custom views of multibranch projects.

### Version 1.9-beta-1 (May 23, 2016)

-   [JENKINS-32396](https://issues.jenkins-ci.org/browse/JENKINS-32396)
    Option to suppress automatic SCM trigger.

### Version 1.8 (May 13, 2016)

-   [JENKINS-33819](https://issues.jenkins-ci.org/browse/JENKINS-33819)
    `OrphanedItemStrategy` is now propagated to multibranch projects.
-   Added extra log
    [messages](https://github.com/jenkinsci/branch-api-plugin/pull/40)
    from branch indexing.
-   A
    [regression](https://github.com/jenkinsci/branch-api-plugin/pull/39)
    was introduced in 1.7 while
    [JENKINS-34259](https://issues.jenkins-ci.org/browse/JENKINS-34259)
    was fixed.

### Version 1.7 (Apr 29, 2016)

-   [JENKINS-34259](https://issues.jenkins-ci.org/browse/JENKINS-34259)
    Some links (in left menu) in Pipeline Multibranch projects and
    GitHub Organization projects are broken when there are no branch
    sources defined or the GitHub Organization is empty.
-   Documented build environment variables.

### Version 1.6 (Apr 11, 2016)

-   [JENKINS-33808](https://issues.jenkins-ci.org/browse/JENKINS-33808)
    Support for Item categorization. More information about this new
    feature in core here
    [JENKINS-31162](https://issues.jenkins-ci.org/browse/JENKINS-31162)

### Version 1.5 (Mar 21, 2016)

-   [JENKINS-32670](https://issues.jenkins-ci.org/browse/JENKINS-32670)
    Suppress whole branch property UI for project types which do not
    have any supported branch properties, such as multibranch Pipeline.

### Version 1.4 (Mar 14, 2016)

-   [JENKINS-33106](https://issues.jenkins-ci.org/browse/JENKINS-33106)
    Organization folder types not displayed under *New Item* without a
    restart.
-   [JENKINS-33309](https://issues.jenkins-ci.org/browse/JENKINS-33309)
    Using API for defining variables associated with pull requests.
-   [JENKINS-32782](https://issues.jenkins-ci.org/browse/JENKINS-32782)
    Welcome view failed to display *Delete Folder* link.

### Version 1.3 (Feb 18, 2016)

-   Prevent NPE while unserialization of BranchSources with a null
    SCMSource
-   [JENKINS-32493](https://issues.jenkins-ci.org/browse/JENKINS-32493)
    Adapt to Parent POM 2.3

### Version 1.1 (Jan 28, 2016)

-   [JENKINS-31949](https://issues.jenkins-ci.org/browse/JENKINS-31949)
    Bogus *New Item* option inside folders.
-   [JENKINS-31516](https://issues.jenkins-ci.org/browse/JENKINS-31516)
    Children not reindexed on organization folder reindex.
-   Useless `MultiBranchProjectDescriptor.getSCMDescriptors` API
    deleted.
-   [JENKINS-31381](https://issues.jenkins-ci.org/browse/JENKINS-31381)
    Show more helpful welcome text for empty multibranch projects and
    organization folders.

### Version 1.0 (Nov 12, 2015)

-   Fix to `RateLimitBranchProperty` for the benefit of Workflow
    multibranch `properties` step.
-   Ensure that `SCMSource.setOwner` is called consistently.
-   [JENKINS-30252](https://issues.jenkins-ci.org/browse/JENKINS-30252)
    New environment variable `BRANCH_NAME`.
-   [JENKINS-30595](https://issues.jenkins-ci.org/browse/JENKINS-30595)
    Implemented new API.
-   Suppress non-read view permissions on multi-branch projects within
    an organization folder.
-   Add `PeriodicFolderTrigger` by default.
-   [JENKINS-30744](https://issues.jenkins-ci.org/browse/JENKINS-30744)
    Fixed handling of branches with slashes in the name.
-   [JENKINS-31432](https://issues.jenkins-ci.org/browse/JENKINS-31432)
    NPE under some conditions.

### Version 0.2-beta-5

**Warning**: settings compatibility for this release has not yet been
tested. If you have an existing project using the Literate plugin in
particular, the dead branch retention strategy might be reset to “delete
immediately” after the upgrade.

-   Introduced an “organization folder” top-level item type. Hidden
    unless there are some SCM providers ([GitHub Branch Source
    Plugin](https://wiki.jenkins.io/display/JENKINS/GitHub+Branch+Source+Plugin)),
    and project factories ([Pipeline
    Plugin](https://wiki.jenkins.io/display/JENKINS/Pipeline+Plugin)).
-   Major refactoring to use `ComputedFolder` API in [CloudBees Folders
    Plugin](https://wiki.jenkins.io/display/JENKINS/CloudBees+Folders+Plugin).
-   Always run branch indexing on the master node.
-   Compatibility with 1.576+ icon captions.

### Version 0.2-beta-4

-   API changes useful for Workflow.

### Version 0.1

-   Initial release.