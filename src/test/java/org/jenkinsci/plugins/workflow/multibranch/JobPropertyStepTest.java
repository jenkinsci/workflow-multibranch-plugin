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

import hudson.model.BooleanParameterDefinition;
import hudson.model.JobProperty;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.LogRotator;
import java.util.Collections;
import java.util.List;
import jenkins.branch.BranchSource;
import jenkins.model.BuildDiscarder;
import jenkins.model.BuildDiscarderProperty;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

@Issue("JENKINS-30519")
public class JobPropertyStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    private static final boolean HAVE_SYMBOL =
        ParametersDefinitionProperty.DescriptorImpl.class.isAnnotationPresent(Symbol.class) && // "parameters"
        BooleanParameterDefinition.DescriptorImpl.class.isAnnotationPresent(Symbol.class) && // "booleanParam"
        StringParameterDefinition.DescriptorImpl.class.isAnnotationPresent(Symbol.class) && // "string"
        BuildDiscarderProperty.DescriptorImpl.class.isAnnotationPresent(Symbol.class) && // "buildDiscarder"
        LogRotator.LRDescriptor.class.isAnnotationPresent(Symbol.class); // "logRotator"

    @SuppressWarnings("rawtypes")
    @Test public void configRoundTripParameters() throws Exception {
        List<JobProperty> properties = Collections.<JobProperty>singletonList(new ParametersDefinitionProperty(new BooleanParameterDefinition("flag", true, null)));
        /* TODO JENKINS-29711 means it omits the parentheses, without which the call is misparsed:
        if (HAVE_SYMBOL) {
            // TODO *ParameterDefinition.description ought to be defaulted to null:
            new SnippetizerTester(r).assertRoundTrip(new JobPropertyStep(properties), "properties([parameters([booleanParam(defaultValue: true, description: '', name: 'flag')])])");
        } else {
            new SnippetizerTester(r).assertRoundTrip(new JobPropertyStep(properties), "properties([[$class: 'ParametersDefinitionProperty', parameterDefinitions: [[$class: 'BooleanParameterDefinition', defaultValue: true, description: '', name: 'flag']]]])");
        }
        */
        StepConfigTester tester = new StepConfigTester(r);
        properties = tester.configRoundTrip(new JobPropertyStep(properties)).getProperties();
        assertEquals(1, properties.size());
        assertEquals(ParametersDefinitionProperty.class, properties.get(0).getClass());
        ParametersDefinitionProperty pdp = (ParametersDefinitionProperty) properties.get(0);
        assertEquals(1, pdp.getParameterDefinitions().size());
        assertEquals(BooleanParameterDefinition.class, pdp.getParameterDefinitions().get(0).getClass());
        BooleanParameterDefinition bpd = (BooleanParameterDefinition) pdp.getParameterDefinitions().get(0);
        assertEquals("flag", bpd.getName());
        assertTrue(bpd.isDefaultValue());
        assertEquals(Collections.emptyList(), tester.configRoundTrip(new JobPropertyStep(Collections.<JobProperty>emptyList())).getProperties());
    }

    @SuppressWarnings("rawtypes")
    @Test public void configRoundTripBuildDiscarder() throws Exception {
        List<JobProperty> properties = Collections.<JobProperty>singletonList(new BuildDiscarderProperty(new LogRotator(1, 2, -1, 3)));
        /* TODO JENKINS-29711:
        if (HAVE_SYMBOL) {
            // TODO structural form of LogRotator is awful; confusion between integer and string types, and failure to handle default values:
            new SnippetizerTester(r).assertRoundTrip(new JobPropertyStep(properties), "properties([buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '3', daysToKeepStr: '1', numToKeepStr: '2'))])");
        } else {
            new SnippetizerTester(r).assertRoundTrip(new JobPropertyStep(properties), "properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '3', daysToKeepStr: '1', numToKeepStr: '2']]])");
        }
        */
        StepConfigTester tester = new StepConfigTester(r);
        properties = tester.configRoundTrip(new JobPropertyStep(properties)).getProperties();
        assertEquals(1, properties.size());
        assertEquals(BuildDiscarderProperty.class, properties.get(0).getClass());
        BuildDiscarderProperty bdp = (BuildDiscarderProperty) properties.get(0);
        BuildDiscarder strategy = bdp.getStrategy();
        assertNotNull(strategy);
        assertEquals(LogRotator.class, strategy.getClass());
        LogRotator lr = (LogRotator) strategy;
        assertEquals(1, lr.getDaysToKeep());
        assertEquals(2, lr.getNumToKeep());
        assertEquals(-1, lr.getArtifactDaysToKeep());
        assertEquals(3, lr.getArtifactNumToKeep());
    }

    @Test public void useParameter() throws Exception {
        sampleRepo.init();
        ScriptApproval.get().approveSignature("method groovy.lang.Binding hasVariable java.lang.String"); // TODO add to generic whitelist
        sampleRepo.write("Jenkinsfile",
                (HAVE_SYMBOL ?
                    "properties([parameters([string(name: 'myparam', defaultValue: 'default value')])])\n" :
                    "properties([[$class: 'ParametersDefinitionProperty', parameterDefinitions: [[$class: 'StringParameterDefinition', name: 'myparam', defaultValue: 'default value']]]])\n") +
                "echo \"received ${binding.hasVariable('myparam') ? myparam : 'undefined'}\"");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false)));
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        // TODO not all that satisfactory since it means you cannot rely on a default value; would be a little easier given JENKINS-27295
        r.assertLogContains("received undefined", b1);
        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("myparam", "special value"))));
        assertEquals(2, b2.getNumber());
        r.assertLogContains("received special value", b2);
    }

    @SuppressWarnings("deprecation") // RunList.size
    @Test public void useBuildDiscarder() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", HAVE_SYMBOL ?
            "properties([buildDiscarder(logRotator(numToKeepStr: '1'))])" :
            "properties([[$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '1']]])");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false)));
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity(); // #1 built automatically
        assertEquals(1, p.getBuilds().size());
        r.assertBuildStatusSuccess(p.scheduleBuild2(0)); // #2
        assertEquals(1, p.getBuilds().size());
        r.assertBuildStatusSuccess(p.scheduleBuild2(0)); // #3
        assertEquals(1, p.getBuilds().size());
        WorkflowRun b3 = p.getLastBuild();
        assertEquals(3, b3.getNumber());
        assertNull(b3.getPreviousBuild());
    }

    @Issue("JENKINS-34547")
    @Test public void concurrentBuildProperty() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        // Verify the base case behavior.
        p.setDefinition(new CpsFlowDefinition("semaphore 'hang'"));

        assertTrue(p.isConcurrentBuild());

        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("hang/1", b1);
        assertTrue(p.isConcurrentBuild());

        WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("hang/2", b2);

        SemaphoreStep.success("hang/1", null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b1));

        SemaphoreStep.success("hang/2", null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b2));

        // Verify that the property successfully disables concurrent builds.
        p.setDefinition(new CpsFlowDefinition("properties([disableConcurrentBuilds()])\n"
                + "semaphore 'hang'"));

        assertTrue(p.isConcurrentBuild());

        WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("hang/3", b3);
        assertFalse(p.isConcurrentBuild());

        QueueTaskFuture<WorkflowRun> futureB4 = p.scheduleBuild2(0);
        // Sleep 2 seconds to make sure the build gets queued.
        Thread.sleep(2000);
        assertFalse(futureB4.getStartCondition().isDone());

        SemaphoreStep.success("hang/3", null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b3));

        WorkflowRun b4 = futureB4.waitForStart();
        SemaphoreStep.waitForStart("hang/4", b4);
        SemaphoreStep.success("hang/4", null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b4));
    }

}
