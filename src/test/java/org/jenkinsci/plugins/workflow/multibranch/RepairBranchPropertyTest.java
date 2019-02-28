package org.jenkinsci.plugins.workflow.multibranch;

import hudson.model.Cause;
import hudson.model.ItemGroup;
import hudson.model.Job;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.OrganizationFolder;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMNavigator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.io.IOException;

import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RepairBranchPropertyTest {

    @Rule
    public RestartableJenkinsRule j = new RestartableJenkinsRule();
    private MockSCMController controller;

    @Before
    public void setUp() throws IOException {
        controller = MockSCMController.create();
        controller.createRepository("repo");
        controller.createBranch("repo", "master");
        controller.addFile("repo", "master", "First!", WorkflowBranchProjectFactory.SCRIPT,
                "echo 'hello'".getBytes());
    }

    @Test
    public void removedProperty() throws IOException {
        j.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                final OrganizationFolder org = j.j.createProject(OrganizationFolder.class, "org");
                org.getNavigators().add(new MockSCMNavigator(controller.getId(), new MockSCMDiscoverBranches()));
                org.save();
                org.scheduleBuild(new Cause.UserIdCause("anonymous"));
                j.j.waitUntilNoActivity();
                final MultiBranchProject<?, ?> repo = org.getItem("repo");
                assertNotNull(repo);
                final Job<?, ?> master = repo.getItem("master");
                assertNotNull(master);
                assertNotNull(master.getProperty(BranchJobProperty.class));
                assertNotNull(master.getLastBuild());
                master.removeProperty(BranchJobProperty.class);
                //removeProperty calls save
                //assertNull(master.getProperty(BranchJobProperty.class)); //Actually calling save will eventually end up calling isProject anyway, so now it will forever reconstruct itself :/
            }
        });
        j.addStep(new Statement() {  //A bit unnessesary now that we fount out that saving a project will reconstruct the property directly
            @Override
            public void evaluate() throws Throwable {
                final OrganizationFolder org = j.j.jenkins.getItem("org", (ItemGroup) null, OrganizationFolder.class);
                assertNotNull(org);
                final MultiBranchProject<?, ?> repo = org.getItem("repo");
                assertNotNull(repo);
                final Job<?, ?> master = repo.getItem("master");
                assertNotNull(master);
                assertNotNull(master.getProperty(BranchJobProperty.class));
                assertTrue(repo.getProjectFactory().isProject(master));
            }
        });
    }
}
