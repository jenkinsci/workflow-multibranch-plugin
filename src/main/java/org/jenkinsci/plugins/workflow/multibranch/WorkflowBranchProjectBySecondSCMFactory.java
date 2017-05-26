/*
 * The MIT License
 *
 * Copyright 2017 IBM Corporation
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

import hudson.Extension;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;

import java.io.IOException;
import java.util.Collection;

import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;

import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Builds all branches unless otherwise filtered.
 */
public class WorkflowBranchProjectBySecondSCMFactory extends AbstractWorkflowBranchProjectFactory {
    public final SCM scm;
    public final String scriptPath;

    @DataBoundConstructor public WorkflowBranchProjectBySecondSCMFactory(SCM scm, String scriptPath) {
        this.scm = scm;
        this.scriptPath = scriptPath;
    }

    @Override protected FlowDefinition createDefinition() {
        return new CpsScmFlowDefinition(scm, scriptPath);
    }

    @Override protected SCMSourceCriteria getSCMSourceCriteria(SCMSource source) {
        return new SCMSourceCriteria() {
            private static final long serialVersionUID = 1000125077208604613L;

            @Override public boolean isHead(SCMSourceCriteria.Probe probe, TaskListener listener) throws IOException {
                return true;
            }

            @Override
            public int hashCode() {
                return getClass().hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                return getClass().isInstance(obj);
            }
        };
    }

    @Extension public static class DescriptorImpl extends AbstractWorkflowBranchProjectFactoryDescriptor {

        @Override public String getDisplayName() {
            return "Pipeline Script From External SCM";
        }

        public Collection<? extends SCMDescriptor<?>> getApplicableDescriptors() {
            StaplerRequest req = Stapler.getCurrentRequest();
            Job job = req != null ? req.findAncestorObject(Job.class) : null;
            return job != null ? SCM._for(job) : /* TODO 1.599+ does this for job == null */ SCM.all();
        }
    }

}
