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
package org.gradle.nativebinaries.toolchain.internal.gcc

import org.gradle.api.Action
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.text.TreeFormatter
import org.gradle.nativebinaries.toolchain.ConfigurableToolChain
import org.gradle.nativebinaries.toolchain.internal.PlatformToolChain
import org.gradle.nativebinaries.toolchain.internal.tools.DefaultTool
import org.gradle.nativebinaries.platform.Platform
import org.gradle.nativebinaries.platform.internal.ArchitectureInternal
import org.gradle.nativebinaries.platform.internal.DefaultArchitecture
import org.gradle.nativebinaries.platform.internal.DefaultOperatingSystem

import org.gradle.nativebinaries.toolchain.internal.ToolSearchResult
import org.gradle.nativebinaries.toolchain.internal.ToolType

import org.gradle.nativebinaries.toolchain.internal.tools.ToolSearchPath
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TreeVisitor
import spock.lang.Specification

import static ArchitectureInternal.InstructionSet.X86

class AbstractGccCompatibleToolChainTest extends Specification {
    def fileResolver = Mock(FileResolver)
    def execActionFactory = Mock(ExecActionFactory)
    def toolSearchPath = Stub(ToolSearchPath)
    def tool = Stub(CommandLineToolSearchResult) {
        isAvailable() >> true
    }
    def toolChain = new TestToolChain("test", fileResolver, execActionFactory, toolSearchPath)
    def platform = Stub(Platform)

    def "is unavailable when platform is not known and is not the default platform"() {
        given:
        platform.name >> 'unknown'

        expect:
        def platformToolChain = toolChain.select(platform)
        !platformToolChain.available
        getMessage(platformToolChain) == "Don't know how to build for platform 'unknown'."
    }

    def "is unavailable when no language tools can be found and building for default platform"() {
        def missing = Stub(CommandLineToolSearchResult) {
            isAvailable() >> false
            explain(_) >> { TreeVisitor<String> visitor -> visitor.node("c compiler not found") }
        }

        given:
        platform.operatingSystem >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.architecture >> ArchitectureInternal.TOOL_CHAIN_DEFAULT

        and:
        toolSearchPath.locate(ToolType.C_COMPILER, "gcc") >> missing
        toolSearchPath.locate(ToolType.CPP_COMPILER, "g++") >> missing
        toolSearchPath.locate(ToolType.OBJECTIVEC_COMPILER, "gcc") >> missing
        toolSearchPath.locate(ToolType.OBJECTIVECPP_COMPILER, "g++") >> missing

        expect:
        def platformToolChain = toolChain.select(platform)
        !platformToolChain.available
        getMessage(platformToolChain) == "c compiler not found"
    }

    def "is available when any language tool can be found and building for default platform"() {
        def missing = Stub(CommandLineToolSearchResult) {
            isAvailable() >> false
        }

        given:
        platform.operatingSystem >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.architecture >> ArchitectureInternal.TOOL_CHAIN_DEFAULT

        and:
        toolSearchPath.locate(ToolType.CPP_COMPILER, "g++") >> missing
        toolSearchPath.locate(_, _) >> tool

        expect:
        toolChain.select(platform).available
    }

    def "is available when any language tool can be found and platform configuration registered for platform"() {
        given:
        toolSearchPath.locate(_, _) >> tool
        toolChain.target(platform, Mock(Action))

        expect:
        toolChain.select(platform).available
    }

    def "supplies no additional arguments to target native binary for tool chain default"() {
        when:
        toolSearchPath.locate(_, _) >> tool
        platform.getOperatingSystem() >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.getArchitecture() >> ArchitectureInternal.TOOL_CHAIN_DEFAULT

        ConfigurableToolChain configurableToolChain = newConfigurableToolChain()
        then:

        with(toolChain.getPlatformConfiguration(platform).apply(configurableToolChain)) {
            def args = []
            configurableToolChain.linker.getArgAction().execute(args)
            args == []
            configurableToolChain.cppCompiler.getArgAction().execute(args)
            args == []
            configurableToolChain.getCCompiler().getArgAction().execute(args)
            args == []
            configurableToolChain.assembler.getArgAction().execute(args)
            args == []
            configurableToolChain.staticLibArchiver.getArgAction().execute(args)
            args == []
            configurableToolChain.objcCompiler.getArgAction().execute(args)
            args == []
        }
    }


    @Requires(TestPrecondition.NOT_WINDOWS)
    def "supplies args for supported architecture"() {
        when:
        toolSearchPath.locate(_, _) >> tool
        platform.operatingSystem >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.architecture >> new DefaultArchitecture(arch, instructionSet, registerSize)

        then:
        toolChain.select(platform).available

        with(toolChain.getPlatformConfiguration(platform).apply(newConfigurableToolChain())) {
            argsFor(linker) ==  [linkerArg]
            argsFor(cppCompiler) == [compilerArg]
            argsFor(getCCompiler()) == [compilerArg]

            if (OperatingSystem.current().isMacOsX()) {
                argsFor(assembler) == osxAssemblerArgs
            } else {
                argsFor(assembler) == [assemblerArg]
            }
            argsFor(staticLibArchiver) == []
        }

        where:
        arch     | instructionSet | registerSize | linkerArg | compilerArg | assemblerArg | osxAssemblerArgs
        "i386"   | X86            | 32           | "-m32"    | "-m32"      | "--32"       | ["-arch", "i386"]
        "x86_64" | X86            | 64           | "-m64"    | "-m64"      | "--64"       | ["-arch", "x86_64"]
    }

