/*
 * The MIT License
 *
 * Copyright 2015, CloudBees, Inc.
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
 */

package org.jenkinsci.plugins.workflow.multibranch;

import hudson.model.TaskListener;
import jenkins.scm.api.SCMProbeStat;
import jenkins.scm.api.SCMSourceCriteria;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;

class FileExistsSCMSourceCriteria implements SCMSourceCriteria {
    private final String filePath;

    public FileExistsSCMSourceCriteria(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public boolean isHead(Probe probe, TaskListener listener) throws IOException {
        SCMProbeStat stat = probe.stat(filePath);
        PrintStream logger = listener.getLogger();
        switch (stat.getType()) {
            case NONEXISTENT:
                if (stat.getAlternativePath() != null) {
                    logger.format("      ‘%s’ not found (but found ‘%s’, search is case sensitive)%n", filePath, stat.getAlternativePath());
                } else {
                    logger.format("      ‘%s’ not found%n", filePath);
                }
                return false;
            case DIRECTORY:
                logger.format("      ‘%s’ found but is a directory not a file%n", filePath);
                return false;
            default:
                logger.format("      ‘%s’ found%n", filePath);
                return true;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileExistsSCMSourceCriteria that = (FileExistsSCMSourceCriteria) o;
        return Objects.equals(filePath, that.filePath);
    }
}
