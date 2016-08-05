/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkinsci.plugins.workflow.multibranch;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Queue;
import jenkins.branch.BranchIndexingCause;
import jenkins.model.OptionalJobProperty;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;

public class SuppressBranchIndexingBuildsJobProperty extends OptionalJobProperty<WorkflowJob> {

    @DataBoundConstructor
    public SuppressBranchIndexingBuildsJobProperty() {
    }

    @Extension
    @Symbol("suppressBranchIndexingBuilds")
    public static class DescriptorImpl extends OptionalJobPropertyDescriptor {

        @Override public String getDisplayName() {
            return "Suppress builds trigged by branch indexing";
        }

    }

    @Extension
    public static class Dispatcher extends Queue.QueueDecisionHandler {

        @SuppressWarnings({"unchecked", "rawtypes"}) // untypable
        @Override
        public boolean shouldSchedule(Queue.Task p, List<Action> actions) {
            for (Action action : actions) {
                if (action instanceof CauseAction) {
                    for (Cause c : ((CauseAction) action).getCauses()) {
                        if (c instanceof BranchIndexingCause) {
                            if (p instanceof WorkflowJob) {
                                WorkflowJob j = (WorkflowJob) p;
                                if (j.getProperty(SuppressBranchIndexingBuildsJobProperty.class) != null) {
                                    return false;
                                }
                            }
                        }
                    }
                }
            }
            return true;
        }

    }

}