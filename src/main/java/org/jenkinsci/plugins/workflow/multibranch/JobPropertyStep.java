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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Run;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

import hudson.model.TaskListener;
import jenkins.branch.BuildRetentionBranchProperty;
import jenkins.branch.RateLimitBranchProperty;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.NodeStepTypePredicate;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Resets the properties of the current job.
 */
@SuppressWarnings({"unchecked", "rawtypes"}) // TODO JENKINS-26535: cannot bind List<JobProperty<?>>
public class JobPropertyStep extends AbstractStepImpl {

    private final List<JobProperty> properties;

    @DataBoundConstructor public JobPropertyStep(List<JobProperty> properties) {
        this.properties = properties;
    }

    public List<JobProperty> getProperties() {
        return properties;
    }

    private static final Logger LOGGER = Logger.getLogger(JobPropertyStep.class.getName());

    public Map<JobPropertyDescriptor,JobProperty> getPropertiesMap() {
        return Descriptor.toMap((List) properties);
    }

    public static class Execution extends AbstractSynchronousStepExecution<Void> {

        @Inject transient JobPropertyStep step;
        @StepContextParameter transient Run<?,?> build;
        @StepContextParameter transient TaskListener l;

        @SuppressWarnings("unchecked") // untypable
        @Override protected Void run() throws Exception {
            Job<?,?> job = build.getParent();

            JobPropertyTrackerAction previousAction = job.getAction(JobPropertyTrackerAction.class);
            boolean previousHadStep = false;
            if (previousAction == null) {
                Run<?,?> previousRun = build.getPreviousCompletedBuild();
                if (previousRun instanceof FlowExecutionOwner.Executable) {
                    // If the job doesn't have the tracker action but does have a previous completed build,, check to
                    // see if it ran the properties step. This is to deal with first run after this change is added.
                    FlowExecutionOwner owner = ((FlowExecutionOwner.Executable) previousRun).asFlowExecutionOwner();

                    if (owner != null) {
                        try {
                            FlowExecution execution = owner.get();
                            if (execution != null) {
                                previousHadStep = new DepthFirstScanner().findFirstMatch(execution,
                                        new NodeStepTypePredicate(step.getDescriptor())) != null;
                            }
                        } catch (IOException ex) {
                            // May happen legitimately due to owner.get() throwing IOException when previous execution was nulled
                            LOGGER.log(Level.FINE, "Could not search for JobPropertyStep execution: previous run either had null execution due to legitimate error and shows as not-yet-started, or threw other IOException", ex);
                        }
                    }
                }
            }

            for (JobProperty prop : step.properties) {
                if (!prop.getDescriptor().isApplicable(job.getClass())) {
                    throw new AbortException("cannot apply " + prop.getDescriptor().getId() + " to a " + job.getClass().getSimpleName());
                }
            }
            BulkChange bc = new BulkChange(job);
            try {
                for (JobProperty prop : job.getAllProperties()) {
                    if (prop instanceof BranchJobProperty) {
                        // To be safe and avoid breaking everything if there's a corner case, we're explicitly ignoring
                        // BranchJobProperty to make sure it gets preserved.
                        continue;
                    }
                    // If we have a record of JobPropertys defined via the properties step in the previous run, only
                    // remove those properties.
                    if (previousAction != null) {
                        if (previousAction.getJobPropertyDescriptors().contains(prop.getDescriptor().getId())) {
                            job.removeProperty(prop);
                        }
                    } else if (previousHadStep) {
                        // If the previous run did not have the tracker action but *did* run the properties step, use
                        // legacy behavior and remove everything.
                        job.removeProperty(prop);
                    }
                }
                for (JobProperty prop : step.properties) {
                    job.addProperty(prop);
                }
                bc.commit();
                job.replaceAction(new JobPropertyTrackerAction(step.properties));
            } finally {
                bc.abort();
            }
            return null;
        }

        private static final long serialVersionUID = 1L;

    }

    @Extension public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "properties";
        }

        @NonNull
        @Override public String getDisplayName() {
            return "Set job properties";
        }

        @Override public Step newInstance(@NonNull StaplerRequest req, @NonNull JSONObject formData) throws FormException {
            if (req == null) { // should not happen
                return super.newInstance(null, formData);
            }
            // A modified version of RequestImpl.TypePair.convertJSON.
            // Works around the fact that Stapler does not call back into Descriptor.newInstance for nested objects (JENKINS-31458);
            // and propertiesMap virtual field name; and null values for unselected properties.
            List<JobProperty> properties = new ArrayList<>();
            ClassLoader cl = req.getStapler().getWebApp().getClassLoader();
            @SuppressWarnings("unchecked") Set<Map.Entry<String,Object>> entrySet = formData.getJSONObject("propertiesMap").entrySet();
            for (Map.Entry<String,Object> e : entrySet) {
                if (e.getValue() instanceof JSONObject) {
                    String className = e.getKey().replace('-', '.'); // decode JSON-safe class name escaping
                    Class<? extends JobProperty> itemType;
                    try {
                        itemType = cl.loadClass(className).asSubclass(JobProperty.class);
                    } catch (ClassNotFoundException x) {
                        throw new FormException(x, "propertiesMap");
                    }
                    JobPropertyDescriptor d = (JobPropertyDescriptor) Jenkins.get().getDescriptorOrDie(itemType);
                    JSONObject more = (JSONObject) e.getValue();
                    JobProperty property = d.newInstance(req, more);
                    if (property != null) {
                        properties.add(property);
                    }
                }
            }
            return new JobPropertyStep(properties);
        }

        @Restricted(DoNotUse.class) // f:repeatableHeteroProperty
        public Collection<? extends Descriptor<?>> getPropertyDescriptors() {
            List<Descriptor<?>> result = new ArrayList<>();
            for (JobPropertyDescriptor p : ExtensionList.lookup(JobPropertyDescriptor.class)) {
                if (p.isApplicable(WorkflowJob.class)) {
                    result.add(p);
                }
            }
            return result;
        }

    }

    @Extension public static class HideSuperfluousBranchProperties extends DescriptorVisibilityFilter {

        @Override public boolean filter(Object context, @NonNull Descriptor descriptor) {
            if (context instanceof WorkflowMultiBranchProject && (descriptor.clazz == RateLimitBranchProperty.class || descriptor.clazz == BuildRetentionBranchProperty.class)) {
                // These are both adequately handled by declarative job properties.
                return false;
            }
            return true;
        }

    }
}
