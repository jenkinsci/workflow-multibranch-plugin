package org.jenkinsci.plugins.workflow.multibranch;

import hudson.model.Descriptor;
import hudson.model.InvisibleAction;
import hudson.model.JobProperty;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Invisible action used for tracking what {@link JobProperty}s were defined in the Jenkinsfile for a given run.
 */
class JobPropertyTrackerAction extends InvisibleAction {
    /**
     * Uses {@link Descriptor#getId()} to identify the {@link JobProperty}s.
     */
    private final Set<String> jobPropertyDescriptors = new HashSet<>();

    JobPropertyTrackerAction(@Nonnull List<JobProperty> jobProperties) {
        for (JobProperty j : jobProperties) {
            jobPropertyDescriptors.add(j.getDescriptor().getId());
        }
    }

    Set<String> getJobPropertyDescriptors() {
        return Collections.unmodifiableSet(jobPropertyDescriptors);
    }

    @Override
    public String toString() {
        return "JobPropertyTrackerAction[jobPropertyDescriptors:" + jobPropertyDescriptors + "]";
    }
}
