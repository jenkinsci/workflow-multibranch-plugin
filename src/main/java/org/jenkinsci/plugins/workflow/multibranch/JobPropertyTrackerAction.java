package org.jenkinsci.plugins.workflow.multibranch;

import hudson.model.InvisibleAction;
import hudson.model.JobProperty;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Invisible action used for tracking what {@link JobProperty}s were defined in the Jenkinsfile for a given run.
 */
public class JobPropertyTrackerAction extends InvisibleAction {
    private final Set<String> jobPropertyDescriptors = new HashSet<>();

    public JobPropertyTrackerAction(@CheckForNull List<JobProperty> jobProperties) {
        if (jobProperties != null) {
            for (JobProperty j : jobProperties) {
                jobPropertyDescriptors.add(j.getDescriptor().getId());
            }
        }
    }

    /**
     * Alternative constructor for copying an existing {@link JobPropertyTrackerAction}'s contents directly.
     *
     * @param copyFrom a non-null {@link JobPropertyTrackerAction}
     */
    public JobPropertyTrackerAction(@Nonnull JobPropertyTrackerAction copyFrom) {
        this.jobPropertyDescriptors.addAll(copyFrom.getJobPropertyDescriptors());
    }

    public Set<String> getJobPropertyDescriptors() {
        return Collections.unmodifiableSet(jobPropertyDescriptors);
    }

    @Override
    public String toString() {
        return "JobPropertyTrackerAction[jobPropertyDescriptors:" + jobPropertyDescriptors + "]";
    }
}
