package org.jenkinsci.plugins.workflow.multibranch;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import hudson.model.queue.QueueTaskFuture;
import hudson.util.FormValidation;
import jenkins.branch.MultiBranchProject;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import java.beans.Transient;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class PipelineTriggerProperty extends AbstractFolderProperty<MultiBranchProject<?, ?>>  {

    private static final Logger LOGGER = Logger.getLogger(PipelineTriggerProperty.class.getName());

    private String preActionJobsToTrigger;
    private String postActionJobsToTrigger;
    private transient List<Job> preActionJobs;
    private transient List<Job> postActionJobs;
    private final int quitePeriod = 0;
    private final String projectNameParameterKey = "SOURCE_PROJECT_NAME";

    @DataBoundConstructor
    public PipelineTriggerProperty(String preActionJobsToTrigger, String postActionJobsToTrigger) {
        this.setPreActionJobsToTrigger(preActionJobsToTrigger);
        this.setPostActionJobsToTrigger(postActionJobsToTrigger);
    }

    public String getPreActionJobsToTrigger() {
        return preActionJobsToTrigger;
    }

    @DataBoundSetter
    public void setPreActionJobsToTrigger(String preActionJobsToTrigger) {
        this.setPreActionJobs(this.validateJobs(preActionJobsToTrigger, true));
        this.preActionJobsToTrigger = this.convertJobsToCommaSeparatedString(this.getPreActionJobs());
    }

    public String getPostActionJobsToTrigger() {
        return postActionJobsToTrigger;
    }

    @DataBoundSetter
    public void setPostActionJobsToTrigger(String postActionJobsToTrigger) {
        this.setPostActionJobs(this.validateJobs(postActionJobsToTrigger, true));
        this.postActionJobsToTrigger = this.convertJobsToCommaSeparatedString(this.getPostActionJobs());
    }

    public List<Job> getPreActionJobs() {
        return preActionJobs;
    }

    public void setPreActionJobs(List<Job> preActionJobs) {
        this.preActionJobs = preActionJobs;
    }

    public List<Job> getPostActionJobs() {
        return postActionJobs;
    }

    public void setPostActionJobs(List<Job> postActionJobs) {
        this.postActionJobs = postActionJobs;
    }

    @Extension
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.PipelineTriggerProperty_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractFolder> containerType) {
            return MultiBranchProject.class.isAssignableFrom(containerType);
        }

        public AutoCompletionCandidates doAutoCompletePreActionJobsToTrigger(@QueryParameter String value) {
            return this.autoCompleteCandidates(value);
        }

        public AutoCompletionCandidates doAutoCompletePostActionJobsToTrigger(@QueryParameter String value) {
            return this.autoCompleteCandidates(value);
        }

        private AutoCompletionCandidates autoCompleteCandidates(String value) {
            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            List<Job> jobs = Jenkins.getInstanceOrNull().getAllItems(Job.class);
            for(Job job : jobs) {
                String jobName = job.getFullName();
                if( jobName.contains(value.trim()) && job.hasPermission(Item.BUILD) && job.hasPermission(Item.READ))
                    candidates.add(jobName);
            }
            return candidates;
        }
    }

    private List<Job> validateJobs(String actionJobsToTrigger, boolean addSourceProjectNameStringParameter) {
        List<Job> jobs = Jenkins.getInstanceOrNull().getAllItems(Job.class);
        List<Job> validatedJobs = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(Util.fixNull(Util.fixEmptyAndTrim(actionJobsToTrigger)),",");
        while(tokenizer.hasMoreTokens()) {
            String tokenJobName = tokenizer.nextToken();
            for(Job job : jobs) {
                if( job.getFullName().trim().equals(tokenJobName.trim())) {
                    if( addSourceProjectNameStringParameter ) {
                        //Try to add job property. If fails do not stop just log warning.
                        try {
                            job.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition(this.projectNameParameterKey, "", "Added by Multibranch Pipeline Plugin")));
                        }
                        catch (Exception ex) {
                            LOGGER.log(Level.WARNING,"Could not set String Parameter Definition. This may affect jobs which are triggered from Multibranch Pipeline Plugin.",ex);
                        }
                    }
                    validatedJobs.add(job);
                }
            }
        }
        return validatedJobs;
    }

    private String convertJobsToCommaSeparatedString(List<Job> jobs) {
        List<String> jobFullNames = jobs.stream().map(job -> job.getFullName()).collect(Collectors.toList());
        return String.join(",",jobFullNames);
    }

    public void buildPreActionJobs(String projectName) {
        this.buildJobs(projectName, this.validateJobs(this.getPreActionJobsToTrigger(), false));
    }

    public void buildPostActionJobs(String projectName) {
        this.buildJobs(projectName, this.validateJobs(this.getPostActionJobsToTrigger(), false));
    }


    private void buildJobs(String projectName, List<Job> jobsToBuild) {
        List<ParameterValue> parameterValues = new ArrayList<>();
        parameterValues.add(new StringParameterValue(this.projectNameParameterKey,projectName,"Set by Multibranch Pipeline Plugin"));
        ParametersAction parametersAction = new ParametersAction(parameterValues);
        for(Job job: jobsToBuild) {

            if( job instanceof AbstractProject) {
                AbstractProject abstractProject = (AbstractProject) job;
                abstractProject.scheduleBuild2(this.quitePeriod, parametersAction);
            }
            else if (job instanceof WorkflowJob) {
                WorkflowJob workflowJob = (WorkflowJob) job;
                workflowJob.scheduleBuild2(this.quitePeriod,parametersAction);
            }
        }
    }
}
