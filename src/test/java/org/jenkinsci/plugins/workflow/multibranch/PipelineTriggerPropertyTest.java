package org.jenkinsci.plugins.workflow.multibranch;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Shell;
import hudson.util.RunList;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class PipelineTriggerPropertyTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
    @Rule
    public GitSampleRepoRule gitRepo = new GitSampleRepoRule();

    private String pipelineScript = "pipeline { agent any; stages { stage('Test') { steps { echo test } } } }";
    private String pipelineFile = "Jenkinsfile";
    private String projectName = "PipelineTriggerPropertyTestJob";
    private String preTriggerJobName = "PreTriggerJob";
    private String postTriggerJobName = "PostTriggerJob";
    private String additionalBranchName = "feature";

    private int expectedBuildNumPerJob = 2;
    private int expectedPipelineCount = 2;


    @Before
    public void setup() throws Exception {
        this.initSourceCodeRepo();
    }

    @Test
    public void pipelineTriggerPropertyTest() throws Exception {

        //Create Free Style Jobs for Testing Trigger
        FreeStyleProject preTriggerJob = jenkins.createFreeStyleProject(this.preTriggerJobName);
        FreeStyleProject postTriggerJob = jenkins.createFreeStyleProject(this.postTriggerJobName);

        //Create Multi Branch Pipeline Job with Git Repo
        WorkflowMultiBranchProject workflowMultiBranchProject = this.jenkins.createProject(WorkflowMultiBranchProject.class, this.projectName);
        workflowMultiBranchProject.getSourcesList().add(new BranchSource(new GitSCMSource(null, this.gitRepo.toString(), "", "*", "", false)));
        workflowMultiBranchProject.getProperties().add(new PipelineTriggerProperty(preTriggerJobName, postTriggerJobName));
        this.indexMultiBranchPipeline(workflowMultiBranchProject, this.expectedPipelineCount);
        this.jenkins.waitUntilNoActivity();

        //Test Pre Trigger Jobs
        this.checkTriggeredJobs(preTriggerJob);

        //Change Branch Source and set Include field to None to test Pipeline Delete by Branch Indexing
        workflowMultiBranchProject.getSourcesList().clear();
        workflowMultiBranchProject.getSourcesList().add(new BranchSource(new GitSCMSource(null, this.gitRepo.toString(), "", "none", "", false)));
        this.indexMultiBranchPipeline(workflowMultiBranchProject, 0);
        this.jenkins.waitUntilNoActivity();

        //Test Post Trigger Jobs
        this.checkTriggeredJobs(postTriggerJob);

    }

    private void indexMultiBranchPipeline(WorkflowMultiBranchProject workflowMultiBranchProject, int expectedPipelineCount) throws Exception {
        workflowMultiBranchProject.scheduleBuild2(0);
        this.jenkins.waitUntilNoActivity();
        assertEquals(expectedPipelineCount, workflowMultiBranchProject.getItems().size());
    }

    private void initSourceCodeRepo() throws Exception {
        this.gitRepo.init();
        this.gitRepo.write(this.pipelineFile, this.pipelineScript);
        this.gitRepo.git("add", this.pipelineFile);
        this.gitRepo.git("commit", "--all", "--message=InitRepoWithFile");
        this.gitRepo.git("checkout", "-b", this.additionalBranchName);
    }

    private void checkTriggeredJobs(FreeStyleProject triggeredJob) throws Exception {
        RunList<FreeStyleBuild> builds = triggeredJob.getBuilds();
        assertEquals(this.expectedBuildNumPerJob, builds.size());
        Iterator<FreeStyleBuild> iterator = builds.iterator();
        while (iterator.hasNext()) {
            FreeStyleBuild build = iterator.next();
            Map<String, String> buildVariables = build.getBuildVariables();
            if( buildVariables.containsKey(PipelineTriggerProperty.projectNameParameterKey) ) {
                String value = buildVariables.get(PipelineTriggerProperty.projectNameParameterKey);
                if (!(value.contains("master") || value.contains(this.additionalBranchName))) {
                    throw new Exception("Trigger Pipeline name not found in the Job Logs");
                }
            }
            else {
                throw new Exception(PipelineTriggerProperty.projectNameParameterKey + " key not found in Build Variables");
            }
        }
    }
}
