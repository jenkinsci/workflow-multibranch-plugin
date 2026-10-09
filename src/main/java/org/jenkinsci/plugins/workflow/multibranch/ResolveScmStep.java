/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package org.jenkinsci.plugins.workflow.multibranch;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.search.Search;
import hudson.search.SearchIndex;
import hudson.security.ACL;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Resolves an {@link SCM} from a {@link SCMSource} using a priority list of target branch names.
 *
 * @since 2.10
 */
public class ResolveScmStep extends Step {

    /**
     * The {@link SCMSource}
     */
    @NonNull
    private final SCMSource source;

    /**
     * The {@link SCMSource}
     */
    @NonNull
    private final List<String> targets;

    /**
     * If {@code true} then {@code null} will be returned in the event that none of the target branch names can be
     * resolved.
     */
    private boolean ignoreErrors;

    /**
     * Constructor.
     *
     * @param source  The {@link SCMSource}
     * @param targets The {@link SCMSource}
     */
    @DataBoundConstructor
    public ResolveScmStep(@NonNull SCMSource source, @NonNull List<String> targets) {
        this.source = source;
        // JENKINS-67005: Pipeline passes null when targets is omitted from the step call.
        Objects.requireNonNull(
                targets,
                "targets is required; provide candidate branch names, e.g. targets: [env.BRANCH_NAME, 'main']");
        this.targets = new ArrayList<>(targets);
    }

    /**
     * Gets the {@link SCMSource} to resolve from.
     *
     * @return the {@link SCMSource} to resolve from.
     */
    @NonNull
    public SCMSource getSource() {
        return source;
    }

    /**
     * Gets the target branch names to try and resolve.
     *
     * @return the target branch names to try and resolve.
     */
    @NonNull
    public List<String> getTargets() {
        return Collections.unmodifiableList(targets);
    }

    /**
     * Returns {@code true} if and only if errors will be ignored.
     *
     * @return {@code true} if and only if errors will be ignored.
     */
    public boolean isIgnoreErrors() {
        return ignoreErrors;
    }

    /**
     * Sets the error handling behaviour.
     *
     * @param ignoreErrors {@code true} if and only if errors will be ignored.
     */
    @DataBoundSetter
    public void setIgnoreErrors(boolean ignoreErrors) {
        this.ignoreErrors = ignoreErrors;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(context, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "ResolveScmStep{" +
                "source=" + source +
                ", targets=" + targets +
                ", ignoreErrors=" + ignoreErrors +
                '}';
    }

    /**
     * Our {@link Descriptor}.
     */
    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public Set<Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class, Run.class);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getFunctionName() {
            return "resolveScm";
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return "Resolves an SCM from an SCM Source and a list of candidate target branch names";
        }

        @Override
        public Step newInstance(@CheckForNull StaplerRequest2 req, @NonNull JSONObject formData)
                throws FormException {
            assert req != null : "see contract for method, it's never null but has to claim it could be";
            // roll our own because we want the groovy api to be easier than the jelly form binding would have us
            JSONObject src = formData.getJSONObject("source");
            src.put("id", "_");
            SCMSource source = req.bindJSON(SCMSource.class, src);
            List<String> targets = new ArrayList<>();
            // TODO JENKINS-27901 use standard control when available
            Object t = formData.get("targets");
            if (t instanceof JSONObject) {
                JSONObject o = (JSONObject) t;
                targets.add(o.getString("target"));
            } else if (t instanceof JSONArray) {
                JSONArray a = (JSONArray) t;
                for (int i = 0; i < a.size(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    targets.add(o.getString("target"));
                }
            }
            ResolveScmStep step = new ResolveScmStep(source, targets);
            if (formData.optBoolean("ignoreErrors", false)) {
                step.setIgnoreErrors(true);
            }
            return step;
        }

        public FormValidation doCheckTarget(@QueryParameter String value) {
            if (value != null && !value.isBlank()) {
                return FormValidation.ok();
            }
            return FormValidation.error("You must supply a target branch name to resolve");
        }
    }

    /**
     * Our {@link StepExecution}.
     */
    public static class Execution extends SynchronousStepExecution<SCM> {

        /**
         * Ensure consistent serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * The {@link SCMSource}
         */
        @NonNull
        private transient final SCMSource source;

        /**
         * The {@link SCMSource}
         */
        @NonNull
        private final List<String> targets;

        /**
         * If {@code true} then {@code null} will be returned in the event that none of the target branch names can be
         * resolved.
         */
        private boolean ignoreErrors;

