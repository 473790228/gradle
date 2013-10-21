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

package org.gradle.plugin

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class PluginHandlerScriptIntegTest extends AbstractIntegrationSpec {

    private static final String SCRIPT = "println 'out'; plugins { println 'in' }"

    def "build scripts have plugin blocks"() {
        when:
        buildFile << SCRIPT
        buildFile << """
            plugins {
              apply 'java'
            }
        """

        then:
        executesCorrectly()
        output.contains "javadoc" // task added by java plugin
    }

    def "settings scripts have plugin blocks"() {
        when:
        settingsFile << SCRIPT

        then:
        executesCorrectly()
    }

    def "init scripts have plugin blocks"() {
        def initScript = file("init.gradle")

        when:
        initScript << SCRIPT

        then:
        args "-I", initScript.absolutePath
        executesCorrectly()
    }

    def "cannot use plugin block when script target is not plugin capable"() {
        buildFile << """
            task foo {}
            apply {
                from "plugin.gradle"
                to foo
            }
        """

        file("plugin.gradle") << """
            plugins {
                apply "foo"
            }
        """

        when:
        fails "foo"

        then:
        errorOutput.contains("cannot have plugins applied to it")
    }


    def void executesCorrectly() {
        succeeds "tasks"
        assert output.contains("in\nout\n") // Testing the the plugins {} block is extracted and executed before the “main” content
    }

    void "can resolve android plugin"() {
        given:
        buildFile << """
            plugins {
                apply "android"
            }
        """

        when:
        fails "tasks"

        then:
        errorOutput.contains "Gradle version 1.8 is required"
    }

    void "can resolve versioned android plugin"() {
        given:
        buildFile << """
            plugins {
                apply "android", "0.5.7"
            }
        """

        when:
        fails "tasks"

        then:
        errorOutput.contains "Gradle version 1.6 is required"
    }
}
