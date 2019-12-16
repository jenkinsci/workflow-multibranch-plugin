package org.jenkinsci.plugins.workflow.multibranch;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchPropertyDescriptor;
import jenkins.branch.BranchPropertyStrategy;
import jenkins.branch.JobDecorator;
import jenkins.branch.MultiBranchProject;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.workflow.flow.DurabilityHintProvider;
import org.jenkinsci.plugins.workflow.flow.FlowDurabilityHint;
import org.jenkinsci.plugins.workflow.flow.GlobalDefaultFlowDurabilityLevel;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Branch property so we can define per-branch durability policies, i.e. so feature branches aren't built durably but master is.
 * Also lets us set the durability level before the pipeline has run (a step ahead of the "properties" step).
 *
 * This implementation is designed so that each build will freshly evaluate the {@link FlowDurabilityHint} provided by {@link BranchPropertyStrategy}
 *  thus sidestepping issues with failing to update along with the BranchPropertyStrategy (JENKINS-48826).
 *
 * @author Sam Van Oort
 */
@Restricted(NoExternalUse.class)
public class DurabilityHintBranchProperty extends BranchProperty {

    private final FlowDurabilityHint hint;

    public FlowDurabilityHint getHint() {
        return hint;
    }

    @DataBoundConstructor
    public DurabilityHintBranchProperty(@Nonnull FlowDurabilityHint hint) {
        this.hint = hint;
    }

    /** No-op impl because we only care about the actual BranchProperty attached. */
    @Override
    public final <P extends Job<P, B>, B extends Run<P, B>> JobDecorator<P, B> jobDecorator(Class<P> clazz) {
        return null;
    }

    @Extension
    public static class DescriptorImpl extends BranchPropertyDescriptor implements DurabilityHintProvider {
        @Override
        public String getDisplayName() {
            return "Pipeline branch speed/durability override";
        }

        public FlowDurabilityHint[] getDurabilityHintValues() {
            return FlowDurabilityHint.values();
        }

        public static FlowDurabilityHint getDefaultDurabilityHint() {
            return GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint();
        }

        /** Lower ordinal than {@link org.jenkinsci.plugins.workflow.job.properties.DurabilityHintJobProperty} so those can override. */
        @Override
        public int ordinal() {
            return 200;
        }

        /**
         * Dynamically fetch the property with each build, because the {@link BranchPropertyStrategy} does not re-evaluate,
         * resulting in {@see <a href="https://issues.jenkins-ci.org/browse/JENKINS-48826">JENKINS-48826</a>}. */
        @CheckForNull
        @Override
        public FlowDurabilityHint suggestFor(@Nonnull Item x) {
            // BranchJobProperty *should* be present if it's a child of a MultiBranchProject but we double-check for safety
            if (x instanceof WorkflowJob && x.getParent() instanceof MultiBranchProject && ((WorkflowJob)x).getProperty(BranchJobProperty.class) != null) {
                MultiBranchProject mp  = (MultiBranchProject)(x.getParent());
                WorkflowJob job = (WorkflowJob)x;
                BranchJobProperty bjp = job.getProperty(BranchJobProperty.class);

                String sourceId = bjp.getBranch().getSourceId();
                if (sourceId != null) {
                    SCMSource source = mp.getSCMSource(sourceId);
                    if (source != null) {
                        BranchPropertyStrategy bps = mp.getBranchPropertyStrategy(source);
                        if (bps != null) {
                            Optional<BranchProperty> props = bps.getPropertiesFor(bjp.getBranch().getHead()).stream().filter(
                                    bp -> bp instanceof DurabilityHintBranchProperty
                            ).findFirst();
                            if (props.isPresent()) {
                                return ((DurabilityHintBranchProperty)props.get()).getHint();
                            }
                        }
                    }
                }

            }
            return null;
        }
    }
}
