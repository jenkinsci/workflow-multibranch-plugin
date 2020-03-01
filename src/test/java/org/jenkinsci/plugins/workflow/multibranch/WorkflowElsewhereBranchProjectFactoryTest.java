/*
 * The MIT License
 *
 * Copyright (c) 2020, Filipe Cristovao
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.multibranch;

import hudson.model.Item;
import hudson.plugins.git.GitSCM;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class WorkflowElsewhereBranchProjectFactoryTest {

    static final String PROJECT_NAME = "under-test";
    static final String PIPELINE_BY_SCRIPT_LOG = "Flow Definition from defined script";
    static final String PIPELINE_SCM_MASTER_LOG = "Flow Definition from SCM master";
    static final String PIPELINE_SCM_BRANCH_LOG = "Flow Definition from SCM random-branch";
    static final String PIPELINE_NEVER_TO_BE_RUN_LOG = "This should never run";
    static final List<String> MARKED_PIPELINE_BRANCH_NAMES =
            Stream.of("with-marker1", "with-marker2", "with-marker-and-jenkinsfile")
                  .collect(toList());

    @Rule
    public JenkinsRule j = new JenkinsRule();
    @Rule
    public GitSampleRepoRule observedRepo = new GitSampleRepoRule();
    @Rule
    public GitSampleRepoRule pipelinesRepo = new GitSampleRepoRule();

    @Before
    public void setUp() throws Exception {
        setupMarkedBranchesFor(observedRepo);
        setupPipelineScriptFor(pipelinesRepo);
    }

    @Test
    public void workflowFactoryShouldOnlyCreateJobsForMarkedBranches() throws Exception {
        WorkflowMultiBranchProject mp = setupWorkflowMultiBranchProjectFor(definedPipelineFlowDefinition());

        // Wait for the scan to happen and generate the jobs
        mp.scheduleBuild2(0).getFuture().get();

        List<String> fullNames = mp.getAllItems()
                                   .stream()
                                   .map(Item::getFullName)
                                   .collect(toList());

        assertEquals(fullNames.size(), MARKED_PIPELINE_BRANCH_NAMES.size());

        // Ensure that the items are named as we expect
        for (String branchName : MARKED_PIPELINE_BRANCH_NAMES) {
            assertThat(fullNames, hasItem(PROJECT_NAME + "/" + branchName));
        }
    }

    @Test
    public void workflowFactoryShouldRunDefinedFlowDefinition() throws Exception {
        WorkflowMultiBranchProject mp = setupWorkflowMultiBranchProjectFor(definedPipelineFlowDefinition());

        // Wait for the scan to happen and generate the jobs
        mp.scheduleBuild2(0).getFuture().get();

        j.waitUntilNoActivity();

        // Ensure that it run the Pipeline that was expected
        for (String branchName : MARKED_PIPELINE_BRANCH_NAMES) {
            WorkflowJob p = mp.getItem(branchName);
            assertNotNull(p);
            WorkflowRun firstBuild = p.getLastBuild(); // The last build should be the first at this stage
            assertEquals(1, firstBuild.getNumber());
            j.assertLogContains(PIPELINE_BY_SCRIPT_LOG, firstBuild);
            j.assertLogNotContains(PIPELINE_NEVER_TO_BE_RUN_LOG, firstBuild);
        }
    }

    @Test
    public void workflowFactoryShouldGetFlowDefinitionFromScm() throws Exception {
        WorkflowMultiBranchProject mp = setupWorkflowMultiBranchProjectFor(scmPipelineFlowDefinition(pipelinesRepo));

        // Wait for the scan to happen and generate the jobs
        mp.scheduleBuild2(0).getFuture().get();

        j.waitUntilNoActivity();

        // Ensure that it run the Pipeline that was expected
        for (String branchName : MARKED_PIPELINE_BRANCH_NAMES) {
            WorkflowJob p = mp.getItem(branchName);
            assertNotNull(p);
            WorkflowRun firstBuild = p.getLastBuild(); // The last build should be the first at this stage
            assertEquals(1, firstBuild.getNumber());
            j.assertLogContains(PIPELINE_SCM_MASTER_LOG, firstBuild);
            j.assertLogNotContains(PIPELINE_NEVER_TO_BE_RUN_LOG, firstBuild);
        }

        // Change the flow definition to come from another branch:
        mp.setProjectFactory(new WorkflowElsewhereBranchProjectFactory(scmPipelineFlowDefinition(pipelinesRepo, "another-branch")));

        // Wait for the scan to happen (although no changes are expected)
        mp.scheduleBuild2(0).getFuture().get();

        j.waitUntilNoActivity();

        // Ensure that it run the Pipeline that was expected
        for (String branchName : MARKED_PIPELINE_BRANCH_NAMES) {
            WorkflowJob p = mp.getItem(branchName);
            assertNotNull(p);
            // There should've been no change, therefore no new run:
            assertEquals(1, p.getLastBuild().getNumber());
            // Schedule a new build. This time will use the new Jenkinsfile from 'another-branch'
            WorkflowRun newBuild = p.scheduleBuild2(0).get();
            assertEquals(2, newBuild.getNumber());
            // It should contain the branch's message, instead of master's:
            j.assertLogContains(PIPELINE_SCM_BRANCH_LOG, newBuild);
            j.assertLogNotContains(PIPELINE_NEVER_TO_BE_RUN_LOG, newBuild);
        }
    }

    private WorkflowMultiBranchProject setupWorkflowMultiBranchProjectFor(FlowDefinition flowDefinition) throws IOException {
        WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, PROJECT_NAME);
        GitSCMSource source = new GitSCMSource(observedRepo.toString());
        source.setTraits(singletonList(new BranchDiscoveryTrait()));
        mp.setSourcesList(singletonList(new BranchSource(source)));
        mp.setProjectFactory(new WorkflowElsewhereBranchProjectFactory(flowDefinition));
        return mp;
    }

    private void setupMarkedBranchesFor(GitSampleRepoRule repo) throws Exception {
        repo.init();
        for (String markedPipelineBranchName : MARKED_PIPELINE_BRANCH_NAMES) {
            repo.git("checkout", "-b", markedPipelineBranchName);
            repo.write(WorkflowElsewhereBranchProjectFactory.MARKER_FILE, "irrelevant-content");
            repo.git("add", WorkflowElsewhereBranchProjectFactory.MARKER_FILE);
            repo.git("commit", "--all", "--message='Added marker file'");
            repo.git("checkout", "master");
        }
        repo.git("checkout", "-b", "with-jenkinsfile");
        repo.write("Jenkinsfile", getScript(PIPELINE_NEVER_TO_BE_RUN_LOG));
        repo.git("add", "Jenkinsfile");
        repo.git("commit", "--all", "--message='Added Jenkinsfile'");
        repo.git("checkout", "master");
        // Additionally, add a Jenkinsfile in a marked branch to see if it gets run:
        repo.git("checkout", MARKED_PIPELINE_BRANCH_NAMES.get(MARKED_PIPELINE_BRANCH_NAMES.size() - 1));
        repo.write("Jenkinsfile", getScript(PIPELINE_NEVER_TO_BE_RUN_LOG));
        repo.git("add", "Jenkinsfile");
        repo.git("commit", "--all", "--message='Added Jenkinsfile'");
        repo.git("checkout", "master");
    }

    private void setupPipelineScriptFor(GitSampleRepoRule repo) throws Exception {
        repo.init();
        repo.git("checkout", "master");
        repo.write("Jenkinsfile", getScript(PIPELINE_SCM_MASTER_LOG));
        repo.git("add", "Jenkinsfile");
        repo.git("commit", "--all", "--message='Added Jenkinsfile'");
        repo.git("checkout", "-b", "another-branch");
        repo.write("Jenkinsfile", getScript(PIPELINE_SCM_BRANCH_LOG));
        repo.git("commit", "--all", "--message='Changed Jenkinsfile'");
        repo.git("checkout", "master");
    }

    private static FlowDefinition definedPipelineFlowDefinition() {
        return new CpsFlowDefinition(getScript(PIPELINE_BY_SCRIPT_LOG), true);
    }

    private static String getScript(String pipelineByScriptLog) {
        return "echo '" + pipelineByScriptLog + "'";
    }

    private static FlowDefinition scmPipelineFlowDefinition(GitSampleRepoRule repo) {
        return scmPipelineFlowDefinition(repo, "master");
    }

    private static FlowDefinition scmPipelineFlowDefinition(GitSampleRepoRule repo, String branchName) {
        GitSCM source = new GitSCM(repo.toString());
        source.getBranches().get(0).setName("*/" + branchName);
        return new CpsScmFlowDefinition(source, "Jenkinsfile");
    }
}
