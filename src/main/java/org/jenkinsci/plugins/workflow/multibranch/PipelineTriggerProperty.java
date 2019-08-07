package org.jenkinsci.plugins.workflow.multibranch;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import hudson.Extension;
import hudson.Util;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Item;
import hudson.model.Job;
import hudson.util.FormValidation;
import jenkins.branch.MultiBranchProject;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

public class PipelineTriggerProperty extends AbstractFolderProperty<MultiBranchProject<?, ?>>  {

    private String preActionJobsToTrigger;
    private String postActionJobsToTrigger;
    private List<Job> preActionJobs;
    private List<Job> postActionJobs;

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
        this.setPreActionJobs(this.validateJobs(preActionJobsToTrigger));
        this.preActionJobsToTrigger = this.convertJobsToCommaSeparatedString(this.getPreActionJobs());
    }

    public String getPostActionJobsToTrigger() {
        return postActionJobsToTrigger;
    }

    @DataBoundSetter
    public void setPostActionJobsToTrigger(String postActionJobsToTrigger) {
        this.setPostActionJobs(this.validateJobs(postActionJobsToTrigger));
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

    private List<Job> validateJobs(String actionJobsToTrigger) {
        List<Job> jobs = Jenkins.getInstanceOrNull().getAllItems(Job.class);
        List<Job> validatedJobs = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(Util.fixNull(Util.fixEmptyAndTrim(actionJobsToTrigger)),",");
        while(tokenizer.hasMoreTokens()) {
            String tokenJobName = tokenizer.nextToken();
            for(Job job : jobs) {
                if( job.getFullName().trim().equals(tokenJobName.trim()))
                    validatedJobs.add(job);
            }
        }
        return validatedJobs;
    }

    private String convertJobsToCommaSeparatedString(List<Job> jobs) {
        List<String> jobFullNames = jobs.stream().map(job -> job.getFullName()).collect(Collectors.toList());
        return String.join(",",jobFullNames);
    }
}
