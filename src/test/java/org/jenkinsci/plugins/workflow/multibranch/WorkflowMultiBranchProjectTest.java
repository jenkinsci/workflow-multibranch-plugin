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

import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.AbstractItem;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.listeners.ItemListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchPropertyDescriptor;
import jenkins.branch.BranchPropertyStrategy;
import jenkins.branch.BranchPropertyStrategyDescriptor;
import jenkins.branch.BranchSource;
import jenkins.branch.BuildRetentionBranchProperty;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.branch.NamedExceptionsBranchPropertyStrategy;
import jenkins.branch.NoTriggerBranchProperty;
import jenkins.branch.RateLimitBranchProperty;
import jenkins.branch.UntrustedBranchProperty;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitBranchSCMHead;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import jenkins.scm.impl.SingleSCMSource;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@WithGitSampleRepo
class WorkflowMultiBranchProjectTest {

    @SuppressWarnings("unused")
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();
    private JenkinsRule r;
    private GitSampleRepoRule sampleRepo;

    @BeforeEach
    void setUp(JenkinsRule rule, GitSampleRepoRule repo) {
        r = rule;
        sampleRepo = repo;
    }

    @Test
    void basicBranches() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file')}");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertEquals(new GitBranchSCMHead("master"), SCMHead.HeadByItem.findHead(p));
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("initial content", b1);
        r.assertLogContains("branch=master", b1);
        sampleRepo.git("checkout", "-b", "feature");
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase()}");
        sampleRepo.write("file", "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");
        p = scheduleAndFindBranchProject(mp, "feature");
        assertEquals(2, mp.getItems().size());
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("SUBSEQUENT CONTENT", b1);
        r.assertLogContains("branch=feature", b1);
    }

    // TODO commit notifications can both add branch projects and build them
    // TODO scheduled reindexing can add branch projects
    // TODO regular polling works on branch projects
    // TODO changelog shows per-branch changes

    public static @NonNull WorkflowJob scheduleAndFindBranchProject(@NonNull WorkflowMultiBranchProject mp, @NonNull String name) throws Exception {
        mp.scheduleBuild2(0).getFuture().get();
        return findBranchProject(mp, name);
    }

    public static @NonNull WorkflowJob findBranchProject(@NonNull WorkflowMultiBranchProject mp, @NonNull String name) throws Exception {
        WorkflowJob p = mp.getItem(name);
        showIndexing(mp);
        if (p == null) {
            fail(name + " project not found");
        }
        return p;
    }

    static void showIndexing(@NonNull WorkflowMultiBranchProject mp) throws Exception {
        FolderComputation<?> indexing = mp.getIndexing();
        System.out.println("---%<--- " + indexing.getUrl());
        indexing.writeWholeLogTo(System.out);
        System.out.println("---%<--- ");
    }

    @Issue({"JENKINS-32396", "JENKINS-32670"})
    @Test
    void visibleBranchProperties() throws Exception {
        WorkflowMultiBranchProject p = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        Set<Class<? extends BranchProperty>> propertyTypes = new HashSet<>();
        for (BranchPropertyDescriptor d : DescriptorVisibilityFilter.apply(p, BranchPropertyDescriptor.all())) {
            propertyTypes.add(d.clazz);
        }
        // RateLimitBranchProperty & BuildRetentionBranchProperty hidden by JobPropertyStep.HideSuperfluousBranchProperties.
        // UntrustedBranchProperty hidden because it applies only to Project.
        assertTrue(propertyTypes.contains(NoTriggerBranchProperty.class));
        assertTrue(propertyTypes.contains(DurabilityHintBranchProperty.class));
        assertFalse(propertyTypes.contains(BuildRetentionBranchProperty.class));
        assertFalse(propertyTypes.contains(RateLimitBranchProperty.class));
        assertFalse(propertyTypes.contains(UntrustedBranchProperty.class));

        Set<Class<? extends BranchPropertyStrategy>> strategyTypes = new HashSet<>();
        for (BranchPropertyStrategyDescriptor d : r.jenkins.getDescriptorByType(BranchSource.DescriptorImpl.class).propertyStrategyDescriptors(p, r.jenkins.getDescriptorByType(SingleSCMSource.DescriptorImpl.class))) {
            strategyTypes.add(d.clazz);
        }
        assertEquals(new HashSet<>(Arrays.asList(DefaultBranchPropertyStrategy.class, NamedExceptionsBranchPropertyStrategy.class)), strategyTypes);
    }

    @SuppressWarnings("rawtypes")
    @Test
    void applicableSCMs() throws Exception {
        final WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        List<Class> scmTypes = new ArrayList<>();
        List<SCMDescriptor<?>> scmDescriptors = SingleSCMSource.DescriptorImpl.getSCMDescriptors(mp);
        for (SCMDescriptor<?> scmDescriptor : scmDescriptors) {
            scmTypes.add(scmDescriptor.clazz);
        }
        assertThat(scmTypes, hasItem((Class) GitSCM.class));
        assertThat(scmTypes, not(hasItem((Class) OldSCM.class)));
        /* More realistic variant:
        mp.getSourcesList().add(new BranchSource(new SingleSCMSource(null, "test", new NullSCM()), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        JenkinsRule.WebClient wc = r.createWebClient();
        String html = wc.getPage(mp, "configure").getWebResponse().getContentAsString();
        assertThat(html, containsString("GitSCM"));
        assertThat(html, not(containsString("OldSCM")));
        */
    }

    public static class OldSCM extends SCM {
        @Override
        public ChangeLogParser createChangeLogParser() {return null;}
        @TestExtension("applicableSCMs")
        public static class DescriptorImpl extends SCMDescriptor<OldSCM> {
            public DescriptorImpl() {
                super(null);
            }
            @NonNull
            @Override
            public String getDisplayName() {
                return "OldSCM";
            }
        }
    }

    @Issue("JENKINS-32179")
    @Test
    void conflictingBranches() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        r.waitUntilNoActivity();
        assertEquals(1, mp.getItems().size());

        r.waitForCompletion(p.getLastBuild());
        Thread.sleep(1000);
        assertFalse(p.isBuilding());
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        Queue.Item it = mp.scheduleBuild2(0);
        Thread.sleep(100);
        if (it != null) {
            System.out.println("indexing");
        }
        it.getFuture().waitForStart();
        it.getFuture().get();
