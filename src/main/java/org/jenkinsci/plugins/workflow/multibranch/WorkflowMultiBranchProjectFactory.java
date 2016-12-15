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
import jenkins.branch.MultiBranchProjectFactory;
import jenkins.branch.MultiBranchProjectFactoryDescriptor;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Defines organization folders by {@link WorkflowBranchProjectFactory}.
 */
public class WorkflowMultiBranchProjectFactory extends AbstractWorkflowMultiBranchProjectFactory {

    private AbstractWorkflowBranchProjectFactory factory;

    @DataBoundConstructor public WorkflowMultiBranchProjectFactory(AbstractWorkflowBranchProjectFactory factory) {
        this.factory = factory;
    }

    @Override protected SCMSourceCriteria getSCMSourceCriteria(SCMSource source) {
        return factory.getSCMSourceCriteria(source);
    }

    @Extension public static class DescriptorImpl extends MultiBranchProjectFactoryDescriptor {

        private AbstractWorkflowBranchProjectFactory factory;

        public DescriptorImpl(){}

        @DataBoundConstructor public DescriptorImpl(AbstractWorkflowBranchProjectFactory factory){
            this.factory = factory;
        }

        public AbstractWorkflowBranchProjectFactory getFactory(){
            return factory;
        }

        @Override public MultiBranchProjectFactory newInstance() {
            return new WorkflowMultiBranchProjectFactory(factory != null ? factory : new WorkflowBranchProjectFactory());
        }

        @Override public String getDisplayName() {
            return "Pipeline script";
        }

    }

}
