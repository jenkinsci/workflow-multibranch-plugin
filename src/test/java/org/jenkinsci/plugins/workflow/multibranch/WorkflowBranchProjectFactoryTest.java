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
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class WorkflowBranchProjectFactoryTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Issue("JENKINS-30744")
    @Test public void slashyBranches() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
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
                WorkflowMultiBranchProject mp = story.j.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
                mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false)));
                WorkflowJob p = scheduleAndFindBranchProject(mp, "dev%2Fmain");
                assertEquals(1, mp.getItems().size());
                story.j.waitUntilNoActivity();
                WorkflowRun b1 = p.getLastBuild();
                assertEquals(1, b1.getNumber());
                story.j.assertLogContains("branch=dev/main", b1);
                story.j.assertLogContains("workspace=dev_main-ZFNHWJSHKH4HUVOQUPOQV6WFX7XUPIKIAQAQ3DV7CCAGIXQW7YSA", b1);
                verifyProject(p);
                sampleRepo.write("Jenkinsfile", script.replace("branch=", "Branch="));
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p/dev%2Fmain", WorkflowJob.class);
                assertNotNull(p);
                sampleRepo.git("commit", "--all", "--message=Flow");
                sampleRepo.notifyCommit(story.j);
                WorkflowRun b2 = p.getLastBuild();
                assertEquals(2, b2.getNumber());
                story.j.assertLogContains("Branch=dev/main", b2);
                story.j.assertLogContains("workspace=dev_main-ZFNHWJSHKH4HUVOQUPOQV6WFX7XUPIKIAQAQ3DV7CCAGIXQW7YSA", b2);
                verifyProject(p);
            }
        });
    }
    private void verifyProject(WorkflowJob p) throws Exception {
        assertEquals("dev%2Fmain", p.getName());
        assertEquals("dev/main", p.getDisplayName());
        assertEquals("p/dev%2Fmain", p.getFullName());
        assertEquals("p » dev/main", p.getFullDisplayName());
        story.j.createWebClient().getPage(p);
        assertEquals(new File(new File(p.getParent().getRootDir(), "branches"), "dev-main.k31kdj"), p.getRootDir());
    }

}
