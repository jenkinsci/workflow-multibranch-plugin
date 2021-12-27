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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectFactory;
import jenkins.branch.OrganizationFolder;
import jenkins.model.TransientActionFactory;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;

/**
 * Defines organization folders by some {@link AbstractWorkflowBranchProjectFactory}.
 */
public abstract class AbstractWorkflowMultiBranchProjectFactory extends MultiBranchProjectFactory.BySCMSourceCriteria {

    @NonNull
    @Override protected final WorkflowMultiBranchProject doCreateProject(@NonNull ItemGroup<?> parent, @NonNull String name, @NonNull Map<String,Object> attributes) throws IOException, InterruptedException {
        WorkflowMultiBranchProject project = new WorkflowMultiBranchProject(parent, name);
        customize(project);
        return project;
    }

    @Override public final void updateExistingProject(@NonNull MultiBranchProject<?, ?> project, @NonNull Map<String, Object> attributes, @NonNull TaskListener listener) throws IOException, InterruptedException {
        if (project instanceof WorkflowMultiBranchProject) {
            customize((WorkflowMultiBranchProject) project);
        } // otherwise got recognized by something else before, oh well
    }

    protected void customize(WorkflowMultiBranchProject project) throws IOException, InterruptedException {}

    @Extension public static class PerFolderAdder extends TransientActionFactory<OrganizationFolder> {

        @Override public Class<OrganizationFolder> type() {
            return OrganizationFolder.class;
        }

        @NonNull
        @Override public Collection<? extends Action> createFor(@NonNull OrganizationFolder target) {
            if (target.getProjectFactories().get(AbstractWorkflowMultiBranchProjectFactory.class) != null && target.hasPermission(Item.EXTENDED_READ)) {
                return Collections.singleton(new Snippetizer.LocalAction());
            } else {
                return Collections.emptySet();
            }
        }

    }

}
