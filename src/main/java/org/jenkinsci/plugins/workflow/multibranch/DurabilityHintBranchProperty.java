package org.jenkinsci.plugins.workflow.multibranch;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchPropertyDescriptor;
import jenkins.branch.JobDecorator;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.flow.FlowDurabilityHint;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.properties.DurabilityHintJobProperty;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.List;

/**
 * Branch property so we can define per-branch durability policies, i.e. so feature branches aren't built durably but master is.
 * NEEDS WORKFLOW-JOB-PROPERTY
 * @author Sam Van Oort
 */
@Restricted(NoExternalUse.class)
public class DurabilityHintBranchProperty extends BranchProperty {

    private final FlowDurabilityHint durabilityHint;

    public FlowDurabilityHint getDurabilityHint() {
        return durabilityHint;
    }

    @DataBoundConstructor
    public DurabilityHintBranchProperty(@Nonnull String hintName) {
        this.durabilityHint = DurabilityHintJobProperty.DescriptorImpl.getDurabilityHintForName(hintName);
    }

    public DurabilityHintBranchProperty(@Nonnull FlowDurabilityHint durabilityHint) {
        this.durabilityHint = durabilityHint;
    }

    public String getHintName() {
        return this.durabilityHint.getName();
    }

    @Override
    public final <P extends Job<P, B>, B extends Run<P, B>> JobDecorator<P, B> jobDecorator(Class<P> clazz) {
        if (!clazz.isAssignableFrom(WorkflowJob.class)) {
            return null;
        }
        return new JobDecorator<P, B>() {
            @NonNull
            @Override
            public List<JobProperty<? super P>> jobProperties(
                    @NonNull List<JobProperty<? super P>> jobProperties) {
                List<JobProperty<? super P>> result = asArrayList(jobProperties);
                for (Iterator<JobProperty<? super P>> iterator = result.iterator(); iterator.hasNext(); ) {
                    JobProperty<? super P> p = iterator.next();
                    if (p instanceof DurabilityHintJobProperty) {
                        iterator.remove();
                    }
                }
                if (getDurabilityHint() != null) {
                    result.add((JobProperty)(new DurabilityHintJobProperty(getHintName())));
                }
                return result;
            }
        };
    }

    @Extension
    public static class DescriptorImpl extends BranchPropertyDescriptor {
        @Override
        public String getDisplayName() {
            return "Add a durabilityHint about how durable the pipeline should be";
        }

        public List<FlowDurabilityHint> getDurabilityHintValues() {
            return FlowDurabilityHint.all();
        }

        @CheckForNull
        public static FlowDurabilityHint getDurabilityHintForName(String hintName) {
            for (FlowDurabilityHint hint : FlowDurabilityHint.all()) {
                if (hint.getName().equals(hintName)) {
                    return hint;
                }
            }
            System.out.println("No hint for name: "+hintName);
            return null;
        }

        public static String getDefaultHintName() {
            return Jenkins.getInstance().getExtensionList(FlowDurabilityHint.FullyDurable.class).get(0).getName();
        }
    }
}
