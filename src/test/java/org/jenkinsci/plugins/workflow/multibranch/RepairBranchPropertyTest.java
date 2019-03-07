/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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
import hudson.model.Actionable;
import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.TopLevelItem;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.OrganizationFolder;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMNavigator;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

public class RepairBranchPropertyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    private MockSCMController controller;

    @Before
    public void setUp() throws IOException {
        setup(MockSCMController.create());
    }

    void setup(MockSCMController co) throws IOException {
        controller = co;
        controller.createRepository("repo");
        controller.createBranch("repo", "master");
        controller.addFile("repo", "master", "First!", WorkflowBranchProjectFactory.SCRIPT,
                "echo 'hello'".getBytes());
    }

    @Test @Issue("JENKINS-55116")
    public void removedProperty() throws Exception {
        OrganizationFolder org = j.createProject(OrganizationFolder.class, "org");
        org.getNavigators().add(new MockSCMNavigator(controller.getId(), new MockSCMDiscoverBranches()));
        org.save();
        org.scheduleBuild(new Cause.UserIdCause("anonymous"));
        j.waitUntilNoActivity();
        MultiBranchProject<?, ?> repo = org.getItem("repo");
        assertNotNull(repo);
        Job<?, ?> master = repo.getItem("master");
        assertNotNull(master);
        assertNotNull(master.getProperty(BranchJobProperty.class));
        assertNotNull(master.getLastBuild());
        master.removeProperty(BranchJobProperty.class);
        //removeProperty calls save
        j.jenkins.reload();

        org = j.jenkins.getItem("org", j.jenkins, OrganizationFolder.class);
        assertNotNull(org);
        repo = org.getItem("repo");
        assertNotNull(repo);
        master = repo.getItem("master");
        assertNotNull(master);

        assertNotNull(master.getProperty(BranchJobProperty.class));
        assertTrue(repo.getProjectFactory().isProject(master));
        assertTrue(repo.getPrimaryView().contains((TopLevelItem)master));
    }

    @Test @Issue("JENKINS-55116")
    public void removedPropertyLastBuildCorrupt() throws Exception {
        OrganizationFolder org = j.createProject(OrganizationFolder.class, "org");
        org.getNavigators().add(new MockSCMNavigator(controller.getId(), new MockSCMDiscoverBranches()));
        org.save();
        org.scheduleBuild(new Cause.UserIdCause("anonymous"));
        j.waitUntilNoActivity();
        MultiBranchProject<?, ?> repo = org.getItem("repo");
        assertNotNull(repo);
        Job<?, ?> master = repo.getItem("master");
        assertNotNull(master);
        assertNotNull(master.getProperty(BranchJobProperty.class));
        assertNotNull(master.getLastBuild());

        controller.addFile("repo", "master", "Second", "README.txt", "Hello".getBytes());
        repo.scheduleBuild();
        j.waitUntilNoActivity();
        assertEquals(2, master.getBuilds().size());
        final Actionable lastBuild = master.getLastBuild();
        lastBuild.removeAction(lastBuild.getAction(SCMRevisionAction.class));
        assertNull(lastBuild.getAction(SCMRevisionAction.class));

        master.removeProperty(BranchJobProperty.class);
        //removeProperty calls save
        j.jenkins.reload();
        org = j.jenkins.getItem("org", j.jenkins, OrganizationFolder.class);
        assertNotNull(org);
        repo = org.getItem("repo");
        assertNotNull(repo);
        master = repo.getItem("master");
        assertNotNull(master);

        assertNotNull(master.getProperty(BranchJobProperty.class));
        assertTrue(repo.getProjectFactory().isProject(master));
        assertTrue(repo.getPrimaryView().contains((TopLevelItem)master));
    }

    @Test @LocalData @Issue("JENKINS-55116")
    public void removedPropertyAtStartup() throws Exception {
        MockSCMController cont = MockSCMController.recreate("9ea2ef21-aa07-4973-a942-6c4c4c7851d1");
        setup(cont);
        OrganizationFolder org = j.jenkins.getItem("org", j.jenkins, OrganizationFolder.class);
        assertNotNull(org);
        MultiBranchProject repo = org.getItem("repo");
        assertNotNull(repo);
        WorkflowJob master = (WorkflowJob)repo.getItem("master");
        assertNotNull(master);
        assertNotNull(master.getProperty(BranchJobProperty.class));
        assertTrue(((WorkflowMultiBranchProject)master.getParent()).getProjectFactory().isProject(master));
        assertTrue(repo.getPrimaryView().contains(master));

        //Can it be scanned successfully afterwards
        final Queue.Item item = repo.scheduleBuild2(0);
        assertNotNull(item);
        final Queue.Executable executable = item.getFuture().get();
        assertNotNull(executable);
        FolderComputation computation = (FolderComputation) executable;
        computation.writeWholeLogTo(System.out);
        assertEquals(Result.SUCCESS, computation.getResult());
        //Since the new controller has new "commits" a build of master should have been scheduled
        j.waitUntilNoActivity();
        assertEquals(2, master.getBuilds().size());
        j.assertBuildStatusSuccess(master.getBuildByNumber(2));
    }
}
