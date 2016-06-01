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

import hudson.BulkChange;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.Run;
import hudson.tasks.LogRotator;
import jenkins.model.BuildDiscarderProperty;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.inject.Inject;


/**
 * Convenience step for {@link BuildDiscarderProperty}.
 */
public class KeepBuildsStep extends AbstractStepImpl {

    /**
     * If not -1 or null, history is only kept up to this days.
     */
    public Integer daysToKeep;

    /**
     * If not -1 or null, only this number of build logs are kept.
     */
    public Integer numToKeep;

    /**
     * If not -1 or null, artifacts are only kept up to this days.
     */
    public Integer artifactDaysToKeep;

    /**
     * If not -1 or null, only this number of builds have their artifacts kept.
     */
    public Integer artifactNumToKeep;

    @DataBoundConstructor public KeepBuildsStep() {

    }

    // Setters are here so that Snippet Generator works properly.
    @DataBoundSetter public void setDaysToKeep(Integer daysToKeep) {
        this.daysToKeep = daysToKeep;
    }

    @DataBoundSetter public void setNumToKeep(Integer numToKeep) {
        this.numToKeep = numToKeep;
    }

    @DataBoundSetter public void setArtifactDaysToKeep(Integer artifactDaysToKeep) {
        this.artifactDaysToKeep = artifactDaysToKeep;
    }

    @DataBoundSetter public void setArtifactNumToKeep(Integer artifactNumToKeep) {
        this.artifactNumToKeep = artifactNumToKeep;
    }

    // Getters are here because NPEs were hit when just using the fields.
    public Integer getDaysToKeepValue() {
        return daysToKeep != null ? daysToKeep : -1;
    }

    public Integer getNumToKeepValue() {
        return numToKeep != null ? numToKeep : -1;
    }

    public Integer getArtifactDaysToKeepValue() {
        return artifactDaysToKeep != null ? artifactDaysToKeep : -1;
    }

    public Integer getArtifactNumToKeepValue() {
        return artifactNumToKeep != null ? artifactNumToKeep : -1;
    }

    public static class Execution extends AbstractSynchronousStepExecution<Void> {

        @Inject transient KeepBuildsStep step;
        @StepContextParameter transient Run<?,?> build;

        @SuppressWarnings("unchecked") // untypable
        @Override protected Void run() throws Exception {
            Job<?,?> job = build.getParent();

            BulkChange bc = new BulkChange(job);
            try {
                for (JobProperty prop : job.getAllProperties()) {
                    if (prop instanceof BuildDiscarderProperty) {
                        job.removeProperty(prop);
                    }
                }
                job.addProperty(new BuildDiscarderProperty(new LogRotator(step.getDaysToKeepValue(),
                        step.getNumToKeepValue(),
                        step.getArtifactDaysToKeepValue(),
                        step.getArtifactNumToKeepValue())));
                bc.commit();
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
            return "keepBuilds";
        }

        @Override public String getDisplayName() {
            return "Set policy for keeping old builds";
        }
    }
}
