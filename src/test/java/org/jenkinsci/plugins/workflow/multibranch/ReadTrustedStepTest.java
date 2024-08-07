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

import hudson.Functions;
import hudson.model.Result;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.GitStep;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jvnet.hudson.test.FlagRule;

import static org.junit.Assume.assumeFalse;

public class ReadTrustedStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();
    @Rule public FlagRule<Boolean> heavyweightCheckoutFlag = new FlagRule<>(() -> SCMBinder.USE_HEAVYWEIGHT_CHECKOUT, v -> { SCMBinder.USE_HEAVYWEIGHT_CHECKOUT = v; });

    @Test public void smokes() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"said ${readTrusted 'message'}\"");
        sampleRepo.write("message", "how do you do");
        sampleRepo.git("add", "Jenkinsfile", "message");
        sampleRepo.git("commit", "--all", "--message=defined");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new SCMBinderTest.WarySource(null, sampleRepo.toString(), "", "*", "", false)));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "master");
        r.waitUntilNoActivity();
        WorkflowRun b = p.getLastBuild();
        assertNotNull(b);
        assertEquals(1, b.getNumber());
        SCMBinderTest.assertRevisionAction(b);
        r.assertBuildStatusSuccess(b);
        r.assertLogContains("said how do you do", b);
        r.assertLogContains("Obtained message from ", b);
        String branch = "evil";
        sampleRepo.git("checkout", "-b", branch);
        sampleRepo.write("message", "your father smelt of elderberries");
        sampleRepo.git("commit", "--all", "--message=rude");
        p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, branch);
        r.waitUntilNoActivity();
        b = p.getLastBuild();
        assertNotNull(b);
        assertEquals(1, b.getNumber());
        SCMBinderTest.assertRevisionAction(b);
        r.assertBuildStatus(Result.FAILURE, b);
        r.assertLogContains(Messages.ReadTrustedStep__has_been_modified_in_an_untrusted_revis("message"), b);
        r.assertLogContains("Obtained message from ", b);
        sampleRepo.write("message", "how do you do");
        sampleRepo.write("ignored-message", "I fart in your general direction");
        sampleRepo.git("add", "ignored-message");
        sampleRepo.git("commit", "--all", "--message=less rude");
        sampleRepo.notifyCommit(r);
        b = p.getLastBuild();
        assertEquals(2, b.getNumber());
        SCMBinderTest.assertRevisionAction(b);
        r.assertBuildStatusSuccess(b);
        r.assertLogContains("said how do you do", b);
        r.assertLogContains("Obtained message from ", b);
    }

    @Test public void exactRevision() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "node {checkout scm; semaphore 'wait1'; def alpha = readTrusted 'alpha'; semaphore 'wait2'; echo \"first got ${alpha} then ${readTrusted 'beta'} vs. disk ${readFile 'alpha'} then ${readFile 'beta'}\"}");
        sampleRepo.write("alpha", "1");
        sampleRepo.write("beta", "1");
        sampleRepo.git("add", "Jenkinsfile", "alpha", "beta");
        sampleRepo.git("commit", "--all", "--message=defined");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new SCMBinderTest.WarySource(null, sampleRepo.toString(), "", "*", "", false)));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "master");
        SemaphoreStep.waitForStart("wait1/1", null);
        WorkflowRun b = p.getLastBuild();
        assertNotNull(b);
        assertEquals(1, b.getNumber());
        SCMBinderTest.assertRevisionAction(b);
        sampleRepo.write("alpha", "2");
        sampleRepo.git("commit", "--all", "--message=alpha-2");
        SemaphoreStep.success("wait1/1", null);
        SemaphoreStep.waitForStart("wait2/1", b);
        sampleRepo.write("beta", "2");
        sampleRepo.git("commit", "--all", "--message=beta-2");
        SemaphoreStep.success("wait2/1", null);
        r.assertLogContains("first got 1 then 1 vs. disk 1 then 1", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
        sampleRepo.write("Jenkinsfile", "def alpha = readTrusted 'alpha'; semaphore 'wait1'; node {checkout scm; semaphore 'wait2'; echo \"now got ${alpha} then ${readTrusted 'beta'} vs. disk ${readFile 'alpha'} then ${readFile 'beta'}\"}");
        sampleRepo.git("commit", "--all", "--message=new definition");
        b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait1/2", b);
        sampleRepo.write("alpha", "3");
        sampleRepo.git("commit", "--all", "--message=alpha-3");
        SemaphoreStep.success("wait1/2", null);
        SemaphoreStep.waitForStart("wait2/2", b);
        sampleRepo.write("beta", "3");
        sampleRepo.git("commit", "--all", "--message=beta-3");
        SemaphoreStep.success("wait2/2", null);
        r.assertLogContains("now got 2 then 2 vs. disk 2 then 2", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
    }

    @Test public void evaluate() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "evaluate readTrusted('lib.groovy')");
        sampleRepo.write("lib.groovy", "echo 'trustworthy library'");
        sampleRepo.git("add", "Jenkinsfile", "lib.groovy");
        sampleRepo.git("commit", "--all", "--message=defined");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new SCMBinderTest.WarySource(null, sampleRepo.toString(), "", "*", "", false)));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "master");
        r.waitUntilNoActivity();
        WorkflowRun b = p.getLastBuild();
        assertNotNull(b);
        assertEquals(1, b.getNumber());
        SCMBinderTest.assertRevisionAction(b);
        r.assertBuildStatusSuccess(b);
        r.assertLogContains("trustworthy library", b);
        String branch = "evil";
        sampleRepo.git("checkout", "-b", branch);
        sampleRepo.write("lib.groovy", "echo 'not trustworthy'");
        sampleRepo.git("commit", "--all", "--message=evil");
        p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, branch);
        r.waitUntilNoActivity();
        b = p.getLastBuild();
        assertNotNull(b);
        assertEquals(1, b.getNumber());
        SCMBinderTest.assertRevisionAction(b);
        r.assertBuildStatus(Result.FAILURE, b);
        r.assertLogContains(Messages.ReadTrustedStep__has_been_modified_in_an_untrusted_revis("lib.groovy"), b);
        r.assertLogNotContains("not trustworthy", b);
    }

    @Issue("JENKINS-31386")
    @Test public void nonMultibranch() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"said ${readTrusted 'message'}\"");
        sampleRepo.write("message", "how do you do");
        sampleRepo.git("add", "Jenkinsfile", "message");
        sampleRepo.git("commit", "--all", "--message=defined");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        GitStep step = new GitStep(sampleRepo.toString());
        p.setDefinition(new CpsScmFlowDefinition(step.createSCM(), "Jenkinsfile"));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("said how do you do", b);
        r.assertLogContains("Obtained message from git ", b);
    }

    @Issue("JENKINS-42817")
    @Test public void nonMultibranchHeavyweight() throws Exception {
        SCMBinder.USE_HEAVYWEIGHT_CHECKOUT = true;
        try {
            sampleRepo.init();
            sampleRepo.write("Jenkinsfile", "echo \"said ${readTrusted 'message'}\"");
            sampleRepo.write("message", "how do you do");
            sampleRepo.git("add", "Jenkinsfile", "message");
            sampleRepo.git("commit", "--all", "--message=defined");
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            GitStep step = new GitStep(sampleRepo.toString());
            CpsScmFlowDefinition def = new CpsScmFlowDefinition(step.createSCM(), "Jenkinsfile");
            def.setLightweight(true);
            p.setDefinition(def);
            WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
            r.assertLogContains("said how do you do", b);
        } finally {
            SCMBinder.USE_HEAVYWEIGHT_CHECKOUT = false;
        }
    }

    @Test
    public void pathTraversalRejected() throws Exception {
        SCMBinder.USE_HEAVYWEIGHT_CHECKOUT = true;
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "node { checkout scm; echo \"${readTrusted '../../secrets/master.key'}\"}");
        Path secrets = Paths.get(sampleRepo.getRoot().getPath(), "secrets");
        Files.createSymbolicLink(secrets, Paths.get(r.jenkins.getRootDir() + "/secrets"));
        sampleRepo.git("add", ".");
        sampleRepo.git("commit", "-m", "init");

        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new SCMBinderTest.WarySource(null, sampleRepo.toString(), "", "*", "", false)));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "master");
        r.waitUntilNoActivity();

        WorkflowRun b = p.getLastBuild();
        assertEquals(1, b.getNumber());
        r.assertLogContains("master.key references a file that is not inside " + r.jenkins.getWorkspaceFor(p).getRemote(), b);
    }

    @Issue("SECURITY-2491")
    @Test
    public void symlinksInReadTrustedCannotEscapeWorkspaceContext() throws Exception {
        assumeFalse(Functions.isWindows()); // On Windows, the symlink is treated as a regular file, so there is no vulnerability, but the behavior is different.
        SCMBinder.USE_HEAVYWEIGHT_CHECKOUT = true;
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "node { checkout scm; echo \"${readTrusted 'secrets/master.key'}\"}");
        Path secrets = Paths.get(sampleRepo.getRoot().getPath(), "secrets");
        Files.createSymbolicLink(secrets, Paths.get(r.jenkins.getRootDir() + "/secrets"));
        sampleRepo.git("add", ".");
        sampleRepo.git("commit", "-m", "init");

        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new SCMBinderTest.WarySource(null, sampleRepo.toString(), "", "*", "", false)));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "master");
        r.waitUntilNoActivity();

        WorkflowRun run = p.getLastBuild();
        assertEquals(1, run.getNumber());
        r.assertLogContains("secrets/master.key references a file that is not inside " + r.jenkins.getWorkspaceFor(p).getRemote(), run);
    }

    @Issue("SECURITY-2491")
    @Test
    public void symlinksInUntrustedRevisionCannotEscapeWorkspace() throws Exception {
        assumeFalse(Functions.isWindows()); // On Windows, the symlink is treated as a regular file, so there is no vulnerability, but the behavior is different.
        SCMBinder.USE_HEAVYWEIGHT_CHECKOUT = true;
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "node { checkout scm; echo \"${readTrusted 'secrets/master.key'}\"}");
        sampleRepo.write("secrets/master.key", "secret info");
        sampleRepo.git("add", ".");
        sampleRepo.git("commit", "-m", "init");
        sampleRepo.git("checkout", "-b", "feature");
        Path secrets = Paths.get(sampleRepo.getRoot().getPath(), "secrets");
        Files.delete(Paths.get(secrets.toString(), "master.key"));
        Files.delete(secrets);
        Files.createSymbolicLink(secrets, Paths.get(r.jenkins.getRootDir() + "/secrets"));
        sampleRepo.git("add", ".");
        sampleRepo.git("commit", "-m", "now with unsafe symlink");

        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new SCMBinderTest.WarySource(null, sampleRepo.toString(), "", "*", "", false)));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "feature");
        r.waitUntilNoActivity();

        WorkflowRun run = p.getLastBuild();
        assertEquals(1, run.getNumber());
        r.assertLogContains("secrets/master.key references a file that is not inside ", run);
    }

    @Issue("SECURITY-2491")
    @Test
    public void symlinksInNonMultibranchCannotEscapeWorkspaceContextViaReadTrusted() throws Exception {
        assumeFalse(Functions.isWindows()); // On Windows, the symlink is treated as a regular file, so there is no vulnerability, but the behavior is different.
        SCMBinder.USE_HEAVYWEIGHT_CHECKOUT = true;
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"${readTrusted 'master.key'}\"");
        Path secrets = Paths.get(sampleRepo.getRoot().getPath(), "master.key");
        Files.createSymbolicLink(secrets, Paths.get(r.jenkins.getRootDir() + "/secrets/master.key"));
        sampleRepo.git("add", ".");
        sampleRepo.git("commit", "-m", "init");

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        GitStep step = new GitStep(sampleRepo.toString());
        p.setDefinition(new CpsScmFlowDefinition(step.createSCM(), "Jenkinsfile"));
        WorkflowRun run = r.buildAndAssertStatus(Result.FAILURE, p);

        r.assertLogContains("master.key references a file that is not inside " + r.jenkins.getWorkspaceFor(p), run);
    }

}
