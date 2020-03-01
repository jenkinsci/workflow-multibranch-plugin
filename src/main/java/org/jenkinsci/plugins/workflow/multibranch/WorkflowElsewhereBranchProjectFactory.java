/*
 * The MIT License
 *
 * Copyright (c) 2020, Filipe Cristovao
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
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMProbeStat;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.Collection;

/**
 * Recognizes branches based on a marker file, and builds them according to a Jenkinsfile
 * defined elsewhere (e.g., another SCM source).
 */
public class WorkflowElsewhereBranchProjectFactory extends AbstractWorkflowBranchProjectFactory {
    static final String MARKER_FILE = ".marker";
    private String markerPath = MARKER_FILE;
    private FlowDefinition flowDefinition;

    public Object readResolve() {
        if (this.markerPath == null) {
            this.markerPath = WorkflowElsewhereBranchProjectFactory.MARKER_FILE;
        }
        return this;
    }

    @DataBoundConstructor public WorkflowElsewhereBranchProjectFactory(FlowDefinition flowDefinition) {
        this.flowDefinition = flowDefinition;
    }

    public String getMarkerPath(){
        return markerPath;
    }

    @DataBoundSetter
    public void setMarkerPath(String markerPath) {
        if (StringUtils.isEmpty(markerPath)) {
            this.markerPath = MARKER_FILE;
        } else {
            this.markerPath = markerPath;
        }
    }

    public FlowDefinition getFlowDefinition() {
        return flowDefinition;
    }

    public void setFlowDefinition(FlowDefinition flowDefinition) {
        this.flowDefinition = flowDefinition;
    }

    @Override protected FlowDefinition createDefinition() {
        return new FlowDefinitionWrapper(flowDefinition);
    }

    @Override protected SCMSourceCriteria getSCMSourceCriteria(SCMSource source) {
        return new SCMSourceCriteria() {
            @Override public boolean isHead(Probe probe, TaskListener listener) throws IOException {
                SCMProbeStat stat = probe.stat(markerPath);
                switch (stat.getType()) {
                    case NONEXISTENT:
                        if (stat.getAlternativePath() != null) {
                            listener.getLogger().format("      ‘%s’ not found (but found ‘%s’, search is case sensitive)%n", markerPath, stat.getAlternativePath());
                        } else {
                            listener.getLogger().format("      ‘%s’ not found%n", markerPath);
                        }
                        return false;
                    case DIRECTORY:
                        listener.getLogger().format("      ‘%s’ found but is a directory not a file%n", markerPath);
                        return false;
                    default:
                        listener.getLogger().format("      ‘%s’ found%n", markerPath);
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

    @Extension public static class DescriptorImpl extends AbstractWorkflowBranchProjectFactoryDescriptor {

        @Override public String getDisplayName() {
            return "by marker file and Pipeline Definition";
        }

        public Collection<? extends Descriptor<FlowDefinition>> applicableDescriptors(Object context) {
            return DescriptorVisibilityFilter.apply(context,
                                                    Jenkins.get().getDescriptorList(FlowDefinition.class));
        }

    }

}
