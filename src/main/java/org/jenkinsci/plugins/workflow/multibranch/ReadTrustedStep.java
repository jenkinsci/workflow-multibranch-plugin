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

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.slaves.WorkspaceList;
import java.io.IOException;
import javax.inject.Inject;
import jenkins.branch.Branch;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.steps.LoadStepExecution;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.scm.GenericSCMStep;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Replacement for {@code readFile} which reads from the SCM using {@link SCMSource#getTrustedRevision}.
 * Refuses to load a file which has been modified in an untrusted revision.
 * If run multiple times, always loads from the same revision.
 * May be used in combination with {@code evaluate} to delegate to more Pipeline Groovy, as a substitute for {@link SCMBinder},
 * at least until {@link LoadStepExecution} has been split into an abstract part that a {@code loadTrusted} step could extend.
 */
public class ReadTrustedStep extends AbstractStepImpl {

    private final String path;
    // TODO encoding

    @DataBoundConstructor public ReadTrustedStep(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public static class Execution extends AbstractSynchronousStepExecution<String> {

        @Inject private transient ReadTrustedStep step;
        @StepContextParameter private transient Run<?,?> build;
        @StepContextParameter private transient TaskListener listener;

        @Override protected String run() throws Exception {
            Job<?, ?> job = build.getParent();
            // Adapted from CpsScmFlowDefinition:
            Node node = Jenkins.getActiveInstance();
            FilePath dir;
            if (job instanceof TopLevelItem) {
                FilePath baseWorkspace = node.getWorkspaceFor((TopLevelItem) job);
                if (baseWorkspace == null) {
                    throw new AbortException(node.getDisplayName() + " may be offline");
                }
                dir = getFilePathWithSuffix(baseWorkspace);
            } else { // should not happen, but just in case:
                throw new IllegalStateException(job + " was not top level");
            }
            FilePath file = dir.child(step.path);
            if (!file.absolutize().getRemote().replace('\\', '/').startsWith(dir.absolutize().getRemote().replace('\\', '/') + '/')) { // TODO JENKINS-26838
                throw new IOException(file + " is not inside " + dir);
            }
            Computer computer = node.toComputer();
            if (computer == null) {
                throw new IOException(node.getDisplayName() + " may be offline");
            }
            WorkspaceList.Lease lease = computer.getWorkspaceList().acquire(dir);
            try {
                // Adapted from SCMBinder:
                BranchJobProperty property = job.getProperty(BranchJobProperty.class);
                if (property == null) {
                    // As in SCMVar:
                    if (job instanceof WorkflowJob) {
                        FlowDefinition defn = ((WorkflowJob) job).getDefinition();
                        if (defn instanceof CpsScmFlowDefinition) {
                            // JENKINS-31386: retrofit to work with standalone projects, without doing any trust checks.
                            SCMStep delegate = new GenericSCMStep(((CpsScmFlowDefinition) defn).getScm());
                            delegate.setPoll(true);
                            delegate.setChangelog(true);
                            delegate.checkout(build, dir, listener, node.createLauncher(listener));
                            if (!file.exists()) {
                                throw new AbortException(file + " not found");
                            }
                            return file.readToString();
                        }
                    }
                    throw new AbortException("‘readTrusted’ is only available when using “" +
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
                SCMHead head = branch.getHead();
                SCMRevision tip;
                SCMRevisionAction action = build.getAction(SCMRevisionAction.class);
                if (action != null) {
                    tip = action.getRevision();
                } else {
                    tip = scmSource.fetch(head, listener);
                    if (tip == null) {
                        throw new AbortException("Could not determine exact tip revision of " + branch.getName());
                    }
                    build.addAction(new SCMRevisionAction(tip));
                }
                SCMRevision trusted = scmSource.getTrustedRevision(tip, listener);
                String untrustedFile = null;
                if (!tip.equals(trusted)) {
                    SCMStep delegate = new GenericSCMStep(scmSource.build(head, tip));
                    delegate.setPoll(false);
                    delegate.setChangelog(false);
                    delegate.checkout(build, dir, listener, node.createLauncher(listener));
                    if (!file.exists()) {
                        throw new AbortException(file + " not found");
                    }
                    untrustedFile = file.readToString();
                }
                SCMStep delegate = new GenericSCMStep(scmSource.build(head, trusted));
                delegate.setPoll(true);
                delegate.setChangelog(true);
                delegate.checkout(build, dir, listener, node.createLauncher(listener));
                if (!file.exists()) {
                    throw new AbortException(file + " not found");
                }
                String content = file.readToString();
                if (untrustedFile != null && !untrustedFile.equals(content)) {
                    throw new AbortException(Messages.ReadTrustedStep__has_been_modified_in_an_untrusted_revis(step.path));
                }
                return content;
            } finally {
                lease.release();
            }
        }

        private FilePath getFilePathWithSuffix(FilePath baseWorkspace) {
            return baseWorkspace.withSuffix(getFilePathSuffix() + "script");
        }

        private String getFilePathSuffix() {
            return System.getProperty(WorkspaceList.class.getName(), "@");
        }

        private static final long serialVersionUID = 1L;

    }

    @Extension public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "readTrusted";
        }

        @Override public String getDisplayName() {
            return "Read trusted file from SCM";
        }

    }

}