    @Requires(TestPrecondition.WINDOWS)
    def "supplies args for supported architecture for i386 architecture on windows"() {
        when:
        toolSearchPath.locate(_, _) >> tool
        platform.operatingSystem >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.architecture >> new DefaultArchitecture("i386", X86, 32)

        then:
        toolChain.select(platform).available

        with(toolChain.getPlatformConfiguration(platform).apply(newConfigurableToolChain())) {
            argsFor(linker) == ["-m32"]
            argsFor(cppCompiler)== ["-m32"]
            argsFor(getCCompiler()) == ["-m32"]
            argsFor(assembler) == ["--32"]
            argsFor(staticLibArchiver) == []
        }
    }

    @Requires(TestPrecondition.WINDOWS)
    def "cannot target x86_64 architecture on windows"() {
        given:
        toolSearchPath.locate(_, _) >> tool

        and:
        platform.getName() >> "x64"
        platform.operatingSystem >> DefaultOperatingSystem.TOOL_CHAIN_DEFAULT
        platform.architecture >> new DefaultArchitecture("x64", X86, 64)

        when:
        def platformToolChain = toolChain.select(platform)

        then:
        !platformToolChain.available
        getMessage(platformToolChain) == "Don't know how to build for platform 'x64'."
    }

    def "uses supplied platform configurations in order to target binary"() {
        setup:
        _ * platform.getName() >> "platform2"
        def platformConfig1 = Mock(Action)
        def platformConfig2 = Mock(Action)
        when:
        toolSearchPath.locate(_, _) >> tool
        platform.getOperatingSystem() >> new DefaultOperatingSystem("other", OperatingSystem.SOLARIS)
        toolChain.target("platform1", platformConfig1)
        toolChain.target("platform2", platformConfig2)

        PlatformToolChain platformToolChain = toolChain.select(platform)

        then:
        platformToolChain.available

        and:
        1 * platformConfig2.execute(_)
    }

    def "uses platform specific toolchain configuration"() {
        given:
        boolean configurationApplied = false
        _ * platform.getName() >> "testPlatform"
        when:
        toolSearchPath.locate(_, _) >> tool
        platform.getOperatingSystem() >> new DefaultOperatingSystem("other", OperatingSystem.SOLARIS)

        and:
        toolChain.target(platform, new Action<ConfigurableToolChain>(){
            void execute(ConfigurableToolChain configurableToolChain) {
                configurationApplied = true;
            }
        })

        then:
        toolChain.select(platform).available
        configurationApplied
    }

    def getMessage(ToolSearchResult result) {
        def formatter = new TreeFormatter()
        result.explain(formatter)
        return formatter.toString()
    }

    static class TestToolChain extends AbstractGccCompatibleToolChain {
        TestToolChain(String name, FileResolver fileResolver, ExecActionFactory execActionFactory, ToolSearchPath tools) {
            super(name, OperatingSystem.current(), fileResolver, execActionFactory, tools)

            registerTool(ToolType.CPP_COMPILER, "g++");
            registerTool(ToolType.C_COMPILER, "gcc");
            registerTool(ToolType.OBJECTIVECPP_COMPILER, "g++");
            registerTool(ToolType.OBJECTIVEC_COMPILER, "gcc");
            registerTool(ToolType.ASSEMBLER, "as");
            registerTool(ToolType.LINKER, "ld");
            registerTool(ToolType.STATIC_LIB_ARCHIVER, "ar");
        }

        @Override
        protected String getTypeName() {
            return "Test"
        }
    }


    ConfigurableToolChain newConfigurableToolChain() {
        ConfigurableToolChain configurableToolChain = Mock(ConfigurableToolChain)
        _ * configurableToolChain.assembler >> new DefaultTool(ToolType.ASSEMBLER, "")
        _ * configurableToolChain.CCompiler>> new DefaultTool(ToolType.C_COMPILER, "")
        _ * configurableToolChain.cppCompiler >> new DefaultTool(ToolType.CPP_COMPILER, "")
        _ * configurableToolChain.linker >> new DefaultTool(ToolType.LINKER, "")
        _ * configurableToolChain.staticLibArchiver >> new DefaultTool(ToolType.STATIC_LIB_ARCHIVER, "")
        _ * configurableToolChain.objcCompiler >> new DefaultTool(ToolType.OBJECTIVEC_COMPILER, "")
        _ * configurableToolChain.objcppCompiler >> new DefaultTool(ToolType.OBJECTIVECPP_COMPILER, "")
        return configurableToolChain;
    }

    def argsFor(def tool) {
        def args = []
        tool.getArgAction().execute(args)
        args
    }
}
