## Changelog

### 2.22

Release date: 2020-08-03

- Improvement: Add `durabilityHint` symbol to `DurabilityHintBranchProperty` for use in data binding ([PR 97](https://github.com/jenkinsci/workflow-multibranch-plugin/pull/97))
- Improvement: Add French localization ([PR 95](https://github.com/jenkinsci/workflow-multibranch-plugin/pull/95))
- Internal: Update minimum required Jenkins version to 2.176.4, update parent POM, and update dependencies ([PR 100](https://github.com/jenkinsci/workflow-multibranch-plugin/pull/100), [PR 92](https://github.com/jenkinsci/workflow-multibranch-plugin/pull/92))
- Internal: Migrate documentation from wiki to plugins.jenkins.io ([PR 93](https://github.com/jenkinsci/workflow-multibranch-plugin/pull/93))
- Internal: Miscellaneous code cleanup (use try-with-resources, remove redundant casts, use NIO, use type inference, etc.) ([PR 89](https://github.com/jenkinsci/workflow-multibranch-plugin/pull/89))
- Internal: Update `workflow-scm-step` dependency past 2.6 to fix PCT issues ([PR 86](https://github.com/jenkinsci/workflow-multibranch-plugin/pull/86))
- Internal: Replace usage of deprecated APIs with nondeprecated equivalents ([PR 85](https://github.com/jenkinsci/workflow-multibranch-plugin/pull/85))
- Internal: Use the Groovy sandbox consistently in tests ([PR 84](https://github.com/jenkinsci/workflow-multibranch-plugin/pull/84))

### 2.21

Release date: 2019-03-07

-   [JENKINS-43194](https://issues.jenkins-ci.org/browse/JENKINS-43194) - Make SCMBinder try to abort builds in which Jenkinsfile has       been modified by an untrusted contributor ([PR #69](https://github.com/jenkinsci/workflow-multibranch-plugin/pull/69))
-   Migrate Chinese localization to another plugin ([PR #80](https://github.com/jenkinsci/workflow-multibranch-plugin/pull/80))
-   [JENKINS-55116](https://issues.jenkins-ci.org/browse/JENKINS-55116) - Workaround Hack patch fix to allow
    at least a rescan to work when it happens ([PR #83](https://github.com/jenkinsci/workflow-multibranch-plugin/pull/83))
-   [ JENKINS-2111](https://issues.jenkins-ci.org/browse/JENKINS-2111) - Integration test of new workspace
    naming strategy ([PR #82](https://github.com/jenkinsci/workflow-multibranch-plugin/pull/82))
-   Add gitDirectory symbol to GitDirectorySCMNavigator ([PR #79](https://github.com/jenkinsci/workflow-multibranch-plugin/pull/79))

### 2.20 

Release date: 2019-07-03

-   Test fixes.

### 2.19 

Release date: 2018-05-17

-   Bugfix: builds failing upon running a JobPropertyStep with error
    "did not yet start" if previous build was hard-killed or failed
    before Pipeline began running
    ([JENKINS-51290](https://issues.jenkins-ci.org/browse/JENKINS-51290))

### 2.18 

Release date: 2018-04-20

-   Correct small bug from the improper use of the
    FlowExecutionOwner.getOrNull() call for getting the previous
    Properties for a step (visible as a result of
    [JENKINS-50784](https://issues.jenkins-ci.org/browse/JENKINS-50784))

### 2.17 

Release date: 2018-01-22

-   Provide a BranchProperty to support setting configuring durability
    per-branch (supports
    [JENKINS-47300](https://issues.jenkins-ci.org/browse/JENKINS-47300))
-   Massive updates to dependencies and tests
-   Bugfix: [JENKINS-42817](https://issues.jenkins-ci.org/browse/JENKINS-42817)
    readTrusted Pipeline from SCM only works in \*/master

### 2.16

Release date: 2017-06-19

-   Bugfix: Stop removing JobProperties defined outside a step
    ([JENKINS-44848](https://issues.jenkins-ci.org/browse/JENKINS-44848))

### 2.15

Release date: 2017-06-01

-   [JENKINS-34561](https://issues.jenkins-ci.org/browse/JENKINS-34561) Option
    to select a script name/path other than `Jenkinsfile`.

### 2.14

Release date: 2017-03-10

-   [JENKINS-40558](https://issues.jenkins-ci.org/browse/JENKINS-40558) Replace
    in-line help references of "pipeline script" with "pipeline" to
    clarify that [Pipeline Model Definition
    Plugin](https://plugins.jenkins.io/pipeline-model-definition) works
    with multibranch

### 2.13

Release date: 2017-03-03

-   [JENKINS-33273](https://issues.jenkins-ci.org/browse/JENKINS-33273)
    Load `Jenkinsfile` (or any file requested in `loadTrusted`) directly
    from the SCM rather than doing a checkout. Requires a compatible SCM
    (currently Git or GitHub).
-   [JENKINS-40521](https://issues.jenkins-ci.org/browse/JENKINS-40521)
    Orphaned branch projects are now effectively disabled.
-   [JENKINS-41146](https://issues.jenkins-ci.org/browse/JENKINS-41146)
    Help text improvement.
-   API change: `AbstractWorkflowMultiBranchProjectFactory` implementers
    may now throw `IOException`.

One aspect of
[JENKINS-33273](https://issues.jenkins-ci.org/browse/JENKINS-33273) is
that you will not get an SCM changelog from a lightweight checkout of
`Jenkinsfile` itself---only if your script actually runs `checkout scm`.

This behavior change can be suppressed in case of emergency using
`-Dorg.jenkinsci.plugins.workflow.multibranch.SCMBinder.USE_HEAVYWEIGHT_CHECKOUT=true`.

### 2.12

Release date: 2017-02-02

-   [JENKINS-40906](https://issues.jenkins-ci.org/browse/JENKINS-40906) Add
    missing help text and configuration UI

### 2.10

Release date: 2017-01-16

    **Please read**[this Blog
    Post](https://jenkins.io/blog/2017/01/17/scm-api-2/) before
    upgrading
-   [JENKINS-40906](https://issues.jenkins-ci.org/browse/JENKINS-40906) Resolve
    an SCM from an SCMSource and a list of candidate target branch names
-   [JENKINS-39355](https://issues.jenkins-ci.org/browse/JENKINS-39355)
    Use SCM API 2.0.x APIs
-   [JENKINS-32179](https://issues.jenkins-ci.org/browse/JENKINS-32179) Branch
    indexing always attempts to create a new project for conflicting
    branch names from multiple sources
-   Added subversion based integration tests
-   If SCMSource.fetch returns null abort the build.
-   [JENKINS-35698](https://issues.jenkins-ci.org/browse/JENKINS-35698) Initial
    run of parameterized pipeline build should return properties default
    value
-   [JENKINS-38987](https://issues.jenkins-ci.org/browse/JENKINS-38987) SCMHead/SCMSource/SCMNavigator
    need getPronoun() to assist contextual naming
-   [JENKINS-38960](https://issues.jenkins-ci.org/browse/JENKINS-38960) Deprecate
    TopLevelItemDescriptor.getIconFilePathPattern() and
    TopLevelItemDescriptor.getIconFile(String)

### 2.10-beta-1

Release date: 2016-12-16

-   Updated to use new multibranch-related APIs.
-   More robust handling of invalid revisions.

### 2.9.2 

Release date: 2016-12-09

-   2.9.1 was corrupt.

### 2.9.1

Release date: 2016-11-09

-   In cases where the SCM source does not recognize a branch and fails
    to determine its tip revision, fail with a clearer message.

Do not use. Use 2.9.2 instead.

### 2.9

Release date: 2016-08-23

-   [JENKINS-37005](https://issues.jenkins-ci.org/browse/JENKINS-37005)
    Warn about use of the `properties` step from a non-multibranch
    project.
-   [JENKINS-37538](https://issues.jenkins-ci.org/browse/JENKINS-37538)
    Ensuring that `readTrusted` output is displayed incrementally, and
    can be interrupted.

### 2.8 (Jun 15, 2016)

Release date: 2016-06-15

-   [JENKINS-31386](https://issues.jenkins-ci.org/browse/JENKINS-31386) +
    [JENKINS-34596](https://issues.jenkins-ci.org/browse/JENKINS-34596):
    allow use of `readTrusted` inside a single-branch *Pipeline Script
    from SCM*.

### 2.7

Release date: 2016-06-09

-   [JENKINS-34246](https://issues.jenkins-ci.org/browse/JENKINS-34246)
    Integration of upstream fix to orphan or modify multibranch projects
    inside an organization folder as project recognizers dictate.

### 2.6

Release date: 2016-06-02

-   [JENKINS-34596](https://issues.jenkins-ci.org/browse/JENKINS-34596)
    Added `readTrusted` step to provide greater flexibility when
    building untrusted pull requests.
-   Internal refactoring to expose multibranch functionality to other
    plugins.

### 2.5

Release date: 2016-07-02

-   [JENKINS-32396](https://issues.jenkins-ci.org/browse/JENKINS-32396)
    Pick up dependency with new feature to suppress automatic triggers
    of certain branch projects.
-   [JENKINS-30206](https://issues.jenkins-ci.org/browse/JENKINS-30206)
    Failure to update branch projects with modified configuration (only
    affecting newly introduced trigger suppression property).

### 2.4

Release date: 2016-05-23

-   [JENKINS-31831](https://issues.jenkins-ci.org/browse/JENKINS-31831)
    Make new *Pipeline Syntax* link appear on multibranch Pipeline
    projects, and organization folders configured with the Pipeline
    factory.

### 2.3

Release date: 2016-04-14

-   [JENKINS-34235](https://issues.jenkins-ci.org/browse/JENKINS-34235)
    Pipeline Multibranch project icon not found in the New Item page.

### 2.2

Release date: 2016-04-14

-   **Wrong release**.

### 2.1

Release date: 2016-04-13

-   [JENKINS-31162](https://issues.jenkins-ci.org/browse/JENKINS-31162)
    Support for Item categorization.

### 2.0

Release date: 2016-04-05

-   First release under per-plugin versioning scheme. See [1.x
    changelog](https://github.com/jenkinsci/workflow-plugin/blob/82e7defa37c05c5f004f1ba01c93df61ea7868a5/CHANGES.md)
    for earlier releases.
