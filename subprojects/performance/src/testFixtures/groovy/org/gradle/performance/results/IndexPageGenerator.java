/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.performance.results;

import com.googlecode.jatl.Html;
import org.gradle.performance.fixture.MeasuredOperationList;
import org.gradle.performance.fixture.PerformanceResults;

import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.List;

public class IndexPageGenerator extends HtmlPageGenerator<ResultsStore> {
    @Override
    public void render(final ResultsStore store, Writer writer) throws IOException {
        new Html(writer) {{
            html();
                head();
                    headSection(this);
                    title().text("Profile report").end();
                end();
                body();
                div().id("content");
                    h2().text("All tests").end();
                    List<String> testNames = store.getTestNames();
                    div().id("controls").end();
                    table().classAttr("history");
                    for (String testName : testNames) {
                        TestExecutionHistory testHistory = store.getTestResults(testName);
                        tr();
                            th().colspan("6").classAttr("test-execution");
                                text(testName);
                            end();
                        end();
                        tr().classAttr("control-groups");
                            th().colspan("3").end();
                            th().colspan(String.valueOf(testHistory.getPerExecutionOperationsCount())).text("Average execution time").end();
                            th().colspan(String.valueOf(testHistory.getPerExecutionOperationsCount())).text("Average heap usage").end();
                        end();
                        tr();
                            th().text("Date").end();
                            th().text("Test version").end();
                            th().text("Branch").end();
                            for (String label : testHistory.getOperationLabels()) {
                                th().classAttr("numeric").text(label).end();
                            }
                            for (String label : testHistory.getOperationLabels()) {
                                th().classAttr("numeric").text(label).end();
                            }
                        end();
                        List<PerformanceResults> results = testHistory.getPerformanceResults();
                        for (int i = 0; i < results.size() && i < 5; i++) {
                            PerformanceResults performanceResults = results.get(i);
                            tr();
                                td().text(format.timestamp(new Date(performanceResults.getTestTime()))).end();
                                td().text(performanceResults.getVersionUnderTest()).end();
                                td().text(performanceResults.getVcsBranch()).end();
                                for (MeasuredOperationList measuredExecution : performanceResults.getExecutionOperations()) {
                                    td().classAttr("numeric");
                                    if (measuredExecution.isEmpty()) {
                                        text("");
                                    } else {
                                        text(measuredExecution.getExecutionTime().getAverage().format());
                                    }
                                    end();
                                }
                            for (MeasuredOperationList measuredExecution : performanceResults.getExecutionOperations()) {
                                    td().classAttr("numeric");
                                    if (measuredExecution.isEmpty()) {
                                        text("");
                                    } else {
                                        text(measuredExecution.getTotalMemoryUsed().getAverage().format());
                                    }
                                    end();
                                }
                            end();
                        }
                        tr();
                            td().colspan("6");
                                String url = testHistory.getId() + ".html";
                                a().href(url).text("details...").end();
                            end();
                        end();
                    }
                    end();
                end();
                footer(this);
            endAll();
        }};
    }
}
