/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import com.cloudbees.hudson.plugins.folder.computed.DefaultOrphanedItemStrategy;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.plugins.git.util.BuildData;
import hudson.scm.ChangeLogSet;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import jenkins.branch.BranchSource;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitBranchSCMRevision;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.impl.subversion.SubversionSCMSource;
import static org.hamcrest.Matchers.*;

import jenkins.scm.impl.subversion.SubversionSampleRepoRule;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
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

public class SCMBinderTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleGitRepo = new GitSampleRepoRule();
    @Rule public SubversionSampleRepoRule sampleSvnRepo = new SubversionSampleRepoRule();

    @Test public void exactRevisionGit() throws Exception {
        sampleGitRepo.init();
        ScriptApproval sa = ScriptApproval.get();
        sa.approveSignature("staticField hudson.model.Items XSTREAM2");
        sa.approveSignature("method com.thoughtworks.xstream.XStream toXML java.lang.Object");
        sampleGitRepo.write("Jenkinsfile", "echo hudson.model.Items.XSTREAM2.toXML(scm); semaphore 'wait'; node {checkout scm; echo readFile('file')}");
        sampleGitRepo.write("file", "initial content");
        sampleGitRepo.git("add", "Jenkinsfile");
        sampleGitRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleGitRepo.toString(), "", "*", "", false)));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "master");
        SemaphoreStep.waitForStart("wait/1", null);
        WorkflowRun b1 = p.getLastBuild();
        assertNotNull(b1);
        assertEquals(1, b1.getNumber());
        assertRevisionAction(b1);
        r.assertLogContains("Obtained Jenkinsfile from ", b1);
        sampleGitRepo.write("Jenkinsfile", "node {checkout scm; echo readFile('file').toUpperCase()}");
        sampleGitRepo.write("file", "subsequent content");
        sampleGitRepo.git("commit", "--all", "--message=tweaked");
        SemaphoreStep.success("wait/1", null);
        sampleGitRepo.notifyCommit(r);
        WorkflowRun b2 = p.getLastBuild();
        assertEquals(2, b2.getNumber());
        r.assertLogContains("initial content", r.waitForCompletion(b1));
        r.assertLogContains("SUBSEQUENT CONTENT", b2);
        assertRevisionAction(b2);
        WorkflowMultiBranchProjectTest.showIndexing(mp);
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b2.getChangeSets();
        assertEquals(1, changeSets.size());
        ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
        assertEquals(b2, changeSet.getRun());
        assertEquals("git", changeSet.getKind());
        Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        ChangeLogSet.Entry entry = iterator.next();
        assertEquals("tweaked", entry.getMsg());
        assertEquals("[Jenkinsfile, file]", new TreeSet<>(entry.getAffectedPaths()).toString());
        assertFalse(iterator.hasNext());
    }

    public static void assertRevisionAction(WorkflowRun build) {
        SCMRevisionAction revisionAction = build.getAction(SCMRevisionAction.class);
        assertNotNull(revisionAction);
        SCMRevision revision = revisionAction.getRevision();
        assertEquals(GitBranchSCMRevision.class, revision.getClass());
        Set<String> expected = new HashSet<>();
        List<BuildData> buildDataActions = build.getActions(BuildData.class);
        if (!buildDataActions.isEmpty()) { // i.e., we have run at least one checkout step, or done a heavyweight checkout to get a single file
            for (BuildData data : buildDataActions) {
                expected.add(data.lastBuild.marked.getSha1().getName());
            }
            assertThat(expected, hasItem(((GitBranchSCMRevision) revision).getHash()));
        }
    }

    @Test public void exactRevisionSubversion() throws Exception {
        sampleSvnRepo.init();
        ScriptApproval sa = ScriptApproval.get();
        sa.approveSignature("staticField hudson.model.Items XSTREAM2");
        sa.approveSignature("method com.thoughtworks.xstream.XStream toXML java.lang.Object");
        sampleSvnRepo.write("Jenkinsfile", "echo hudson.model.Items.XSTREAM2.toXML(scm); semaphore 'wait'; node {checkout scm; echo readFile('file')}");
        sampleSvnRepo.write("file", "initial content");
        sampleSvnRepo.svnkit("add", sampleSvnRepo.wc() + "/Jenkinsfile");
        sampleSvnRepo.svnkit("commit", "--message=flow", sampleSvnRepo.wc());
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new SubversionSCMSource(null, sampleSvnRepo.prjUrl())));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "trunk");
        SemaphoreStep.waitForStart("wait/1", null);
        WorkflowRun b1 = p.getLastBuild();
        assertNotNull(b1);
        assertEquals(1, b1.getNumber());
        sampleSvnRepo.write("Jenkinsfile", "node {checkout scm; echo readFile('file').toUpperCase()}");
        sampleSvnRepo.write("file", "subsequent content");
        sampleSvnRepo.svnkit("commit", "--message=tweaked", sampleSvnRepo.wc());
        SemaphoreStep.success("wait/1", null);
        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(2, b2.getNumber());
        r.assertLogContains("initial content", r.waitForCompletion(b1));
        r.assertLogContains("SUBSEQUENT CONTENT", b2);
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b2.getChangeSets();
        /* TODO JENKINS-29326 analogue: currently 2 (they are the same):
        assertEquals(1, changeSets.size());
        */
        ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
        assertEquals(b2, changeSet.getRun());
        assertEquals("svn", changeSet.getKind());
        Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        ChangeLogSet.Entry entry = iterator.next();
        assertEquals("tweaked", entry.getMsg());
        assertEquals("[/prj/trunk/Jenkinsfile, /prj/trunk/file]", new TreeSet<>(entry.getAffectedPaths()).toString());
        assertFalse(iterator.hasNext());
    }

    @Test public void deletedJenkinsfile() throws Exception {
        sampleGitRepo.init();
        sampleGitRepo.write("Jenkinsfile", "node { echo 'Hello World' }");
        sampleGitRepo.git("add", "Jenkinsfile");
        sampleGitRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleGitRepo.toString(), "", "*", "", false)));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "master");
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        sampleGitRepo.git("rm", "Jenkinsfile");
        sampleGitRepo.git("commit", "--all", "--message=remove");
        WorkflowRun b2 = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        r.assertLogContains("Jenkinsfile not found", b2);
    }

    @Issue("JENKINS-40521")
    @Test public void deletedBranch() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        sampleGitRepo.init();
        // TODO GitSCMSource offers no way to set a GitSCMExtension such as CleanBeforeCheckout; work around with deleteDir
        // (without cleaning, b2 will succeed since the workspace will still have a cached origin/feature ref)
        sampleGitRepo.write("Jenkinsfile", "node {deleteDir(); checkout scm; echo 'Hello World'}");
        sampleGitRepo.git("add", "Jenkinsfile");
        sampleGitRepo.git("commit", "--all", "--message=flow");
        sampleGitRepo.git("checkout", "-b", "feature");
        sampleGitRepo.write("somefile", "stuff");
        sampleGitRepo.git("add", "somefile");
        sampleGitRepo.git("commit", "--all", "--message=tweaked");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleGitRepo.toString(), "", "*", "", false)));
        mp.setOrphanedItemStrategy(new DefaultOrphanedItemStrategy(false, "", ""));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "feature");
        assertEquals(2, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        Authentication auth = User.getById("dev", true).impersonate();
        assertFalse(p.getACL().hasPermission(auth, Item.DELETE));
        assertTrue(p.isBuildable());
        sampleGitRepo.git("checkout", "master");
        sampleGitRepo.git("branch", "-D", "feature");
        { // TODO AbstractGitSCMSource.retrieve(SCMHead, TaskListener) is incorrect: after fetching remote refs into the cache,
          // the origin/feature ref remains locally even though it has been deleted upstream, since only the other overload prunes stale remotes:
            Util.deleteRecursive(new File(r.jenkins.getRootDir(), "caches"));
        }
        WorkflowRun b2 = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        r.assertLogContains("nondeterministic checkout", b2); // SCMBinder
        r.assertLogContains("Could not determine exact tip revision of feature", b2); // SCMVar
        mp.scheduleBuild2(0).getFuture().get();
        WorkflowMultiBranchProjectTest.showIndexing(mp);
        assertEquals(2, mp.getItems().size());
        assertTrue(p.getACL().hasPermission(auth, Item.DELETE));
        assertFalse(p.isBuildable());
        mp.setOrphanedItemStrategy(new DefaultOrphanedItemStrategy(true, "", "0"));
        mp.scheduleBuild2(0).getFuture().get();
        WorkflowMultiBranchProjectTest.showIndexing(mp);
        assertEquals(1, mp.getItems().size());
    }

    @Test public void untrustedRevisions() throws Exception {
        sampleGitRepo.init();
        sampleGitRepo.write("Jenkinsfile", "node {checkout scm; echo readFile('file')}");
        sampleGitRepo.write("file", "initial content");
        sampleGitRepo.git("add", "Jenkinsfile");
        sampleGitRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new WarySource(null, sampleGitRepo.toString(), "", "*", "", false)));
        WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "master");
        r.waitUntilNoActivity();
        WorkflowRun b = p.getLastBuild();
        assertNotNull(b);
        assertEquals(1, b.getNumber());
        assertRevisionAction(b);
        r.assertBuildStatusSuccess(b);
        r.assertLogContains("initial content", b);
        String branch = "some-other-branch-from-Norway";
        sampleGitRepo.git("checkout", "-b", branch);
        sampleGitRepo.write("Jenkinsfile", "error 'ALL YOUR BUILD STEPS ARE BELONG TO US'");
        sampleGitRepo.write("file", "subsequent content");
        sampleGitRepo.git("commit", "--all", "--message=big evil laugh");
        p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, branch);
        r.waitUntilNoActivity();
        b = p.getLastBuild();
        assertNotNull(b);
        assertEquals(1, b.getNumber());
        assertRevisionAction(b);
        r.assertBuildStatusSuccess(b);
        r.assertLogContains(Messages.ReadTrustedStep__has_been_modified_in_an_untrusted_revis("Jenkinsfile"), b);
        r.assertLogContains("subsequent content", b);
        r.assertLogContains("not trusting", b);
    }
    public static class WarySource extends GitSCMSource {
        public WarySource(String id, String remote, String credentialsId, String includes, String excludes, boolean ignoreOnPushNotifications) {
            super(id, remote, credentialsId, includes, excludes, ignoreOnPushNotifications);
        }
        @Override public SCMRevision getTrustedRevision(SCMRevision revision, TaskListener listener) throws IOException, InterruptedException {
            String branch = revision.getHead().getName();
            if (branch.equals("master")) {
                return revision;
            } else {
                listener.getLogger().println("not trusting " + branch);
                return fetch(new SCMHead("master"), listener);
            }
        }
        @Override public SCMSourceDescriptor getDescriptor() {
            return Jenkins.getInstance().getDescriptorByType(GitSCMSource.DescriptorImpl.class);
        }
    }

}
