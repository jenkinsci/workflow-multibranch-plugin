/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Integration test for {@link SuppressBranchIndexingBuildsJobProperty}
 */
@Issue("JENKINS-37219")
public class SuppressBranchIndexingBuildsJobPropertyTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Issue("JENKINS-37219")
    @Test public void singleRepo() throws Exception {
        round1();
        WorkflowMultiBranchProject p = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        BranchSource branchSource = new BranchSource(new GitSCMSource("source-id", sampleRepo.toString(), "", "*", "", false));
        p.getSourcesList().add(branchSource);

        // Should be initial build of master, which sets the job property.
        WorkflowJob master = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(p, "master");
        r.waitUntilNoActivity();
        r.assertBuildStatusSuccess(master.getBuildByNumber(1));
        assertEquals(2, master.getNextBuildNumber());

        assertNotNull(master.getProperty(SuppressBranchIndexingBuildsJobProperty.class));
        round2();

        WorkflowMultiBranchProjectTest.showIndexing(p);
        // Should not be a new build.
        assertEquals(2, master.getNextBuildNumber());

        // Should be able to manually build master, which should result in the blocking going away.
        WorkflowRun secondBuild = r.assertBuildStatusSuccess(master.scheduleBuild2(0));
        assertNotNull(secondBuild);
        assertEquals(2, secondBuild.getNumber());
        assertEquals(3, master.getNextBuildNumber());
        assertNull(master.getProperty(SuppressBranchIndexingBuildsJobProperty.class));


        // Now let's see it actually trigger another build from a new commit.
        round3();
        WorkflowMultiBranchProjectTest.showIndexing(p);
        assertEquals(4, master.getNextBuildNumber());
    }


    private void round1() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "properties([suppressBranchIndexingBuilds()])");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--message=init");
    }

    private void round2() throws Exception {
        sampleRepo.git("checkout", "master");
        sampleRepo.write("Jenkinsfile", "properties([])");
        sampleRepo.git("commit", "--all", "--message=master-2");
        sampleRepo.notifyCommit(r);
    }

    private void round3() throws Exception {
        sampleRepo.git("checkout", "master");
        sampleRepo.write("Jenkinsfile", "// yet more");
        sampleRepo.git("commit", "--all", "--message=master-3");
        sampleRepo.notifyCommit(r);
    }

}
