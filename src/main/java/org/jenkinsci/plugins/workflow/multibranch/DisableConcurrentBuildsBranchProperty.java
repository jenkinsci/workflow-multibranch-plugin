package org.jenkinsci.plugins.workflow.multibranch;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.Run;
import java.util.List;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchPropertyDescriptor;
import jenkins.branch.JobDecorator;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.properties.DisableConcurrentBuildsJobProperty;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

@Restricted(NoExternalUse.class)
public class DisableConcurrentBuildsBranchProperty extends BranchProperty {

  private boolean enabled;
  private boolean abortPrevious;

  @DataBoundConstructor
  public DisableConcurrentBuildsBranchProperty() {
    enabled = true;
  }

  public boolean isEnabled() {
    return enabled;
  }

  @DataBoundSetter
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isAbortPrevious() {
    return abortPrevious;
  }

  @DataBoundSetter
  public void setAbortPrevious(boolean abortPrevious) {
    this.abortPrevious = abortPrevious;
  }

  @Override
  public final <P extends Job<P, B>, B extends Run<P, B>> JobDecorator<P, B> jobDecorator(Class<P> clazz) {
    if (!WorkflowJob.class.isAssignableFrom(clazz)) {
      return null;
    }

    return new JobDecorator<>() {
      @Override
      @NonNull
      public List<JobProperty<? super P>> jobProperties(@NonNull List<JobProperty<? super P>> properties) {
        List<JobProperty<? super P>> result = BranchProperty.asArrayList(properties);

        if (!enabled) {
          result.removeIf(jp -> jp instanceof DisableConcurrentBuildsJobProperty);

        } else {
          DisableConcurrentBuildsJobProperty prop = new DisableConcurrentBuildsJobProperty();
          prop.setAbortPrevious(abortPrevious);
          //noinspection unchecked
          result.add((JobProperty<? super P>) prop);
        }
        return result;
      }
    };
  }

  @Symbol("disableConcurrentBuilds")
  @Extension
  public static class PropertyDescriptorImpl extends BranchPropertyDescriptor {
    @NonNull
    @Override
    public String getDisplayName() {
      return "Disable concurrent builds";
    }
  }
}
