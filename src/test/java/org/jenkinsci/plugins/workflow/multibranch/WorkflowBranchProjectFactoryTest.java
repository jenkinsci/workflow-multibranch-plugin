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

import java.io.File;
import java.util.Collections;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsSessionRule;

public class WorkflowBranchProjectFactoryTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsSessionRule story = new JenkinsSessionRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Issue("JENKINS-30744")
    @Test public void slashyBranches() throws Throwable {
        story.then(j -> {
                sampleRepo.init();
                sampleRepo.git("checkout", "-b", "dev/main");
                String script =
                    "echo \"branch=${env.BRANCH_NAME}\"\n" +
                    "node {\n" +
                    "  checkout scm\n" +
                    "  echo \"workspace=${pwd().replaceFirst('.+dev', 'dev')}\"\n" +
                    "}";
                sampleRepo.write("Jenkinsfile", script);
                sampleRepo.git("add", "Jenkinsfile");
                sampleRepo.git("commit", "--all", "--message=flow");
                WorkflowMultiBranchProject mp = j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
                GitSCMSource source = new GitSCMSource(sampleRepo.toString());
                source.setTraits(Collections.singletonList(new BranchDiscoveryTrait()));
                mp.getSourcesList().add(new BranchSource(source));
                WorkflowJob p = scheduleAndFindBranchProject(mp, "dev%2Fmain");
                assertEquals(1, mp.getItems().size());
                j.waitUntilNoActivity();
                WorkflowRun b1 = p.getLastBuild();
                assertEquals(1, b1.getNumber());
                j.assertLogContains("branch=dev/main", b1);
                j.assertLogContains("workspace=dev_main", b1);
                verifyProject(j, p);
                sampleRepo.write("Jenkinsfile", script.replace("branch=", "Branch="));
        });
        story.then(j -> {
                WorkflowJob p = j.jenkins.getItemByFullName("p/dev%2Fmain", WorkflowJob.class);
                assertNotNull(p);
                sampleRepo.git("commit", "--all", "--message=Flow");
                sampleRepo.notifyCommit(j);
                WorkflowRun b2 = p.getLastBuild();
                assertEquals(2, b2.getNumber());
                j.assertLogContains("Branch=dev/main", b2);
                j.assertLogContains("workspace=dev_main", b2);
                verifyProject(j, p);
        });
    }
    private static void verifyProject(JenkinsRule j, WorkflowJob p) throws Exception {
        assertEquals("dev%2Fmain", p.getName());
        assertEquals("dev/main", p.getDisplayName());
        assertEquals("p/dev%2Fmain", p.getFullName());
        assertEquals("p » dev/main", p.getFullDisplayName());
        j.createWebClient().getPage(p);
        assertEquals(new File(new File(p.getParent().getRootDir(), "branches"), "dev-main.k31kdj"), p.getRootDir());
    }

}
