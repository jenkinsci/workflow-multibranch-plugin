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

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.GitStep;
import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.LibraryRetriever;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

@WithGitSampleRepo
class SCMVarTest {

    @SuppressWarnings("unused")
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();
    @RegisterExtension
    private final JenkinsSessionExtension story = new JenkinsSessionExtension();
    private GitSampleRepoRule sampleRepo;

    @BeforeEach
    void setUp(GitSampleRepoRule repo) {
        sampleRepo = repo;
    }

    @Test
    void scmPickle() throws Throwable {
        story.then(j -> {
                sampleRepo.init();
                sampleRepo.write("Jenkinsfile", "def _scm = scm; semaphore 'wait'; node {checkout _scm; echo readFile('file')}");
                sampleRepo.write("file", "initial content");
                sampleRepo.git("add", "Jenkinsfile");
                sampleRepo.git("commit", "--all", "--message=flow");
                WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
                mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
                WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "master");
                SemaphoreStep.waitForStart("wait/1", null);
                WorkflowRun b1 = p.getLastBuild();
                assertNotNull(b1);
        });
        story.then(j -> {
                SemaphoreStep.success("wait/1", null);
                WorkflowJob p = j.jenkins.getItemByFullName("p/master", WorkflowJob.class);
                assertNotNull(p);
                WorkflowRun b1 = p.getLastBuild();
                assertNotNull(b1);
                assertEquals(1, b1.getNumber());
                j.assertLogContains("initial content", j.waitForCompletion(b1));
                SCMBinderTest.assertRevisionAction(b1);
        });
    }

    @Issue("JENKINS-30222")
    @Test
    void globalVariable() throws Throwable {
        story.then(j -> {
                // Set up a standardJob definition:
                File lib = new File(Jenkins.get().getRootDir(), "somelib");
                LibraryConfiguration cfg = new LibraryConfiguration("somelib", new LocalRetriever(lib));
                cfg.setImplicit(true);
                cfg.setDefaultVersion("fixed");
                GlobalLibraries.get().setLibraries(List.of(cfg));
                File vars = new File(lib, "vars");
                Files.createDirectories(vars.toPath());
                FileUtils.writeStringToFile(new File(vars, "standardJob.groovy"),
                        """
                                def call(body) {
                                  def config = [:]
                                  body.resolveStrategy = Closure.DELEGATE_FIRST
                                  body.delegate = config
                                  body()
                                  node {
                                    checkout scm
                                    echo "loaded ${readFile config.file}"
                                  }
                                }
                                """, StandardCharsets.UTF_8);
                // Then a project using it:
                sampleRepo.init();
                sampleRepo.write("Jenkinsfile", "standardJob {file = 'resource'}");
                sampleRepo.write("resource", "resource content");
                sampleRepo.git("add", "Jenkinsfile");
                sampleRepo.git("add", "resource");
                sampleRepo.git("commit", "--all", "--message=flow");
                // And run:
                WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
                mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
                WorkflowJob p = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "master");
                WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                j.assertLogContains("loaded resource content", b);
        });
    }

    // TODO copied from GrapeTest along with body of libroot(); could make sense as a *-tests.jar utility
    private static final class LocalRetriever extends LibraryRetriever {
        private final File lib;
        LocalRetriever(File lib) {
            this.lib = lib;
        }
        @Override
        public void retrieve(String name, String version, boolean changelog, FilePath target, Run<?, ?> run, TaskListener listener) throws Exception {
            new FilePath(lib).copyRecursiveTo(target);
        }
        @Override
        public void retrieve(String name, String version, FilePath target, Run<?, ?> run, TaskListener listener) throws Exception {
            retrieve(name, version, false, target, run, listener);
        }
    }

    @Issue("JENKINS-31386")
    @Test
    void standaloneProject() throws Throwable {
        story.then(j -> {
                sampleRepo.init();
                sampleRepo.write("Jenkinsfile", "node {checkout scm; echo readFile('file')}");
                sampleRepo.write("file", "some content");
                sampleRepo.git("add", "Jenkinsfile");
                sampleRepo.git("commit", "--all", "--message=flow");
                WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsScmFlowDefinition(new GitStep(sampleRepo.toString()).createSCM(), "Jenkinsfile"));
                WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                j.assertLogContains("some content", b);
        });
    }

}
