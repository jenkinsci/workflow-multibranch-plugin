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

import hudson.AbortException;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.ItemGroup;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import java.io.IOException;
import java.util.List;
import jenkins.branch.Branch;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinitionDescriptor;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * Checks out the desired version of the script referred to by scriptPath.
 */
class SCMBinder extends FlowDefinition {

    /** Kill switch for JENKINS-33273 in case of problems. */
    static /* not final */ boolean USE_HEAVYWEIGHT_CHECKOUT = Boolean.getBoolean(SCMBinder.class.getName() + ".USE_HEAVYWEIGHT_CHECKOUT"); // TODO 2.4+ use SystemProperties

    /** Kill switch for making this as strict as {@link ReadTrustedStep} about untrusted modifications. */
    static /* not final */ boolean IGNORE_UNTRUSTED_EDITS = Boolean.getBoolean(SCMBinder.class.getName() + ".IGNORE_UNTRUSTED_EDITS"); // TODO 2.4+ use SystemProperties

    private String scriptPath = WorkflowBranchProjectFactory.SCRIPT;

    public Object readResolve() {
        if (this.scriptPath == null) {
            this.scriptPath = WorkflowBranchProjectFactory.SCRIPT;
        }
        return this;
    }

    public SCMBinder(String scriptPath) {
        this.scriptPath = scriptPath;
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
            build.addAction(new SCMRevisionAction(tip));
            SCMRevision rev = scmSource.getTrustedRevision(tip, listener);
            try (SCMFileSystem fs = USE_HEAVYWEIGHT_CHECKOUT ? null : SCMFileSystem.of(scmSource, head, rev)) {
                if (fs != null) { // JENKINS-33273
                    String script = null;
                    try {
                        script = fs.child(scriptPath).contentAsString();
                        listener.getLogger().println("Obtained " + scriptPath + " from " + rev);
                    } catch (IOException | InterruptedException x) {
                        listener.error("Could not do lightweight checkout, falling back to heavyweight").println(Functions.printThrowable(x).trim());
                    }
                    if (script != null) {
                        if (!IGNORE_UNTRUSTED_EDITS && !rev.equals(tip)) {
                            // Make a best effort to abort builds where an untrusted contributor has tried to edit Jenkinsfile.
                            // If we fail to check this (e.g., due to heavyweight checkout), a warning will be printed to the log
                            // and the build will continue with the trusted variant, which is safe but confusing.
                            SCMFileSystem tipFS = SCMFileSystem.of(scmSource, head, tip);
                            if (tipFS != null) {
                                String tipScript = null;
                                try {
                                    tipScript = tipFS.child(scriptPath).contentAsString();
                                } catch (IOException | InterruptedException x) {
                                    listener.error("Could not compare lightweight checkout of trusted revision").println(Functions.printThrowable(x).trim());
                                }
                                if (tipScript != null && !script.equals(tipScript)) {
                                    throw new AbortException(Messages.ReadTrustedStep__has_been_modified_in_an_untrusted_revis(scriptPath));
                                }
                            }
                        }
                        return new CpsFlowDefinition(script, true).create(handle, listener, actions);
                    }
                }
            }
            scm = scmSource.build(head, rev);
        } else {
            listener.error("Could not determine exact tip revision of " + branch.getName() + "; falling back to nondeterministic checkout");
            // Build might fail later anyway, but reason should become clear: for example, branch was deleted before indexing could run.
            scm = branch.getScm();
        }
        return new CpsScmFlowDefinition(scm, scriptPath).create(handle, listener, actions);
    }

    @Extension public static class DescriptorImpl extends FlowDefinitionDescriptor {

        @Override public String getDisplayName() {
            return "Pipeline from multibranch configuration";
        }

    }

    /** Want to display this in the r/o configuration for a branch project, but not offer it on standalone jobs or in any other context. */
    @Extension public static class HideMeElsewhere extends DescriptorVisibilityFilter {

        @SuppressWarnings("rawtypes")
        @Override public boolean filter(Object context, Descriptor descriptor) {
            if (descriptor instanceof DescriptorImpl) {
                return context instanceof WorkflowJob && ((WorkflowJob) context).getParent() instanceof WorkflowMultiBranchProject;
            }
            return true;
        }

    }

}
