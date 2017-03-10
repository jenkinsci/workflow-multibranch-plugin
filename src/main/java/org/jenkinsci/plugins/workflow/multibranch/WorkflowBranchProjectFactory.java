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
import java.io.IOException;
import jenkins.scm.api.SCMProbeStat;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Recognizes and builds {@code Jenkinsfile}.
 */
public class WorkflowBranchProjectFactory extends AbstractWorkflowBranchProjectFactory {
    
    static final String JENKINSFILE = "Jenkinsfile";

    private String scriptPath = JENKINSFILE;

    @DataBoundConstructor public WorkflowBranchProjectFactory(String scriptPath) {
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
            @Override public boolean isHead(SCMSourceCriteria.Probe probe, TaskListener listener) throws IOException {
                SCMProbeStat stat = probe.stat(scriptPath);
                switch (stat.getType()) {
                    case NONEXISTENT:
                        if (stat.getAlternativePath() != null) {
                            listener.getLogger().format("      ‘%s’ not found (but found ‘%s’, search is case sensitive)%n", scriptPath, stat.getAlternativePath());
                        } else {
                            listener.getLogger().format("      ‘%s’ not found%n",scriptPath);
                        }
                        return false;
                    case DIRECTORY:
                        listener.getLogger().format("      ‘%s’ found but is a directory not a file%n", scriptPath);
                        return false;
                    default:
                        listener.getLogger().format("      ‘%s’ found%n", scriptPath);
                        return true;

                }
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

    protected Object readResolve(){
        if(scriptPath == null){
            scriptPath = JENKINSFILE; //Maintains backwards compat with when scriptPath wasn't stored per job
        }
        return this;
    }

    @Extension public static class DescriptorImpl extends AbstractWorkflowBranchProjectFactoryDescriptor {

        private String scriptPath = JENKINSFILE;

        public DescriptorImpl(){}

        @DataBoundConstructor
        public DescriptorImpl(String scriptPath){ this.scriptPath = scriptPath; }

        public String getScriptPath(){
            return scriptPath;
        }

        @Override public String getDisplayName() {
            return Messages.ConfigurableWorkflowBranchProjectFactory_PipelineScript();
        }

    }

}
