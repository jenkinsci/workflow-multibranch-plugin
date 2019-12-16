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

import hudson.model.queue.QueueTaskFuture;
import java.util.Collections;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.NamedExceptionsBranchPropertyStrategy;
import jenkins.branch.NoTriggerBranchProperty;
import jenkins.branch.NoTriggerOrganizationFolderProperty;
import jenkins.branch.OrganizationFolder;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.impl.SingleSCMNavigator;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Integration test for {@link NoTriggerBranchProperty} and {@link NoTriggerOrganizationFolderProperty}.
 */
@Issue("JENKINS-32396")
public class NoTriggerBranchPropertyWorkflowTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Issue("JENKINS-30206")
    @Test public void singleRepo() throws Exception {
        round1();
        WorkflowMultiBranchProject p = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        BranchSource branchSource = new BranchSource(new GitSCMSource("source-id", sampleRepo.toString(), "", "*", "", false));
        branchSource.setStrategy(new NamedExceptionsBranchPropertyStrategy(new BranchProperty[0], new NamedExceptionsBranchPropertyStrategy.Named[] {
            new NamedExceptionsBranchPropertyStrategy.Named("release*", new BranchProperty[] {new NoTriggerBranchProperty()})
        }));
        p.getSourcesList().add(branchSource);
        // Should be initial builds of master & newfeature but not release.
        WorkflowJob master = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(p, "master");
        r.waitUntilNoActivity();
        assertEquals(2, master.getNextBuildNumber());
        WorkflowJob release = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(p, "release");
        assertEquals(1, release.getNextBuildNumber());
        WorkflowJob newfeature = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(p, "newfeature");
        assertEquals(2, newfeature.getNextBuildNumber());
        round2();
        WorkflowMultiBranchProjectTest.showIndexing(p);
        // Should be second builds of master & newfeature but not release.
        assertEquals(3, master.getNextBuildNumber());
        assertEquals(1, release.getNextBuildNumber());
        assertEquals(3, newfeature.getNextBuildNumber());
        // Should be able to manually build release.
        QueueTaskFuture<WorkflowRun> releaseBuild = release.scheduleBuild2(0);
        assertNotNull(releaseBuild);
        assertEquals(1, releaseBuild.get().getNumber());
        assertEquals(2, release.getNextBuildNumber());
        // Updating configuration should take effect for next time: new builds of newfeature & release but not master.
        branchSource = new BranchSource(new GitSCMSource("source-id", sampleRepo.toString(), "", "*", "", false));
        branchSource.setStrategy(new NamedExceptionsBranchPropertyStrategy(new BranchProperty[0], new NamedExceptionsBranchPropertyStrategy.Named[] {
            new NamedExceptionsBranchPropertyStrategy.Named("master", new BranchProperty[] {new NoTriggerBranchProperty()})
        }));
        p.getSourcesList().clear();
        p.getSourcesList().add(branchSource);
        round3();
        WorkflowMultiBranchProjectTest.showIndexing(p);
        assertEquals(3, master.getNextBuildNumber());
        assertEquals(3, release.getNextBuildNumber());
        assertEquals(4, newfeature.getNextBuildNumber());
    }

    @Test public void organizationFolder() throws Exception {
        round1();
        OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
        top.getProperties().add(new NoTriggerOrganizationFolderProperty("(?!release.*).*"));
        top.getNavigators().add(new SingleSCMNavigator("p", Collections.singletonList(new GitSCMSource("source-id", sampleRepo.toString(), "", "*", "", false))));
        top.scheduleBuild2(0).getFuture().get();
        r.waitUntilNoActivity();
        top.getComputation().writeWholeLogTo(System.out);
        WorkflowMultiBranchProject p = r.jenkins.getItemByFullName("top/p", WorkflowMultiBranchProject.class);
        assertNotNull(p);
        WorkflowJob master = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(p, "master");
        r.waitUntilNoActivity();
        assertEquals(2, master.getNextBuildNumber());
        WorkflowJob release = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(p, "release");
        assertEquals(1, release.getNextBuildNumber());
        WorkflowJob newfeature = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(p, "newfeature");
        assertEquals(2, newfeature.getNextBuildNumber());
        round2();
        WorkflowMultiBranchProjectTest.showIndexing(p);
        assertEquals(3, master.getNextBuildNumber());
        assertEquals(1, release.getNextBuildNumber());
        assertEquals(3, newfeature.getNextBuildNumber());
        QueueTaskFuture<WorkflowRun> releaseBuild = release.scheduleBuild2(0);
        assertNotNull(releaseBuild);
        assertEquals(1, releaseBuild.get().getNumber());
        assertEquals(2, release.getNextBuildNumber());
        top.getProperties().replace(new NoTriggerOrganizationFolderProperty("(?!master$).*"));
        round3();
        WorkflowMultiBranchProjectTest.showIndexing(p);
        assertEquals(3, master.getNextBuildNumber());
        assertEquals(3, release.getNextBuildNumber());
        assertEquals(4, newfeature.getNextBuildNumber());
    }

    private void round1() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--message=init");
        sampleRepo.git("checkout", "-b", "newfeature");
        sampleRepo.write("Jenkinsfile", "// newfeature");
        sampleRepo.git("commit", "--all", "--message=newfeature");
        sampleRepo.git("checkout", "-b", "release", "master");
        sampleRepo.write("Jenkinsfile", "// release");
        sampleRepo.git("commit", "--all", "--message=release");
    }

    private void round2() throws Exception {
        sampleRepo.git("checkout", "master");
        sampleRepo.write("Jenkinsfile", "// more");
        sampleRepo.git("commit", "--all", "--message=master-2");
        sampleRepo.git("checkout", "newfeature");
        sampleRepo.write("Jenkinsfile", "// more");
        sampleRepo.git("commit", "--all", "--message=newfeature-2");
        sampleRepo.git("checkout", "release");
        sampleRepo.write("Jenkinsfile", "// more");
        sampleRepo.git("commit", "--all", "--message=release-2");
        sampleRepo.notifyCommit(r);
    }

    private void round3() throws Exception {
        sampleRepo.git("checkout", "master");
        sampleRepo.write("Jenkinsfile", "// yet more");
        sampleRepo.git("commit", "--all", "--message=master-3");
        sampleRepo.git("checkout", "newfeature");
        sampleRepo.write("Jenkinsfile", "// yet more");
        sampleRepo.git("commit", "--all", "--message=newfeature-3");
        sampleRepo.git("checkout", "release");
        sampleRepo.write("Jenkinsfile", "// yet more");
        sampleRepo.git("commit", "--all", "--message=release-3");
        sampleRepo.notifyCommit(r);
    }

}
