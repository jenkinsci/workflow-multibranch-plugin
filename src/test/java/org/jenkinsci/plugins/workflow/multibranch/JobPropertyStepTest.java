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
import hudson.model.BooleanParameterValue;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.JobProperty;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.LogRotator;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.branch.OverrideIndexTriggersJobProperty;
import jenkins.model.BuildDiscarder;
import jenkins.model.BuildDiscarderProperty;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitBranchSCMHead;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.triggers.ReverseBuildTrigger;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.ArrayType;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;
import org.jenkinsci.plugins.structs.describable.ErrorType;
import org.jenkinsci.plugins.structs.describable.HeterogeneousObjectType;
import org.jenkinsci.plugins.structs.describable.HomogeneousObjectType;
import org.jenkinsci.plugins.structs.describable.ParameterType;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.DisableConcurrentBuildsJobProperty;
import org.jenkinsci.plugins.workflow.job.properties.MockTrigger;
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.NoStaplerConstructorException;

import static org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject;

@Issue("JENKINS-30519")
public class JobPropertyStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    /**
     * Needed to ensure that we get a fresh {@code MockTrigger#startsAndStops} with each test run. Has to be *after* rather than
     * *before* to avoid weird ordering issues with {@code @LocalData}.
     */
    @After
    public void resetStartsAndStops() {
        MockTrigger.startsAndStops = new ArrayList<>();
    }

    @SuppressWarnings("rawtypes")
    @Test public void configRoundTripParameters() throws Exception {
        List<JobProperty> properties = Collections.singletonList(new ParametersDefinitionProperty(new BooleanParameterDefinition("flag", true, null)));
        // TODO *ParameterDefinition.description ought to be defaulted to null:
        new SnippetizerTester(r).assertRoundTrip(new JobPropertyStep(properties), "properties([parameters([booleanParam(defaultValue: true, name: 'flag')])])");

        StepConfigTester tester = new StepConfigTester(r);
        properties = tester.configRoundTrip(new JobPropertyStep(properties)).getProperties();
        assertEquals(1, properties.size());
        ParametersDefinitionProperty pdp = getPropertyFromList(ParametersDefinitionProperty.class, properties);
        assertNotNull(pdp);
        assertEquals(1, pdp.getParameterDefinitions().size());
        assertEquals(BooleanParameterDefinition.class, pdp.getParameterDefinitions().get(0).getClass());
        BooleanParameterDefinition bpd = (BooleanParameterDefinition) pdp.getParameterDefinitions().get(0);
        assertEquals("flag", bpd.getName());
        assertTrue(bpd.isDefaultValue());

        List<JobProperty> emptyInput = tester.configRoundTrip(new JobPropertyStep(Collections.emptyList())).getProperties();

        assertEquals(Collections.emptyList(), removeTriggerProperty(emptyInput));
    }

    @Issue("JENKINS-51290")
    @Test
    public void testPreviousBuildFailedHard() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");

        // First we simulate a build that has resulted in a null execution
        // This can be a result of a hard-kill of the Pipeline or a catastrophic failure starting the run, i.e. fetching the Jenkinsfile
        p.setDefinition(new CpsFlowDefinition("echo 'Not doing anything'", true));
        WorkflowRun run = r.buildAndAssertSuccess(p);
        Field f = run.getClass().getDeclaredField("execution");
        f.setAccessible(true);
        f.set(run, null);
        Assert.assertNull(run.getExecution());

        // Verify build runs cleanly
        p.setDefinition(new CpsFlowDefinition("properties([buildDiscarder(logRotator(numToKeepStr: '1'))])", true));
        r.buildAndAssertSuccess(p);
    }

    @SuppressWarnings("rawtypes")
    @Test public void configRoundTripBuildDiscarder() throws Exception {
        List<JobProperty> properties = Collections.singletonList(new BuildDiscarderProperty(new LogRotator(1, 2, -1, 3)));

        // TODO structural form of LogRotator is awful; confusion between integer and string types, and failure to handle default values:
        new SnippetizerTester(r).assertRoundTrip(new JobPropertyStep(properties), "properties([buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '3', daysToKeepStr: '1', numToKeepStr: '2'))])");

        StepConfigTester tester = new StepConfigTester(r);
        properties = tester.configRoundTrip(new JobPropertyStep(properties)).getProperties();
        assertFalse(properties.isEmpty());
        BuildDiscarderProperty bdp = getPropertyFromList(BuildDiscarderProperty.class, properties);
        assertNotNull(bdp);
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
        sampleRepo.write("Jenkinsfile",
                "properties([parameters([string(name: 'myparam', defaultValue: 'default value')])])\n" +
                "echo \"received ${params.myparam}\"");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false)));
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("received default value", b1);
        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("myparam", "special value"))));
        assertEquals(2, b2.getNumber());
        r.assertLogContains("received special value", b2);

        sampleRepo.write("Jenkinsfile",
                "properties([parameters([booleanParam(name: 'flag', defaultValue: false)])])\n" +
                "echo \"enabled? ${params.flag}\"");
        sampleRepo.git("commit", "--all", "--message=flow");
        sampleRepo.notifyCommit(r);
        WorkflowRun b3 = p.getLastBuild();
        assertEquals(3, b3.getNumber());
        r.assertLogContains("enabled? false", b3);
        WorkflowRun b4 = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new BooleanParameterValue("flag", true))));
        assertEquals(4, b4.getNumber());
        r.assertLogContains("enabled? true", b4);

        sampleRepo.write("Jenkinsfile",
                "properties([parameters([booleanParam(name: 'newflag', defaultValue: false)])])\n" +
                "echo \"enabled again? ${params.newflag}\"");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowRun b5 = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new BooleanParameterValue("newflag", true))));
        assertEquals(5, b5.getNumber());
        r.assertLogContains("enabled again? true", b5);

        sampleRepo.write("Jenkinsfile",
                "properties([parameters([choice(name: 'select', choices: 'foo\\nbar\\nbaz')])])\n" +
                "echo \"value ${params.select}\"");
        sampleRepo.git("commit", "--all", "--message=flow");
        sampleRepo.notifyCommit(r);
        WorkflowRun b6 = p.getLastBuild();
        assertEquals(6, b6.getNumber());
        r.assertLogContains("value foo", b6);
        WorkflowRun b7 = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("select", "bar", ""))));
        assertEquals(7, b7.getNumber());
        r.assertLogContains("value bar", b7);

        sampleRepo.write("Jenkinsfile",
                "properties([parameters([choice(name: 'select', choices: ['foo', 'bar', 'baz'] )])])\n" +
                        "echo \"value ${params.select}\"");
        sampleRepo.git("commit", "--all", "--message=flow");
        sampleRepo.notifyCommit(r);
        WorkflowRun b8 = p.getLastBuild();
        assertEquals(8, b8.getNumber());
        r.assertLogContains("value foo", b8);
        WorkflowRun b9 = r.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("select", "bar", ""))));
        assertEquals(9, b9.getNumber());
        r.assertLogContains("value bar", b9);
    }

    @Issue("JENKINS-26143")
    @Test
    public void testChoiceParameterSnippetizer() throws Exception {
        //new SnippetizerTester(r).assertGenerateSnippet();
        new SnippetizerTester(r).assertRoundTrip(new JobPropertyStep(Collections.singletonList(new ParametersDefinitionProperty(new ChoiceParameterDefinition("paramName", new String[]{"foo", "bar", "baz"}, "test")))),
                "properties([parameters([choice(choices: ['foo', 'bar', 'baz'], description: 'test', name: 'paramName')])])");
    }

    @SuppressWarnings("deprecation") // RunList.size
    @Test public void useBuildDiscarder() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "properties([buildDiscarder(logRotator(numToKeepStr: '1'))])");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false)));
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity(); // #1 built automatically
        assertEquals(1, p.getBuilds().size());
        r.assertBuildStatusSuccess(p.scheduleBuild2(0)); // #2
        Thread.sleep(500L); // WorkflowRun performs log rotation asynchronously.
        assertEquals(1, p.getBuilds().size());
        r.assertBuildStatusSuccess(p.scheduleBuild2(0)); // #3
        Thread.sleep(500L); // WorkflowRun performs log rotation asynchronously.
        assertEquals(1, p.getBuilds().size());
        WorkflowRun b3 = p.getLastBuild();
        assertEquals(3, b3.getNumber());
        assertNull(b3.getPreviousBuild());
    }

    @Issue("JENKINS-44848")
    @Test public void onlyRemoveJenkinsfileProperties() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.addProperty(new DisableConcurrentBuildsJobProperty());

        // Base case - not calling the properties step
        p.setDefinition(new CpsFlowDefinition("echo 'Not doing anything'", true));

        r.buildAndAssertSuccess(p);

        assertNotNull(p.getProperty(DisableConcurrentBuildsJobProperty.class));
        assertNull(p.getAction(JobPropertyTrackerAction.class));

        // Adding a property, make sure the predefined one is still there.
        // TODO Jenkins 2.x use symbols
        p.setDefinition(new CpsFlowDefinition("properties([buildDiscarder(logRotator(numToKeepStr: '1'))])", true));

        r.buildAndAssertSuccess(p);

        assertNotNull(p.getProperty(DisableConcurrentBuildsJobProperty.class));
        assertNotNull(p.getProperty(BuildDiscarderProperty.class));
        JobPropertyTrackerAction action2 = p.getAction(JobPropertyTrackerAction.class);
        assertNotNull(action2);
        assertEquals(1, action2.getJobPropertyDescriptors().size());
        assertEquals(Collections.singleton(r.jenkins.getDescriptor(BuildDiscarderProperty.class).getId()), action2.getJobPropertyDescriptors());

        // Make sure the predefined property is still there after we remove the properties-step-defined property.
        p.setDefinition(new CpsFlowDefinition("properties([])", true));

        r.buildAndAssertSuccess(p);

        assertNotNull(p.getProperty(DisableConcurrentBuildsJobProperty.class));
        assertNull(p.getProperty(BuildDiscarderProperty.class));

        JobPropertyTrackerAction action3 = p.getAction(JobPropertyTrackerAction.class);
        assertNotNull(action3);
        assertTrue(action3.getJobPropertyDescriptors().isEmpty());
    }

    @Issue("JENKINS-44848")
    @LocalData
    @Test
    public void trackerPropertyUpgrade() throws Exception {
        WorkflowJob p = r.jenkins.getItemByFullName("trackerPropertyUpgrade", WorkflowJob.class);
        assertNotNull(p);
        WorkflowRun b1 = p.getLastBuild();
        assertNotNull(b1);
        assertNotNull(p.getProperty(BuildDiscarderProperty.class));
        assertNull(p.getAction(JobPropertyTrackerAction.class));

        p.setDefinition(new CpsFlowDefinition("properties([disableConcurrentBuilds()])", true));

        r.buildAndAssertSuccess(p);

        assertNull(p.getProperty(BuildDiscarderProperty.class));
        assertNotNull(p.getProperty(DisableConcurrentBuildsJobProperty.class));
        JobPropertyTrackerAction action2 = p.getAction(JobPropertyTrackerAction.class);
        assertNotNull(action2);
        assertEquals(Collections.singleton(r.jenkins.getDescriptor(DisableConcurrentBuildsJobProperty.class).getId()), action2.getJobPropertyDescriptors());
    }

    @Issue("JENKINS-34547")
    @Test public void concurrentBuildProperty() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        // Verify the base case behavior.
        p.setDefinition(new CpsFlowDefinition("semaphore 'hang'", true));

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
                + "semaphore 'hang'", true));

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

    @Issue("JENKINS-34005")
    @Test public void triggersProperty() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        // Verify the base case behavior.
        p.setDefinition(new CpsFlowDefinition("echo 'foo'", true));

        assertTrue(p.getTriggers().isEmpty());

        r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        // Make sure the triggers are still empty.
        assertTrue(p.getTriggers().isEmpty());

        // Now add a trigger.
        p.setDefinition(new CpsFlowDefinition(
                "properties([pipelineTriggers([\n"
              + "  cron('@daily'), [$class: 'MockTrigger']])])\n" + "echo 'foo'",
                true));

        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertEquals(2, p.getTriggers().size());

        PipelineTriggersJobProperty triggerProp = p.getTriggersJobProperty();

        TimerTrigger timerTrigger = getTriggerFromList(TimerTrigger.class, triggerProp.getTriggers());

        assertNotNull(timerTrigger);

        assertEquals("@daily", timerTrigger.getSpec());

        MockTrigger mockTrigger = getTriggerFromList(MockTrigger.class, triggerProp.getTriggers());

        assertNotNull(mockTrigger);

        assertTrue(mockTrigger.isStarted);

        assertEquals("[null, false]", MockTrigger.startsAndStops.toString());

        // Now run a properties step with a different property and verify that we still have a
        // PipelineTriggersJobProperty, but with no triggers in it.
        p.setDefinition(new CpsFlowDefinition("properties([disableConcurrentBuilds()])\n"
                + "echo 'foo'", true));

        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertNotNull(p.getTriggersJobProperty());

        assertTrue(p.getTriggers().isEmpty());

        assertEquals("[null, false, null]", MockTrigger.startsAndStops.toString());
    }

    @Issue("JENKINS-37731")
    @Test public void scmTriggerProperty() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        // Verify the base case behavior.
        p.setDefinition(new CpsFlowDefinition("echo 'foo'", true));

        assertTrue(p.getTriggers().isEmpty());

        r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        // Make sure the triggers are still empty.
        assertTrue(p.getTriggers().isEmpty());

        // Now add a trigger.
        // Looking for core versions 2.21 and later for the proper pollScm symbol, rather than the broken scm symbol.
        if (SymbolLookup.getSymbolValue(SCMTrigger.class).contains("pollSCM")) {
            p.setDefinition(new CpsFlowDefinition(
                    "properties([pipelineTriggers([\n"
                            + "  pollSCM(scmpoll_spec: '@daily', ignorePostCommitHooks: true), [$class: 'MockTrigger']])])\n"
                            + "echo 'foo'", true));
        } else {
            p.setDefinition(new CpsFlowDefinition(
                    "properties([pipelineTriggers([pollSCM(scmpoll_spec: '@daily', ignorePostCommitHooks: true),\n"
                            + "    [$class: 'MockTrigger']])])\n"
                            + "echo 'foo'", true));
        }

        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertEquals(2, p.getTriggers().size());

        PipelineTriggersJobProperty triggerProp = p.getTriggersJobProperty();

        SCMTrigger scmTrigger = getTriggerFromList(SCMTrigger.class, triggerProp.getTriggers());

        assertNotNull(scmTrigger);

        assertEquals("@daily", scmTrigger.getSpec());
        assertTrue(scmTrigger.isIgnorePostCommitHooks());

        MockTrigger mockTrigger = getTriggerFromList(MockTrigger.class, triggerProp.getTriggers());

        assertNotNull(mockTrigger);

        assertTrue(mockTrigger.isStarted);

        assertEquals("[null, false]", MockTrigger.startsAndStops.toString());

        // Now run a properties step with a different property and verify that we still have a
        // PipelineTriggersJobProperty, but with no triggers in it.
        p.setDefinition(new CpsFlowDefinition("properties([disableConcurrentBuilds()])\n"
                + "echo 'foo'", true));

        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertNotNull(p.getTriggersJobProperty());

        assertTrue(p.getTriggers().isEmpty());

        assertEquals("[null, false, null]", MockTrigger.startsAndStops.toString());
    }

    @Test public void scmAndEmptyTriggersProperty() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        // Verify the base case behavior.
        p.setDefinition(new CpsFlowDefinition("echo 'foo'", true));

        assertTrue(p.getTriggers().isEmpty());

        r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        // Make sure the triggers are still empty.
        assertTrue(p.getTriggers().isEmpty());

        // Now add a trigger. Deliberately keeping the old syntax to make sure it still works.
        p.setDefinition(new CpsFlowDefinition(
                "properties([pipelineTriggers([pollSCM(scmpoll_spec: '@daily')])])\n"
                        + "echo 'foo'", true));

        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertEquals(1, p.getTriggers().size());

        PipelineTriggersJobProperty triggerProp = p.getTriggersJobProperty();

        SCMTrigger scmTrigger = getTriggerFromList(SCMTrigger.class, triggerProp.getTriggers());

        assertNotNull(scmTrigger);

        assertEquals("@daily", scmTrigger.getSpec());

        // Now run a properties step with an empty triggers property and verify that we still have a
        // PipelineTriggersJobProperty, but with no triggers in it.
        p.setDefinition(new CpsFlowDefinition("properties([pipelineTriggers()])\n"
                + "echo 'foo'", true));

        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertNotNull(p.getTriggersJobProperty());

        assertTrue(p.getTriggers().isEmpty());
    }

    @Issue("JENKINS-37477")
    @Test
    public void generateHelpTrigger() throws Exception {
        DescribableModel<?> model = new DescribableModel<>(PipelineTriggersJobProperty.class);

        assertNotNull(model);

        recurseOnModel(model);
    }

    /**
     * TODO: Move to workflow-cps test classes somewhere.
     */
    private void recurseOnTypes(ParameterType type) throws Exception {
        // For the moment, only care about types with @DataBoundConstructors.
        if (type instanceof ErrorType && !(((ErrorType)type).getError() instanceof NoStaplerConstructorException)) {
            throw ((ErrorType)type).getError();
        }

        if (type instanceof ArrayType) {
            recurseOnTypes(((ArrayType)type).getElementType());
        } else if (type instanceof HomogeneousObjectType) {
            recurseOnModel(((HomogeneousObjectType) type).getSchemaType());
        } else if (type instanceof HeterogeneousObjectType) {
            for (Map.Entry<String, DescribableModel<?>> entry : ((HeterogeneousObjectType) type).getTypes().entrySet()) {
                recurseOnModel(entry.getValue());
            }
        }
    }

    /**
     * TODO: Move to workflow-cps test classes somewhere.
     */
    private void recurseOnModel(DescribableModel<?> model) throws Exception {
        for (DescribableParameter param : model.getParameters()) {
            recurseOnTypes(param.getType());
        }
    }


    @Issue("JENKINS-37477")
    @Test
    public void configRoundTripTrigger() throws Exception {
        List<JobProperty> properties = Collections.singletonList(new PipelineTriggersJobProperty(Collections.singletonList(new TimerTrigger("@daily"))));
        String snippetJson = "{'propertiesMap': {\n" +
                "    'stapler-class-bag': 'true',\n" +
                "    'org-jenkinsci-plugins-workflow-job-properties-PipelineTriggersJobProperty': {'triggers': {\n" +
                "      'stapler-class-bag': 'true',\n" +
                "      'hudson-triggers-TimerTrigger': {'spec': '@daily'}\n" +
                "    }}},\n" +
                "  'stapler-class': 'org.jenkinsci.plugins.workflow.multibranch.JobPropertyStep',\n" +
                "  '$class': 'org.jenkinsci.plugins.workflow.multibranch.JobPropertyStep'}";

        new SnippetizerTester(r).assertGenerateSnippet(snippetJson, "properties([pipelineTriggers([cron('@daily')])])", null);
        new SnippetizerTester(r).assertRoundTrip(new JobPropertyStep(properties), "properties([pipelineTriggers([cron('@daily')])])");
    }

    @Issue("JENKINS-37721")
    @Test
    public void configRoundTripSCMTrigger() throws Exception {
        List<JobProperty> properties = Collections.singletonList(new PipelineTriggersJobProperty(Collections.singletonList(new SCMTrigger("@daily"))));
        String snippetJson = "{'propertiesMap': {\n" +
                "    'stapler-class-bag': 'true',\n" +
                "    'org-jenkinsci-plugins-workflow-job-properties-PipelineTriggersJobProperty': {'triggers': {\n" +
                "      'stapler-class-bag': 'true',\n" +
                "      'hudson-triggers-SCMTrigger': {'scmpoll_spec': '@daily', 'ignorePostCommitHooks': false }\n" +
                "    }}},\n" +
                "  'stapler-class': 'org.jenkinsci.plugins.workflow.multibranch.JobPropertyStep',\n" +
                "  '$class': 'org.jenkinsci.plugins.workflow.multibranch.JobPropertyStep'}";

        new SnippetizerTester(r).assertGenerateSnippet(snippetJson, "properties([pipelineTriggers([pollSCM('@daily')])])", null);
        new SnippetizerTester(r).assertRoundTrip(new JobPropertyStep(properties), "properties([pipelineTriggers([pollSCM('@daily')])])");
    }

    @Issue("JENKINS-34464")
    @Test
    public void configRoundTripReverseBuildTrigger() throws Exception {
        List<JobProperty> properties = Collections.singletonList(new PipelineTriggersJobProperty(Collections.singletonList(new ReverseBuildTrigger("some-job", Result.UNSTABLE))));
        String snippetJson = "{'propertiesMap': {\n" +
                "    'stapler-class-bag': 'true',\n" +
                "    'org-jenkinsci-plugins-workflow-job-properties-PipelineTriggersJobProperty': {'triggers': {\n" +
                "      'stapler-class-bag': 'true',\n" +
                "      'jenkins-triggers-ReverseBuildTrigger': { 'threshold': 'UNSTABLE', 'upstreamProjects': 'some-job'}\n" +
                "    }}},\n" +
                "  'stapler-class': 'org.jenkinsci.plugins.workflow.multibranch.JobPropertyStep',\n" +
                "  '$class': 'org.jenkinsci.plugins.workflow.multibranch.JobPropertyStep'}";

        new SnippetizerTester(r).assertGenerateSnippet(snippetJson, "properties([pipelineTriggers([upstream(threshold: 'UNSTABLE', upstreamProjects: 'some-job')])])", null);
        new SnippetizerTester(r).assertRoundTrip(new JobPropertyStep(properties), "properties([pipelineTriggers([upstream(threshold: 'UNSTABLE', upstreamProjects: 'some-job')])])");
    }

    @Issue("JENKINS-37005")
    @Test 
    public void noPropertiesWarnings() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"\n"
                + "properties([disableConcurrentBuilds()])");
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

        // Now verify that we don't get any messages about removing properties when a property actually gets removed as
        // we add a new one.
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"\n"
                + "properties([buildDiscarder(logRotator(numToKeepStr: '1'))])");
                sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");

        r.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    @Issue("JENKINS-37219")
    @Test public void disableTriggers() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "properties([overrideIndexTriggers(false)])");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--message=init");

        WorkflowMultiBranchProject p = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        BranchSource branchSource = new BranchSource(new GitSCMSource("source-id", sampleRepo.toString(), "", "*", "", false));
        p.getSourcesList().add(branchSource);

        // Should be initial build of master, which sets the job property.
        WorkflowJob master = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(p, "master");
        r.waitUntilNoActivity();
        r.assertBuildStatusSuccess(master.getBuildByNumber(1));
        assertEquals(2, master.getNextBuildNumber());

        assertNotNull(master.getProperty(OverrideIndexTriggersJobProperty.class));
        assertFalse(master.getProperty(OverrideIndexTriggersJobProperty.class).getEnableTriggers());

        sampleRepo.write("Jenkinsfile", "properties([])");
        sampleRepo.git("commit", "--all", "--message=master-2");
        sampleRepo.notifyCommit(r);

        WorkflowMultiBranchProjectTest.showIndexing(p);
        // Should not be a new build.
        assertEquals(2, master.getNextBuildNumber());

        // Should be able to manually build master, which should result in the blocking going away.
        WorkflowRun secondBuild = r.assertBuildStatusSuccess(master.scheduleBuild2(0));
        assertNotNull(secondBuild);
        assertEquals(2, secondBuild.getNumber());
        assertEquals(3, master.getNextBuildNumber());
        assertNull(master.getProperty(OverrideIndexTriggersJobProperty.class));


        // Now let's see it actually trigger another build from a new commit.
        sampleRepo.write("Jenkinsfile", "// yet more");
        sampleRepo.git("commit", "--all", "--message=master-3");
        sampleRepo.notifyCommit(r);
        WorkflowMultiBranchProjectTest.showIndexing(p);
        assertEquals(4, master.getNextBuildNumber());
    }

    @Issue("JENKINS-37219")
    @Test
    public void snippetGeneratorOverrideIndexing() throws Exception {
        String snippetJson = "{'propertiesMap':\n" +
                "{'stapler-class-bag': 'true', 'jenkins-branch-OverrideIndexTriggersJobProperty': \n" +
                "{'specified': true, 'enableTriggers': true}},\n" +
                "'stapler-class': 'org.jenkinsci.plugins.workflow.multibranch.JobPropertyStep',\n" +
                "'$class': 'org.jenkinsci.plugins.workflow.multibranch.JobPropertyStep'}";

        new SnippetizerTester(r).assertGenerateSnippet(snippetJson, "properties([overrideIndexTriggers(true)])", null);
    }

    private <T extends Trigger<?>> T getTriggerFromList(Class<T> clazz, List<Trigger<?>> triggers) {
        for (Trigger<?> t : triggers) {
            if (clazz.isInstance(t)) {
                return clazz.cast(t);
            }
        }

        return null;
    }

    private <T extends JobProperty> T getPropertyFromList(Class<T> clazz, List<JobProperty> properties) {
        for (JobProperty p : properties) {
            if (clazz.isInstance(p)) {
                return clazz.cast(p);
            }
        }

        return null;
    }

    private List<JobProperty> removeTriggerProperty(List<JobProperty> originalProps) {
        List<JobProperty> returnList = new ArrayList<>();
        for (JobProperty p : originalProps) {
            if (!(p instanceof PipelineTriggersJobProperty)) {
                returnList.add(p);
            }
        }

        return returnList;
    }
}
