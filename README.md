# Pipeline: Multibranch

## Introduction

Enhances Pipeline plugin to handle branches better by automatically
grouping builds from different branches.

Automatically creates a new Jenkins job whenever a new branch is pushed
to a source code repository.  
Other plugins can define various branch types, e.g. a Git branch, a
Subversion branch, a GitHub Pull Request etc.

See this blog post for more
info:<https://jenkins.io/blog/2015/12/03/pipeline-as-code-with-multibranch-workflows-in-jenkins/>

## Notes

To determine the branch being built - use the environment variable
`BRANCH_NAME` - e.g. `${env.BRANCH_NAME}`

## Version History

For version 2.23 and beyond, see the [GitHub releases](https://github.com/jenkinsci/workflow-multibranch-plugin/releases) list.
For older versions, see the [archive](https://github.com/jenkinsci/workflow-multibranch-plugin/blob/2e067658d86895c4c22005c4022cca53f65f98c1/CHANGELOG.md).
