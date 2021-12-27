package org.jenkinsci.plugins.workflow.multibranch;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor;
import hudson.model.InvisibleAction;
import hudson.model.JobProperty;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Invisible action used for tracking what {@link JobProperty}s were defined in the Jenkinsfile in the last run of a
 * job.
 */
public class JobPropertyTrackerAction extends InvisibleAction {
    /**
     * Uses {@link Descriptor#getId()} to identify the {@link JobProperty}s.
     */
    private final Set<String> jobPropertyDescriptors = new HashSet<>();

    public JobPropertyTrackerAction(@NonNull List<JobProperty> jobProperties) {
        for (JobProperty j : jobProperties) {
            jobPropertyDescriptors.add(j.getDescriptor().getId());
        }
    }

    public Set<String> getJobPropertyDescriptors() {
        return Collections.unmodifiableSet(jobPropertyDescriptors);
    }

    @Override
    public String toString() {
        return "JobPropertyTrackerAction[jobPropertyDescriptors:" + jobPropertyDescriptors + "]";
    }
}
