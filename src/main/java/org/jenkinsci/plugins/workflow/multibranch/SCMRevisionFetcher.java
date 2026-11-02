/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
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
import hudson.Functions;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.io.IOException;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

/**
 * Associates a {@link SCMRevisionAction} with a new branch build.
 */
@Extension public final class SCMRevisionFetcher extends RunListener<WorkflowRun> {

    @Override public void onStarted(WorkflowRun build, TaskListener listener) {
        var job = build.getParent();
        var property = job.getProperty(BranchJobProperty.class);
        if (property == null) {
            // not a branch project, ignore
            return;
        }
        var branch = property.getBranch();
        var parent = job.getParent();
        if (!(parent instanceof WorkflowMultiBranchProject wmbp)) {
            listener.getLogger().println("Expected to be part of a multibranch folder but was not");
            return;
        }
        var scmSource = wmbp.getSCMSource(branch.getSourceId());
        if (scmSource == null) {
            listener.getLogger().println(branch.getSourceId() + " not found");
            return;
        }
        var head = branch.getHead();
        SCMRevision tip;
        try {
            tip = scmSource.fetch(head, listener);
        } catch (IOException | InterruptedException x) {
            Functions.printStackTrace(x, listener.getLogger());
            return;
        }
        if (tip == null) {
            listener.getLogger().println("Could not determine exact tip revision of " + branch.getName());
            return;
        }
        build.addAction(new SCMRevisionAction(scmSource, tip));
    }

}
