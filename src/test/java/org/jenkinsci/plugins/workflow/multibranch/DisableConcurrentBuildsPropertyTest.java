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

import static org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject;

import hudson.model.Result;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchPropertyStrategy;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.api.SCMHead;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.DisableConcurrentBuildsJobProperty;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class DisableConcurrentBuildsPropertyTest {

  @Rule public JenkinsRule r = new JenkinsRule();
  @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

  @Test
  public void configRoundtrip() throws Exception {
    WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
    BranchSource bs = branchSource();
    mp.getSourcesList().add(bs);
    {
      DisableConcurrentBuildsBranchProperty mbProperty = new DisableConcurrentBuildsBranchProperty();
      bs.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[] {
          mbProperty
      }));
    }
    r.configRoundtrip(mp);

    BranchPropertyStrategy strat = mp.getBranchPropertyStrategy(mp.getSCMSources().get(0));
    Assert.assertNotNull(strat);
    DisableConcurrentBuildsBranchProperty prop = strat.getPropertiesFor(new SCMHead("master")).stream()
        .filter(DisableConcurrentBuildsBranchProperty.class::isInstance)
        .map(DisableConcurrentBuildsBranchProperty.class::cast)
        .findFirst().orElse(null);
    Assert.assertNotNull(prop);
    Assert.assertTrue(prop.isEnabled());
    Assert.assertFalse(prop.isAbortPrevious());
  }

  @Test
  public void propertyByBranchProperty() throws Exception {
    sampleRepo.init();
    sampleRepo.write("Jenkinsfile",
        "echo 'whynot'");
    sampleRepo.git("add", "Jenkinsfile");
    sampleRepo.git("commit", "--all", "--message=flow");

    WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
    BranchSource bs = branchSource();
    mp.getSourcesList().add(bs);
    {
      DisableConcurrentBuildsBranchProperty mbProperty = new DisableConcurrentBuildsBranchProperty();
      mbProperty.setEnabled(true);
      mbProperty.setAbortPrevious(true);
      bs.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[] {
          mbProperty
      }));
    }
    WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
    r.waitUntilNoActivity();

    DisableConcurrentBuildsJobProperty property = p.getProperty(DisableConcurrentBuildsJobProperty.class);
    Assert.assertNotNull(property);
    Assert.assertTrue(property.isAbortPrevious());
    WorkflowRun b1 = p.getLastBuild();
    Assert.assertEquals(Result.SUCCESS, b1.getResult());

    // Ensure when we disable the property, branches see that on the next build
    {
      DisableConcurrentBuildsBranchProperty mbProperty = new DisableConcurrentBuildsBranchProperty();
      mbProperty.setEnabled(false);
      bs.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[] {
          mbProperty
      }));
    }

    p = scheduleAndFindBranchProject(mp, "master");
    r.waitUntilNoActivity();

    property = p.getProperty(DisableConcurrentBuildsJobProperty.class);
    Assert.assertNull(property);
  }

  @SuppressWarnings("deprecation")
  private BranchSource branchSource() {
    return new BranchSource(
        new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false)
    );
  }
}
