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

import hudson.model.Item;
import hudson.model.User;
import hudson.model.View;
import hudson.security.ACL;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import java.io.File;
import java.util.List;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.OrganizationFolder;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.api.SCMSource;
import org.acegisecurity.Authentication;
import static org.hamcrest.Matchers.*;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class WorkflowMultiBranchProjectFactoryTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo1 = new GitSampleRepoRule();
    @Rule public GitSampleRepoRule sampleRepo2 = new GitSampleRepoRule();
    @Rule public GitSampleRepoRule sampleRepo3 = new GitSampleRepoRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void smokes() throws Exception {
        File clones = tmp.newFolder();
        sampleRepo1.init();
        sampleRepo1.write(WorkflowBranchProjectFactory.SCRIPT, "echo 'ran one'");
        sampleRepo1.git("add", WorkflowBranchProjectFactory.SCRIPT);
        sampleRepo1.git("commit", "--all", "--message=flow");
        sampleRepo1.git("clone", ".", new File(clones, "one").getAbsolutePath());
        sampleRepo3.init(); // but do not write SCRIPT, so should be ignored
        sampleRepo3.git("clone", ".", new File(clones, "three").getAbsolutePath());
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());
        OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
        top.getNavigators().add(new GitDirectorySCMNavigator(clones.getAbsolutePath()));
        // Make sure we created one multibranch projects:
        top.scheduleBuild2(0).getFuture().get();
        top.getComputation().writeWholeLogTo(System.out);
        assertEquals(1, top.getItems().size());
        MultiBranchProject<?,?> one = top.getItem("one");
        assertThat(one, is(instanceOf(WorkflowMultiBranchProject.class)));
        // Check that it has Git configured:
        List<SCMSource> sources = one.getSCMSources();
        assertEquals(1, sources.size());
        assertEquals("GitSCMSource", sources.get(0).getClass().getSimpleName());
        // Verify permissions:
        Authentication admin = User.get("admin").impersonate();
        ACL acl = one.getACL();
        assertTrue(acl.hasPermission(ACL.SYSTEM, Item.CONFIGURE));
        assertTrue(acl.hasPermission(ACL.SYSTEM, Item.DELETE));
        assertFalse(acl.hasPermission(admin, Item.CONFIGURE));
        assertFalse(acl.hasPermission(admin, View.CONFIGURE));
        assertFalse(acl.hasPermission(admin, View.CREATE));
        assertFalse(acl.hasPermission(admin, View.DELETE));
        assertFalse(acl.hasPermission(admin, Item.DELETE));
        assertTrue(acl.hasPermission(admin, Item.EXTENDED_READ));
        assertTrue(acl.hasPermission(admin, Item.READ));
        assertTrue(acl.hasPermission(admin, View.READ));
        // Check that the master branch project works:
        r.waitUntilNoActivity();
        WorkflowJob p = WorkflowMultiBranchProjectTest.findBranchProject((WorkflowMultiBranchProject) one, "master");
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("ran one", b1);
        // Then add a second checkout and reindex:
        sampleRepo2.init();
        sampleRepo2.write(WorkflowBranchProjectFactory.SCRIPT, "echo 'ran two'");
        sampleRepo2.git("add", WorkflowBranchProjectFactory.SCRIPT);
        sampleRepo2.git("commit", "--all", "--message=flow");
        sampleRepo2.git("clone", ".", new File(clones, "two").getAbsolutePath());
        top.scheduleBuild2(0).getFuture().get();
        top.getComputation().writeWholeLogTo(System.out);
        assertEquals(2, top.getItems().size());
        // Same for another one:
        MultiBranchProject<?,?> two = top.getItem("two");
        assertThat(two, is(instanceOf(WorkflowMultiBranchProject.class)));
        r.waitUntilNoActivity();
        p = WorkflowMultiBranchProjectTest.findBranchProject((WorkflowMultiBranchProject) two, "master");
        b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("ran two", b1);
        // JENKINS-34246: also delete Jenkinsfile
        sampleRepo2.git("rm", WorkflowBranchProjectFactory.SCRIPT);
        sampleRepo2.git("commit", "--message=noflow");
        top.scheduleBuild2(0).getFuture().get();
        top.getComputation().writeWholeLogTo(System.out);
        assertEquals(1, top.getItems().size());
    }

    @Issue("JENKINS-34561")
    @Test public void configuredScriptName() throws Exception {
        String alternativeJenkinsFileName = "alternative_Jenkinsfile_name.groovy";

        File clones = tmp.newFolder();
        sampleRepo1.init();
        sampleRepo1.write(WorkflowBranchProjectFactory.SCRIPT,
                "echo 'echo from " + WorkflowBranchProjectFactory.SCRIPT + "'");
        sampleRepo1.git("add", WorkflowBranchProjectFactory.SCRIPT);
        sampleRepo1.git("commit", "--all", "--message=flow");
        String repoWithJenkinsfile = "repo_with_jenkinsfile";
        sampleRepo1.git("clone", ".", new File(clones, repoWithJenkinsfile).getAbsolutePath());

        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy());
        OrganizationFolder top = r.jenkins.createProject(OrganizationFolder.class, "top");
        top.getNavigators().add(new GitDirectorySCMNavigator(clones.getAbsolutePath()));
        top.scheduleBuild2(0).getFuture().get();
        top.getComputation().writeWholeLogTo(System.out);
        assertEquals(1, top.getItems().size());

        // Make sure we created one multibranch project:
        top.scheduleBuild2(0).getFuture().get();
        top.getComputation().writeWholeLogTo(System.out);
        assertEquals(1, top.getItems().size());
        MultiBranchProject<?,?> projectFromJenkinsfile = top.getItem(repoWithJenkinsfile);
        assertThat(projectFromJenkinsfile, is(instanceOf(WorkflowMultiBranchProject.class)));

        // Check that the 'Jenkinsfile' project works:
        r.waitUntilNoActivity();
        WorkflowJob p = WorkflowMultiBranchProjectTest.findBranchProject((WorkflowMultiBranchProject) projectFromJenkinsfile, "master");
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("echo from Jenkinsfile", b1);

        // add second Project Recognizer with alternative Jenkinsfile name to organization folder
        WorkflowMultiBranchProjectFactory workflowMultiBranchProjectFactory = new WorkflowMultiBranchProjectFactory();
        workflowMultiBranchProjectFactory.setScriptPath(alternativeJenkinsFileName);
        top.getProjectFactories().add(workflowMultiBranchProjectFactory);

        // Then add a second checkout and reindex:
        sampleRepo2.init();
        sampleRepo2.write(alternativeJenkinsFileName,
                "echo 'echo from " + alternativeJenkinsFileName + "'");
        sampleRepo2.git("add", alternativeJenkinsFileName);
        sampleRepo2.git("commit", "--all", "--message=flow");
        String repoWithAlternativeJenkinsfile = "repo_with_alternative_jenkinsfile";
        sampleRepo2.git("clone", ".", new File(clones, repoWithAlternativeJenkinsfile).getAbsolutePath());

        // Make sure we created two multibranch projects:
        top.scheduleBuild2(0).getFuture().get();
        top.getComputation().writeWholeLogTo(System.out);
        assertEquals(2, top.getItems().size());

        // Check that the 'alternative_Jenkinsfile_name' project works:
        MultiBranchProject<?,?> projectFromAlternativeJenkinsFile = top.getItem(repoWithAlternativeJenkinsfile);
        assertThat(projectFromAlternativeJenkinsFile, is(instanceOf(WorkflowMultiBranchProject.class)));
        r.waitUntilNoActivity();
        p = WorkflowMultiBranchProjectTest.findBranchProject((WorkflowMultiBranchProject) projectFromAlternativeJenkinsFile, "master");
        b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("echo from alternative_Jenkinsfile_name", b1);
    }
}
