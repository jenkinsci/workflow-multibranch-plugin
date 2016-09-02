package org.jenkinsci.plugins.workflow.multibranch;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.groovy.GroovyScript;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinitionDescriptor;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.io.File;
import java.util.List;

/**
 * Checks out the local default version of {@link WorkflowBranchProjectFactory#SCRIPT} in order if exist:
 * 1. From module checkout
 * 1. From task workspace directory
 * 2. From global jenkins managed files
 */
public class LocalSCMBinder extends SCMBinder {

    @Override
    public FlowExecution create(FlowExecutionOwner handle, TaskListener listener, List<? extends Action> actions) throws Exception {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return super.create(handle, listener, actions);
        }
        FilePath workspacePath = jenkins.getWorkspaceFor(((WorkflowRun) handle.getExecutable()).getParent());
        if (workspacePath == null || workspacePath.child(WorkflowBranchLocalProjectFactory.SCRIPT).exists()) {
            return super.create(handle, listener, actions);
        }

        Queue.Executable executable = handle.getExecutable();
        File rootDir = null;
        if (executable != null) {
            WorkflowJob workflowJob = ((WorkflowJob) handle.getExecutable().getParent());
            rootDir = workflowJob.getParent().getRootDir();
            if (rootDir != null) {
                File localConfig = new File(rootDir + File.separator + WorkflowBranchLocalProjectFactory.SCRIPT);
                if (localConfig.exists()) {
                    return new CpsFlowDefinition(FileUtils.readFileToString(localConfig, "utf-8"), false).create(handle, listener, actions);
                }
            }
        }

        ConfigProvider configProvider = ConfigProvider.getByIdOrNull(GroovyScript.class.getName());
        if (configProvider != null) {
            Config config = configProvider.getConfigById(WorkflowBranchLocalProjectFactory.SCRIPT);
            if (config != null) {
                return new CpsFlowDefinition(config.content, false).create(handle, listener, actions);
            }
        }
        throw new IllegalArgumentException(WorkflowBranchLocalProjectFactory.SCRIPT + " not found");
    }

    @Extension
    public static class DescriptorImpl extends FlowDefinitionDescriptor {

        @Override
        public String getDisplayName() {
           return "Pipeline script from default " + WorkflowBranchProjectFactory.SCRIPT;
        }

    }

    /**
     * Want to display this in the r/o configuration for a branch project, but not offer it on standalone jobs or in any other context.
     */
    @Extension
    public static class HideMeElsewhere extends DescriptorVisibilityFilter {

        @Override
        public boolean filter(Object context, Descriptor descriptor) {
            if (descriptor instanceof DescriptorImpl) {
                return context instanceof WorkflowJob && ((WorkflowJob) context).getParent() instanceof WorkflowMultiBranchProject;
            }
            return true;
        }

    }
}
