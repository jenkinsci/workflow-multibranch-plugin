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

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.ItemGroup;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.scm.SCM;
import hudson.slaves.WorkspaceList;

import java.io.IOException;
import java.util.List;
import jenkins.branch.Branch;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinitionDescriptor;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.scm.GenericSCMStep;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;

/**
 * Checks out the desired version of {@link WorkflowBranchProjectFactory#SCRIPT}.
 */
class ScriptBinder extends FlowDefinition {
    protected String script;
    protected boolean sandbox;

    /**
     * Bind a pipeline script to the project
     * @param script the script contents
     * @param sandbox true if the script should be sandboxed
     */
    public ScriptBinder(String script, boolean sandbox) {
        this.script = script;
        this.sandbox = sandbox;
    }

    @Override public FlowExecution create(FlowExecutionOwner handle, TaskListener listener, List<? extends Action> actions) throws Exception {
        Queue.Executable exec = handle.getExecutable();
        if (!(exec instanceof WorkflowRun)) {
            throw new IllegalStateException("inappropriate context");
        }
        WorkflowRun build = (WorkflowRun) exec;
        WorkflowJob job = build.getParent();
        BranchJobProperty property = job.getProperty(BranchJobProperty.class);
        if (property == null) {
            throw new IllegalStateException("inappropriate context");
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
        SCMHead head = branch.getHead();
        SCMRevision tip = scmSource.fetch(head, listener);
        SCM scm;
        if (tip != null) {
            scm = scmSource.build(head, scmSource.getTrustedRevision(tip, listener));
            build.addAction(new SCMRevisionAction(tip));
        } else {
            listener.error("Could not determine exact tip revision of " + branch.getName() + "; falling back to nondeterministic checkout");
            // Build might fail later anyway, but reason should become clear: for example, branch was deleted before indexing could run.
            scm = branch.getScm();
        }

        Node node = Jenkins.getActiveInstance();
        Computer computer = node.toComputer();
        if (computer == null) {
            throw new IOException(node.getDisplayName() + " may be offline");
        }

        FilePath baseWorkspace = node.getWorkspaceFor((TopLevelItem) build.getParent());
        if (baseWorkspace == null) {
            throw new IOException(node.getDisplayName() + " may be offline");
        }
        SCMStep delegate = new GenericSCMStep(scm);
        delegate.setPoll(true);
        delegate.setChangelog(true);
        WorkspaceList.Lease lease = computer.getWorkspaceList().acquire(baseWorkspace);
        try {
            delegate.checkout(build, baseWorkspace, listener, node.createLauncher(listener));
        } finally {
            lease.release();
        }

        return new CpsFlowDefinition(script, sandbox).create(handle, listener, actions);
    }

    @Extension public static class DescriptorImpl extends FlowDefinitionDescriptor {

        @Override public String getDisplayName() {
            return "External script";
        }

    }

    /** Want to display this in the r/o configuration for a branch project, but not offer it on standalone jobs or in any other context. */
    @Extension public static class HideMeElsewhere extends DescriptorVisibilityFilter {

        @Override public boolean filter(Object context, Descriptor descriptor) {
            if (descriptor instanceof DescriptorImpl) {
                return context instanceof WorkflowJob && ((WorkflowJob) context).getParent() instanceof WorkflowMultiBranchProject;
            }
            return true;
        }

    }
}