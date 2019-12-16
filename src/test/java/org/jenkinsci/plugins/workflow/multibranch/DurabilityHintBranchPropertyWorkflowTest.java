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

import hudson.model.Result;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.branch.NoTriggerBranchProperty;
import jenkins.branch.NoTriggerOrganizationFolderProperty;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.flow.DurabilityHintProvider;
import org.jenkinsci.plugins.workflow.flow.FlowDurabilityHint;
import org.jenkinsci.plugins.workflow.flow.GlobalDefaultFlowDurabilityLevel;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.DurabilityHintJobProperty;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Integration test for {@link NoTriggerBranchProperty} and {@link NoTriggerOrganizationFolderProperty}.
 */
@Issue("JENKINS-32396")
public class DurabilityHintBranchPropertyWorkflowTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Test
    public void configRoundtrip() throws Exception {
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        BranchSource bs = new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false));
        mp.getSourcesList().add(bs);
        bs.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{new DurabilityHintBranchProperty(FlowDurabilityHint.SURVIVABLE_NONATOMIC)}));
        r.configRoundtrip(mp);
        DefaultBranchPropertyStrategy strat = (DefaultBranchPropertyStrategy) mp.getBranchPropertyStrategy(mp.getSCMSources().get(0));
        DurabilityHintBranchProperty prop = null;
        for (BranchProperty bp : strat.getProps()) {
            if (bp instanceof  DurabilityHintBranchProperty) {
                prop = (DurabilityHintBranchProperty)bp;
                break;
            }
        }
        Assert.assertNotNull(prop);
        Assert.assertEquals(FlowDurabilityHint.SURVIVABLE_NONATOMIC, prop.getHint());
    }

    @Test public void durabilityHintByPropertyStep() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile",
                        "properties([durabilityHint('" + FlowDurabilityHint.SURVIVABLE_NONATOMIC.getName()+"')])\n"+
                        "echo 'whynot'");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");


        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false)));
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        r.waitUntilNoActivity();

        WorkflowRun b1 = p.getLastBuild();
        Assert.assertEquals(Result.SUCCESS, b1.getResult());
        DurabilityHintJobProperty prop = p.getProperty(DurabilityHintJobProperty.class);
        Assert.assertEquals(FlowDurabilityHint.SURVIVABLE_NONATOMIC, prop.getHint());
    }

    @Test
    @Issue("JENKINS-48826")
    public void durabilityHintByBranchProperty() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile",
                        "echo 'whynot'");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");


        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        BranchSource bs = new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false));
        mp.getSourcesList().add(bs);
        bs.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{new DurabilityHintBranchProperty(FlowDurabilityHint.SURVIVABLE_NONATOMIC)}));
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        r.waitUntilNoActivity();

        Assert.assertEquals(FlowDurabilityHint.SURVIVABLE_NONATOMIC, DurabilityHintProvider.suggestedFor(p));
        WorkflowRun b1 = p.getLastBuild();
        Assert.assertEquals(Result.SUCCESS, b1.getResult());

        // Ensure when we remove the property, branches see that on the next build
        bs.setStrategy(new DefaultBranchPropertyStrategy(new BranchProperty[]{}));
        p = scheduleAndFindBranchProject(mp, "master");
        r.waitUntilNoActivity();

        Assert.assertEquals(GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint(), DurabilityHintProvider.suggestedFor(mp.getItems().iterator().next()));
    }
}
