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

import hudson.Extension;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * Recognizes and builds {@code Jenkinsfile}.
 */
public class ConfigurableWorkflowBranchProjectFactory extends AbstractWorkflowBranchProjectFactory {

    private String scriptPath = WorkflowBranchProjectFactory.SCRIPT;

    @DataBoundConstructor public ConfigurableWorkflowBranchProjectFactory(String scriptPath) {
        this.scriptPath = scriptPath;
    }

    public String getScriptPath(){
        return scriptPath;
    }

    @Override protected FlowDefinition createDefinition() {
        return new SCMBinder(scriptPath);
    }

    @Override protected SCMSourceCriteria getSCMSourceCriteria(SCMSource source) {
        return new SCMSourceCriteria() {
            @Override public boolean isHead(Probe probe, TaskListener listener) throws IOException {
                return probe.exists(scriptPath);
            }
        };
    }

    @Extension public static class DescriptorImpl extends AbstractWorkflowBranchProjectFactoryDescriptor {

        private String scriptPath = WorkflowBranchProjectFactory.SCRIPT;

        public DescriptorImpl(){}

        @DataBoundConstructor
        public DescriptorImpl(String scriptPath){ this.scriptPath = scriptPath; }

        public String getScriptPath(){
            return scriptPath;
        }

        @Override public String getDisplayName() {
            return Messages.ConfigurableWorkflowBranchProjectFactory_configurableScript();
        }

    }

}
