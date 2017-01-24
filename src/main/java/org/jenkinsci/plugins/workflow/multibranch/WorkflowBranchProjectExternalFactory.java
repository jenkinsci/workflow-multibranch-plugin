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
import hudson.model.TaskListener;
import java.io.IOException;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Builds all branches unless otherwise filtered.
 */
public class WorkflowBranchProjectExternalFactory extends AbstractWorkflowBranchProjectFactory {
    public final String script;
    public final boolean sandbox;

    @DataBoundConstructor public WorkflowBranchProjectExternalFactory(String script, boolean sandbox) {
        this.script = script;
        this.sandbox = sandbox;
    }

    @Override protected FlowDefinition createDefinition() {
        return new CpsFlowDefinition(script, sandbox);
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
            return "External Script";
        }

    }

}
