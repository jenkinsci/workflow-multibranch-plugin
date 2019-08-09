package org.jenkinsci.plugins.workflow.multibranch;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import jenkins.branch.MultiBranchProject;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Job property to enable setting jobs to trigger when a pipeline is created or deleted.
 * In details by this, multi branch pipeline will trigger other job/jobs depending on the configuration.
 * Jobs defined in Pipeline Pre Create Jobs Trigger Field, will be triggered when a new pipeline created by branch indexing.
 * Jobs defined in Pipeline Post Create Jobs Trigger Field, will be triggered when a pipeline is deleted by branch indexing.
 */
public class PipelineTriggerProperty extends AbstractFolderProperty<MultiBranchProject<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(PipelineTriggerProperty.class.getName());

    private String preActionJobsToTrigger;
    private String postActionJobsToTrigger;
    private transient List<Job> preActionJobs;
    private transient List<Job> postActionJobs;
    private final int quitePeriod = 0;
    public static final String projectNameParameterKey = "SOURCE_PROJECT_NAME";

    /**
     * @see DataBoundConstructor
     * @param preActionJobsToTrigger  Full names of the jobs in comma separated format which are defined in the field
     * @param postActionJobsToTrigger  Full names of the jobs in comma separated format which are defined in the field
     */
    @DataBoundConstructor
    public PipelineTriggerProperty(String preActionJobsToTrigger, String postActionJobsToTrigger) {
        this.setPreActionJobsToTrigger(preActionJobsToTrigger);
        this.setPostActionJobsToTrigger(postActionJobsToTrigger);
    }

    /**
     * Getter method for @preActionJobsToTrigger
     * @return  Full names of the jobs in comma separated format
     */
    public String getPreActionJobsToTrigger() {
        return preActionJobsToTrigger;
    }

    /**
     * Setter method for @preActionJobsToTrigger
     * Additionally. this methods parses job names from @preActionJobsToTrigger, convert to List of Job and store in @preActionJobs for later use.
     * @param preActionJobsToTrigger Full names of the jobs in comma separated format which are defined in the field
     */
    @DataBoundSetter
    public void setPreActionJobsToTrigger(String preActionJobsToTrigger) {
        this.setPreActionJobs(this.validateJobs(preActionJobsToTrigger, true));
        this.preActionJobsToTrigger = this.convertJobsToCommaSeparatedString(this.getPreActionJobs());
    }

    /**
     * Getter method for @postActionJobsToTrigger
     * @return  Full names of the jobs in comma-separated format
     */
    public String getPostActionJobsToTrigger() {
        return postActionJobsToTrigger;
    }

    /**
     * Setter method for @postActionJobsToTrigger
     * Additionally. this methods parses job names from @postActionJobsToTrigger, convert to List of Job and store in @postActionJobs for later use.
     * @param postActionJobsToTrigger Full names of the jobs in comma-separated format which are defined in the field
     */
    @DataBoundSetter
    public void setPostActionJobsToTrigger(String postActionJobsToTrigger) {
        this.setPostActionJobs(this.validateJobs(postActionJobsToTrigger, true));
        this.postActionJobsToTrigger = this.convertJobsToCommaSeparatedString(this.getPostActionJobs());
    }

    /**
     * Getter method for @preActionJobs
     * @return List of Job for Pre Action
     */
    public List<Job> getPreActionJobs() {
        return preActionJobs;
    }

    /**
     * Setter method for @preActionJobs
     * @param preActionJobs List of Job for Pre Action
     */
    public void setPreActionJobs(List<Job> preActionJobs) {
        this.preActionJobs = preActionJobs;
    }

    /**
     * Getter method for @postActionJobs
     * @return List of Job for Post Action
     */
    public List<Job> getPostActionJobs() {
        return postActionJobs;
    }

    /**
     * Setter method for @preActionJobs
     * @param postActionJobs List of Job for Post Action
     */
    public void setPostActionJobs(List<Job> postActionJobs) {
        this.postActionJobs = postActionJobs;
    }

    /**
     * @see AbstractFolderPropertyDescriptor
     */
    @Extension
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {

        /**
         * @see AbstractFolderPropertyDescriptor
         * @return  Property Name
         */
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.PipelineTriggerProperty_DisplayName();
        }

        /**
         * Return true if calling class is @{@link MultiBranchProject}
         * @see AbstractFolderPropertyDescriptor
         * @param containerType See AbstractFolder
         * @return @boolean
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractFolder> containerType) {
            return MultiBranchProject.class.isAssignableFrom(containerType);
        }

        /**
         * Auto complete methods @preActionJobsToTrigger field.
         * @param value  Value to search in Job Full Names
         * @return @{@link AutoCompletionCandidates}
         */
        public AutoCompletionCandidates doAutoCompletePreActionJobsToTrigger(@QueryParameter String value) {
            return this.autoCompleteCandidates(value);
        }

        /**
         * Auto complete methods @postActionJobsToTrigger field.
         * @param value  Value to search in Job Full Namesif
         * @return @{@link AutoCompletionCandidates}
         */
        public AutoCompletionCandidates doAutoCompletePostActionJobsToTrigger(@QueryParameter String value) {
            return this.autoCompleteCandidates(value);
        }

        /**
         * Get all Job items in Jenkins. Filter them if they contain @value in Job Full names.
         * Also filter Jobs which have @Item.BUILD and @Item.READ permissions.
         * @param value  Value to search in Job Full Names
         * @return @{@link AutoCompletionCandidates}
         */
        private AutoCompletionCandidates autoCompleteCandidates(String value) {
            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            List<Job> jobs = Jenkins.getInstanceOrNull().getAllItems(Job.class);
            for (Job job : jobs) {
                String jobName = job.getFullName();
                if (jobName.contains(value.trim()) && job.hasPermission(Item.BUILD) && job.hasPermission(Item.READ))
                    candidates.add(jobName);
            }
            return candidates;
        }
    }

    /**
     * Find and check Jobs which are defined in @actionJobsToTrigger in comma-separated format.
     * Additionally, create StringParameterDefinition in Jobs to pass @projectNameParameterKey as build value.
     * @param actionJobsToTrigger Full names of the Jobs in comma-separated format which are defined in the field
     * @param addSourceProjectNameStringParameter If set True, create StringParameterDefinition in Job
     * @return List of Job
     */
    private List<Job> validateJobs(String actionJobsToTrigger, boolean addSourceProjectNameStringParameter) {
        List<Job> jobs = Jenkins.getInstanceOrNull().getAllItems(Job.class);
        List<Job> validatedJobs = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(Util.fixNull(Util.fixEmptyAndTrim(actionJobsToTrigger)), ",");
        while (tokenizer.hasMoreTokens()) {
            String tokenJobName = tokenizer.nextToken();
            for (Job job : jobs) {
                if (job.getFullName().trim().equals(tokenJobName.trim())) {
                    if (addSourceProjectNameStringParameter) {
                        //Try to add job property. If fails do not stop just log warning.
                        try {
                            job.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition(PipelineTriggerProperty.projectNameParameterKey, "", "Added by Multibranch Pipeline Plugin")));
                        } catch (Exception ex) {
                            LOGGER.log(Level.WARNING, "Could not set String Parameter Definition. This may affect jobs which are triggered from Multibranch Pipeline Plugin.", ex);
                        }
                    }
                    validatedJobs.add(job);
                }
            }
        }
        return validatedJobs;
    }

    /**
     * Get full names Jobs and return in comma separated format.
     * @param jobs List of Job
     * @return  Full names of the jobs in comma separated format
     */
    private String convertJobsToCommaSeparatedString(List<Job> jobs) {
        List<String> jobFullNames = jobs.stream().map(job -> job.getFullName()).collect(Collectors.toList());
        return String.join(",", jobFullNames);
    }

    /**
     * Build Jobs which are defined in the @preActionJobsToTrigger field.
     * @param projectName @String Name of the project. This will be branch name which is found in branch indexing.
     *                    Also this value will be passed as StringParameterDefinition
     */
    public void buildPreActionJobs(String projectName) {
        this.buildJobs(projectName, this.validateJobs(this.getPreActionJobsToTrigger(), false));
    }

    /**
     * Build Jobs which are defined in the @postActionJobsToTrigger field.
     * @param projectName @String Name of the project. This will be branch name which is found in branch indexing.
     *                    Also this value will be passed as StringParameterDefinition
     */
    public void buildPostActionJobs(String projectName) {
        this.buildJobs(projectName, this.validateJobs(this.getPostActionJobsToTrigger(), false));
    }


    /**
     * Build Jobs and pass parameter to Build
     * @param projectName @String Name of the project. This value will be passed as StringParameterDefinition
     * @param jobsToBuild List of Jobs to build
     */
    private void buildJobs(String projectName, List<Job> jobsToBuild) {
        List<ParameterValue> parameterValues = new ArrayList<>();
        parameterValues.add(new StringParameterValue(PipelineTriggerProperty.projectNameParameterKey, projectName, "Set by Multibranch Pipeline Plugin"));
        ParametersAction parametersAction = new ParametersAction(parameterValues);
        for (Job job : jobsToBuild) {

            if (job instanceof AbstractProject) {
                AbstractProject abstractProject = (AbstractProject) job;
                abstractProject.scheduleBuild2(this.quitePeriod, parametersAction);
            } else if (job instanceof WorkflowJob) {
                WorkflowJob workflowJob = (WorkflowJob) job;
                workflowJob.scheduleBuild2(this.quitePeriod, parametersAction);
            }
        }
    }
}