//        mp.scheduleBuild2(0).getFuture().get();
     //   mp.getIndexing().writeWholeLogTo(System.out);
//        assertEquals("[p, p/master]", ExtensionList.lookup(Listener.class).get(0).names.toString());
    }

    @TestExtension("conflictingBranches")
    public static class Listener extends ItemListener {
        List<String> names = new ArrayList<>();
        @Override
        public void onCreated(Item item) {
            names.add(item.getFullName());
        }
    }


    @Issue("JENKINS-34561")
    @Test
    void configuredScriptNameBranches() throws Exception {
        sampleRepo.init();

        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }
        WorkflowBranchProjectFactory projectFactory = new WorkflowBranchProjectFactory();
        projectFactory.setScriptPath("another-Jenkinsfile");
        mp.setProjectFactory(projectFactory);

        sampleRepo.write("file","initial commit");
        sampleRepo.git("commit", "--all", "--message=init-master");

        mp.scheduleBuild2(0).getFuture().get();
        assertNull(mp.getItem("master"));

        sampleRepo.git("checkout", "-b", "feature");
        sampleRepo.write("another-Jenkinsfile", "echo(/branch=$BRANCH_NAME/); node {checkout scm; echo readFile('file')}");
        sampleRepo.git("add", "another-Jenkinsfile");
        sampleRepo.write("file", "subsequent content");
        sampleRepo.git("commit", "--all", "--message=feature-create");
        WorkflowJob p1 = scheduleAndFindBranchProject(mp, "feature");
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b1 = p1.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("subsequent content", b1);
        r.assertLogContains("branch=feature", b1);

        sampleRepo.git("checkout", "-b", "feature2");
        sampleRepo.write("another-Jenkinsfile", "echo(/branch=$BRANCH_NAME/); node {checkout scm; echo readFile('file').toUpperCase()}");
        sampleRepo.write("file", "alternative content");
        sampleRepo.git("commit", "--all", "--message=feature2-create");
        WorkflowJob p2 = scheduleAndFindBranchProject(mp, "feature2");
        assertEquals(2, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b2 = p2.getLastBuild();
        assertEquals(1, b2.getNumber());
        r.assertLogContains("ALTERNATIVE CONTENT", b2);
        r.assertLogContains("branch=feature2", b2);
    }

    @Issue("JENKINS-72613")
    @Test
    void reloadMangledName() throws Exception {
        r.jenkins.setQuietPeriod(0);
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo 'on master'");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=init");
        for (var branch : List.of("ok-1", "danger_1")) {
            sampleRepo.git("checkout", "-b", branch, "master");
            sampleRepo.write("Jenkinsfile", "echo 'on " + branch + "'");
            sampleRepo.git("add", "Jenkinsfile");
            sampleRepo.git("commit", "--all", "--message=" + branch);
        }
        var mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        var source = new GitSCMSource(sampleRepo.toString());
        source.setTraits(List.of(new BranchDiscoveryTrait()));
        mp.getSourcesList().add(new BranchSource(source));
        var ok = scheduleAndFindBranchProject(mp, "ok-1");
        var danger = findBranchProject(mp, "danger_1");
        r.waitUntilNoActivity();
        assertThat(ok.getRootDir().getName(), is("ok-1"));
        assertThat(danger.getRootDir().getName(), is("danger-1.i2g9ue"));
        ok.doReload();
        assertThat(mp.getItems().stream().map(AbstractItem::getName).toArray(String[]::new),
            arrayContainingInAnyOrder("master", "ok-1", "danger_1"));
        danger.doReload();
        assertThat(mp.getItems().stream().map(AbstractItem::getName).toArray(String[]::new),
            arrayContainingInAnyOrder("master", "ok-1", "danger_1"));
    }

}