        /**
         * Our constructor.
         *
         * @param context the context.
         * @param step    the step.
         */
        Execution(StepContext context, ResolveScmStep step) {
            super(context);
            this.source = step.getSource();
            this.targets = new ArrayList<>(step.getTargets());
            this.ignoreErrors = step.isIgnoreErrors();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected SCM run() throws Exception {
            StepContext context = getContext();
            TaskListener listener = context.get(TaskListener.class);
            assert listener != null;
            PrintStream out = listener.getLogger();
            out.printf("Checking for first existing branch from %s...%n", targets);
            var item = context.get(Run.class).getParent();
            source.setOwner(new ResolveSCMOwner(source, item));
            SCMRevision fetch = source.fetch(new ObserverImpl(targets), listener).result();
            if (fetch == null) {
                if (ignoreErrors) {
                    out.println("Could not find any matching branch");
                    return null;
                }
                throw new AbortException("Could not find any matching branch");
            }
            out.printf("Found %s at revision %s%n", fetch.getHead().getName(), fetch);
            return source.build(fetch.getHead(), fetch);
        }

    }

    private static class ResolveSCMOwner implements SCMSourceOwner {
        @NonNull
        private transient final SCMSource source;
        @NonNull
        private transient final Item context;

        public ResolveSCMOwner(@NonNull SCMSource source, @NonNull Item context) {
            this.source = source;
            this.context = context;
        }


        @Override
        public @org.jspecify.annotations.NonNull List<SCMSource> getSCMSources() {
            return List.of(source);
        }

        @Override
        public void onSCMSourceUpdated(@org.jspecify.annotations.NonNull SCMSource source) {

        }

        @Override
        public ItemGroup<? extends Item> getParent() {
            return context.getParent();
        }

        @Override
        public Collection<? extends Job> getAllJobs() {
            return context.getAllJobs();
        }

        @Override
        public @org.jspecify.annotations.NonNull String getName() {
            return context.getName();
        }

        @Override
        public @org.jspecify.annotations.NonNull String getFullName() {
            return context.getFullName();
        }

        @Override
        public String getDisplayName() {
            return context.getDisplayName();
        }

        @Override
        public String getUrl() {
            return context.getUrl();
        }

        @Override
        public String getShortUrl() {
            return context.getShortUrl();
        }

        @Override
        public void onLoad(ItemGroup<? extends Item> parent, String name) throws IOException {
            context.onLoad(parent, name);
        }

        @Override
        public void onCopiedFrom(Item src) {
            context.onCopiedFrom(src);
        }

        @Override
        public void save() throws IOException {
            context.save();
        }

        @Override
        public void delete() throws IOException, InterruptedException {
            context.delete();
        }

        @Override
        public File getRootDir() {
            return context.getRootDir();
        }

        @Override
        public Search getSearch() {
            return context.getSearch();
        }

        @Override
        public String getSearchName() {
            return context.getSearchName();
        }

        @Override
        public String getSearchUrl() {
            return context.getSearchUrl();
        }

        @Override
        public SearchIndex getSearchIndex() {
            return context.getSearchIndex();
        }

        @Override
        public @org.jspecify.annotations.NonNull ACL getACL() {
            return context.getACL();
        }

        @Override
        public String getFullDisplayName() {
            return context.getFullDisplayName();
        }
    }

    /**
     * An observer that collects the {@link SCMRevision} of a named {@link SCMHead} from a list of priority
     * candidates and stops observing when the preferred candidate is found.
     */
    private static class ObserverImpl extends SCMHeadObserver {
        /**
         * The heads we are looking for
         */
        private final Map<String, SCMRevision> revision = new LinkedHashMap<>();

        /**
         * Constructor.
         *
         * @param heads the {@link SCMHead#getName()} to get the {@link SCMRevision} of.
         */
        public ObserverImpl(@NonNull List<String> heads) {
            heads.getClass(); // fail fast if null
            for (String head : heads) {
                if (head != null && !head.isBlank()) {
                    revision.put(head, null);
                }
            }
        }

        /**
         * Returns the result.
         *
         * @return the result.
         */
        @CheckForNull
        public SCMRevision result() {
            for (SCMRevision r : revision.values()) {
                if (r != null) {
                    return r;
                }
            }
            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void observe(@NonNull SCMHead head, @NonNull SCMRevision revision) {
            if (this.revision.containsKey(head.getName())) {
                this.revision.put(head.getName(), revision);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isObserving() {
            return revision.values().iterator().next() == null;
        }

    }
}
