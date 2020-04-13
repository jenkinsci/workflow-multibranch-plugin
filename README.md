# Pipeline: Multibranch

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/workflow-multibranch)](https://plugins.jenkins.io/workflow-multibranch)
[![Changelog](https://img.shields.io/github/v/tag/jenkinsci/workflow-multibranch-plugin?label=changelog)](https://github.com/jenkinsci/workflow-multibranch-plugin/blob/master/CHANGELOG.md)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/workflow-multibranch?color=blue)](https://plugins.jenkins.io/workflow-multibranch)

## Introduction

Enhances Pipeline plugin to handle branches better by automatically
grouping builds from different branches.

Automatically creates a new Jenkins job whenever a new branch is pushed
to a source code repository.  
Other plugins can define various branch types, e.g. a Git branch, a
Subversion branch, a GitHub Pull Request etc.

See this blog post for more
info:<https://jenkins.io/blog/2015/12/03/pipeline-as-code-with-multibranch-workflows-in-jenkins/>

## Contributing

If you want to contribute to this plugin, you probably will need a Jenkins plugin development
environment. This basically means a version of Java 8
and [Apache Maven]. See the [Jenkins Plugin Tutorial] for details.

Running

    $ mvn hpi:run

allows you to spin up a test Jenkins instance on [localhost] to test your
local changes before committing.

[Apache Maven]: https://maven.apache.org/
[Jenkins Plugin Tutorial]: https://jenkins.io/doc/developer/tutorial/prepare/
[localhost]: http://localhost:8080/jenkins/

## Notes

To determine the branch being built - use the environment variable
`BRANCH_NAME` - e.g. `${env.BRANCH_NAME}`

## Version History

See [the changelog](CHANGELOG.md).
