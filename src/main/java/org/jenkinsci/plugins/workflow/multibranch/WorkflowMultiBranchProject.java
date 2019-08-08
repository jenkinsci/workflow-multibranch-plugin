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

import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import hudson.scm.SCMDescriptor;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.branch.Branch;
import jenkins.branch.BranchProjectFactory;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchPropertyStrategy;
import jenkins.branch.BranchSource;
import jenkins.branch.MultiBranchProject;
import jenkins.branch.MultiBranchProjectDescriptor;
import jenkins.model.TransientActionFactory;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import org.apache.commons.lang.StringUtils;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Representation of a set of workflows keyed off of source branches.
 */
@SuppressWarnings({"unchecked", "rawtypes"}) // core’s fault
public class WorkflowMultiBranchProject extends MultiBranchProject<WorkflowJob,WorkflowRun> {

    private static final Logger LOGGER = Logger.getLogger(WorkflowMultiBranchProject.class.getName());

    public WorkflowMultiBranchProject(ItemGroup parent, String name) {
        super(parent, name);
    }

    @Override protected BranchProjectFactory<WorkflowJob,WorkflowRun> newProjectFactory() {
        return new WorkflowBranchProjectFactory();
    }

    @Override public SCMSourceCriteria getSCMSourceCriteria(SCMSource source) {
        return ((AbstractWorkflowBranchProjectFactory) getProjectFactory()).getSCMSourceCriteria(source);
    }

    @Override
    public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
        super.onLoad(parent, name);
        for (WorkflowJob job : items.values()) {
            if (job.getProperty(BranchJobProperty.class) == null) {
                //JENKINS-55116 It is highly unlikely that this property shouldn't be there
                //we only somehow lost the BranchJobProperty so we take the penalty to load a build or two
                //to see if this is what we think it is, and maybe desperately try to patch it.
                LOGGER.log(Level.WARNING, String.format("[JENKINS-55116] Found potential broken branch job property on %s, attempting to patch, you'll need to run a full rescan asap.", job.getFullName()));
                WorkflowRun build = job.getLastBuild();
                for (int i = 0; i < 3; i++) {
                    if (build != null) {
                        SCMRevisionAction action = build.getAction(SCMRevisionAction.class);
                        if (action != null) {
                            BranchJobProperty p = reconstructBranchJobProperty(job, action);
                            if (p != null) {
                                try {
                                    job.addProperty(p);
                                    LOGGER.log(Level.WARNING, String.format("[JENKINS-55116] Reconstructed branch job property on %s from %s, you'll need to run a full rescan asap.", job.getFullName(), build.getNumber()));
                                    break;
                                } catch (IOException e) {
                                    LOGGER.log(Level.SEVERE, String.format("[JENKINS-55116] Failed storing reconstructed branch job property on %s from %s", job.getFullName(), build.getNumber()), e);
                                }
                            }
                        }
                        build = build.getPreviousBuild();
                    }
                }
            }
        }
    }

    private BranchJobProperty reconstructBranchJobProperty(@Nonnull WorkflowJob job, @Nonnull SCMRevisionAction action) {
        String sourceId = action.getSourceId();
        if (StringUtils.isEmpty(sourceId)) {
            return null;
        }
        SCMHead head = action.getRevision().getHead();
        BranchSource source = null;
        for (BranchSource s : ((WorkflowMultiBranchProject) job.getParent()).getSources()) {
            if (sourceId.equals(s.getSource().getId())) {
                source = s;
                break;
            }
        }
        if (source == null) {
            return null;
        }

        final BranchPropertyStrategy strategy = source.getStrategy();
        return new BranchJobProperty(new Branch(sourceId, head, source.getSource().build(head),
                strategy != null ? strategy.getPropertiesFor(head) : Collections.<BranchProperty>emptyList()));
    }

    @Extension public static class DescriptorImpl extends MultiBranchProjectDescriptor implements IconSpec {

        @Override public String getDisplayName() {
            return Messages.WorkflowMultiBranchProject_DisplayName();
        }

        public String getDescription() {
            return Messages.WorkflowMultiBranchProject_Description();
        }

        public String getIconFilePathPattern() {
            return "plugin/workflow-multibranch/images/:size/pipelinemultibranchproject.png";
        }

        @Override
        public String getIconClassName() {
            return "icon-pipeline-multibranch-project";
        }

        @Override public TopLevelItem newInstance(ItemGroup parent, String name) {
            return new WorkflowMultiBranchProject(parent, name);
        }

        @Override public boolean isApplicable(Descriptor descriptor) {
            if (descriptor instanceof SCMDescriptor) {
                SCMDescriptor d = (SCMDescriptor) descriptor;
                // TODO would prefer to have SCMDescriptor.isApplicable(Class<? extends Job>)
                try {
                    if (!d.isApplicable(new WorkflowJob(null, null))) {
                        return false;
                    }
                } catch (RuntimeException x) {
                    LOGGER.log(Level.FINE, "SCMDescriptor.isApplicable hack failed", x);
                }
            }
            return super.isApplicable(descriptor);
        }

        static {
            IconSet.icons.addIcon(
                    new Icon("icon-pipeline-multibranch-project icon-sm",
                            "plugin/workflow-multibranch/images/16x16/pipelinemultibranchproject.png",
                            Icon.ICON_SMALL_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-pipeline-multibranch-project icon-md",
                            "plugin/workflow-multibranch/images/24x24/pipelinemultibranchproject.png",
                            Icon.ICON_MEDIUM_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-pipeline-multibranch-project icon-lg",
                            "plugin/workflow-multibranch/images/32x32/pipelinemultibranchproject.png",
                            Icon.ICON_LARGE_STYLE));
            IconSet.icons.addIcon(
                    new Icon("icon-pipeline-multibranch-project icon-xlg",
                            "plugin/workflow-multibranch/images/48x48/pipelinemultibranchproject.png",
                            Icon.ICON_XLARGE_STYLE));
        }
    }

    @Extension public static class PerFolderAdder extends TransientActionFactory<WorkflowMultiBranchProject> {

        @Override public Class<WorkflowMultiBranchProject> type() {
            return WorkflowMultiBranchProject.class;
        }

        @Override public Collection<? extends Action> createFor(WorkflowMultiBranchProject target) {
            if (target.hasPermission(Item.EXTENDED_READ)) {
                return Collections.singleton(new Snippetizer.LocalAction());
            } else {
                return Collections.emptySet();
            }
        }

    }

    /**
     * This is triggered when a pipeline is deleted by branch indexing.
     * @param item @{@link WorkflowJob} which will be deleted
     * @throws IOException
     */
    @Override
    public void onDeleted(WorkflowJob item) throws IOException {
        super.onDeleted(item);
        PipelineTriggerProperty pipelineTriggerProperty = this.getProperties().get(PipelineTriggerProperty.class);
        if( pipelineTriggerProperty != null)
            pipelineTriggerProperty.buildPostActionJobs(item.getName());
    }


}
