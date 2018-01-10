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

import com.thoughtworks.xstream.converters.Converter;
import hudson.AbortException;
import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.util.DescribableList;
import java.io.Serializable;
import jenkins.branch.Branch;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.pickles.Pickle;
import org.jenkinsci.plugins.workflow.support.pickles.SingleTypedPickleFactory;
import org.jenkinsci.plugins.workflow.support.pickles.XStreamPickle;

/**
 * Adds an {@code scm} global variable to the script.
 * This makes it possible to run {@code checkout scm} to get your project sources in the right branch.
 */
@Extension public class SCMVar extends GlobalVariable {

    @Override public String getName() {
        return "scm";
    }

    @Override public SCM getValue(CpsScript script) throws Exception {
        Run<?,?> build = script.$build();
        // TODO some code overlap with SCMBinder.create, but not obvious how to factor out common parts
        if (!(build instanceof WorkflowRun)) {
            throw new AbortException("‘checkout scm’ is not available outside a Pipeline build");
        }
        Job<?,?> job = build.getParent();
        BranchJobProperty property = job.getProperty(BranchJobProperty.class);
        if (property == null) {
            if (job instanceof WorkflowJob) {
                FlowDefinition defn = ((WorkflowJob) job).getDefinition();
                if (defn instanceof CpsScmFlowDefinition) {
                    // JENKINS-31386: retrofit to work with standalone projects, minus the exact revision support.
                    return ((CpsScmFlowDefinition) defn).getScm();
                }
            }
            throw new AbortException("‘checkout scm’ is only available when using “" +
                Jenkins.getActiveInstance().getDescriptorByType(WorkflowMultiBranchProject.DescriptorImpl.class).getDisplayName() +
                "” or “" + Jenkins.getActiveInstance().getDescriptorByType(CpsScmFlowDefinition.DescriptorImpl.class).getDisplayName() + "”");
        }
        Branch branch = property.getBranch();
        ItemGroup<?> parent = job.getParent();
        if (!(parent instanceof WorkflowMultiBranchProject)) {
            throw new IllegalStateException("inappropriate context");
        }
        SCMSource scmSource = ((WorkflowMultiBranchProject) parent).getSCMSource(branch.getSourceId());
        if (scmSource == null) {
            throw new IllegalStateException(branch.getSourceId() + " not found");
        }
        SCMRevision tip;
        SCMRevisionAction revisionAction = build.getAction(SCMRevisionAction.class);
        if (revisionAction != null) {
            tip = revisionAction.getRevision();
        } else {
            SCMHead head = branch.getHead();
            FlowExecutionOwner owner = ((WorkflowRun) build).asFlowExecutionOwner();
            TaskListener listener = owner != null ? owner.getListener() : TaskListener.NULL;
            tip = scmSource.fetch(head, listener);
            if (tip == null) {
                throw new AbortException("Could not determine exact tip revision of " + branch.getName());
            }
            revisionAction = new SCMRevisionAction(scmSource, tip);
            build.addAction(revisionAction);
        }
        return scmSource.build(branch.getHead(), tip);
    }

    /**
     * Ensures that {@code scm} is saved in its XML representation.
     * Necessary for {@code GitSCM} which is marked {@link Serializable}
     * yet includes a {@link DescribableList} which relies on a custom {@link Converter}.
     * Note that a script which merely calls {@code checkout scm}, even after a restart, does not rely on this;
     * but one which saves {@code scm} somewhere and uses it later would.
     */
    @Extension public static class Pickler extends SingleTypedPickleFactory<SCM> {

        @Override protected Pickle pickle(SCM scm) {
            return new XStreamPickle(scm);
        }

    }

}
