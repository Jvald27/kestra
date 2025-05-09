package io.kestra.plugin.scripts.exec.scripts.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.models.tasks.runners.DefaultLogConsumer;
import io.kestra.core.models.tasks.runners.*;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.plugin.core.runner.Process;
import io.kestra.core.models.tasks.NamespaceFiles;
import io.kestra.core.runners.FilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.scripts.exec.scripts.models.DockerOptions;
import io.kestra.plugin.scripts.exec.scripts.models.RunnerType;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.runner.docker.Docker;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import static io.kestra.core.utils.NamespaceFilesUtils.loadNamespaceFiles;
import static io.kestra.core.utils.Rethrow.throwFunction;

@AllArgsConstructor
@Getter
public class CommandsWrapper implements TaskCommands {
    private RunContext runContext;

    private Path workingDirectory;

    private Path outputDirectory;

    private Map<String, Object> additionalVars;

    @With
    private Property<List<String>> interpreter;

    @With
    private Property<List<String>> beforeCommands;

    @With
    private Property<List<String>> commands;

    @With
    private boolean beforeCommandsWithOptions;

    @With
    private boolean failFast;

    private Map<String, String> env;

    @With
    private io.kestra.core.models.tasks.runners.AbstractLogConsumer logConsumer;

    @With
    private RunnerType runnerType;

    @With
    private String containerImage;

    @With
    private TaskRunner<?> taskRunner;

    @With
    private DockerOptions dockerOptions;

    @With
    private Boolean warningOnStdErr;

    @With
    private NamespaceFiles namespaceFiles;

    @With
    private Object inputFiles;

    @With
    private List<String> outputFiles;

    @With
    private Boolean enableOutputDirectory;

    @With
    private Duration timeout;

    @With
    private TargetOS targetOS;

    public CommandsWrapper(RunContext runContext) {
        this.runContext = runContext;
        this.workingDirectory = runContext.workingDir().path();
        this.logConsumer = new DefaultLogConsumer(runContext);
        this.additionalVars = new HashMap<>();
        this.env = new HashMap<>();
    }

    public CommandsWrapper withEnv(Map<String, String> envs) {
        return new CommandsWrapper(
            runContext,
            workingDirectory,
            getOutputDirectory(),
            additionalVars,
            interpreter,
            beforeCommands,
            commands,
            beforeCommandsWithOptions,
            failFast,
            envs,
            logConsumer,
            runnerType,
            containerImage,
            taskRunner,
            dockerOptions,
            warningOnStdErr,
            namespaceFiles,
            inputFiles,
            outputFiles,
            enableOutputDirectory,
            timeout,
            targetOS
        );
    }

    public CommandsWrapper addAdditionalVars(Map<String, Object> additionalVars) {
        if (this.additionalVars == null) {
            this.additionalVars = new HashMap<>();
        }
        this.additionalVars.putAll(additionalVars);

        return this;
    }

    public CommandsWrapper addEnv(Map<String, String> envs) {
        if (this.env == null) {
            this.env = new HashMap<>();
        }
        this.env.putAll(envs);

        return this;
    }

    public <T extends TaskRunnerDetailResult> ScriptOutput run() throws Exception {
        if (this.namespaceFiles != null && !Boolean.FALSE.equals(runContext.render(this.namespaceFiles.getEnabled()).as(Boolean.class).orElse(true))) {
            loadNamespaceFiles(runContext, this.namespaceFiles);
        }

        TaskRunner<T> realTaskRunner = this.getTaskRunner();
        if (this.inputFiles != null) {
            FilesService.inputFiles(runContext, realTaskRunner.additionalVars(runContext, this), this.inputFiles);
        }

        RunContextInitializer initializer = ((DefaultRunContext) runContext).getApplicationContext().getBean(RunContextInitializer.class);

        RunContext taskRunnerRunContext = initializer.forPlugin(((DefaultRunContext) runContext).clone(), realTaskRunner);

        List<String> renderedCommands = this.renderCommands(runContext, commands);
        List<String> renderedBeforeCommands = this.renderCommands(runContext, beforeCommands);
        List<String> renderedInterpreter = this.renderCommands(runContext, interpreter);

        List<String> finalCommands = renderedBeforeCommands.isEmpty() && renderedInterpreter.isEmpty() ?
            renderedCommands :
            ScriptService.scriptCommands(
                renderedInterpreter,
                this.isBeforeCommandsWithOptions() ? getBeforeCommandsWithOptions(renderedBeforeCommands) :  renderedBeforeCommands,
                renderedCommands,
                Optional.ofNullable(targetOS).orElse(TargetOS.AUTO)
            );

        this.commands = Property.of(finalCommands);

        ScriptOutput.ScriptOutputBuilder scriptOutputBuilder = ScriptOutput.builder()
            .warningOnStdErr(this.warningOnStdErr);

        try {
            TaskRunnerResult<T> taskRunnerResult = realTaskRunner.run(taskRunnerRunContext, this, this.outputFiles);
            scriptOutputBuilder.exitCode(taskRunnerResult.getExitCode())
                .outputFiles(getOutputFiles(taskRunnerRunContext))
                .taskRunner(taskRunnerResult.getDetails());

            if (taskRunnerResult.getLogConsumer() != null) {
                scriptOutputBuilder
                    .stdOutLineCount(taskRunnerResult.getLogConsumer().getStdOutCount())
                    .stdErrLineCount(taskRunnerResult.getLogConsumer().getStdErrCount())
                    .vars(taskRunnerResult.getLogConsumer().getOutputs());
            }

            return scriptOutputBuilder.build();
        } catch (TaskException e) {
            var output = scriptOutputBuilder.exitCode(e.getExitCode())
                .stdOutLineCount(e.getStdOutCount())
                .stdErrLineCount(e.getStdErrCount())
                .vars(e.getLogConsumer() != null ? e.getLogConsumer().getOutputs() : null)
                .outputFiles(getOutputFiles(taskRunnerRunContext))
                .build();
            throw new RunnableTaskException(e, output);
        }
    }

    private Map<String, URI> getOutputFiles(RunContext taskRunnerRunContext) throws Exception {
        Map<String, URI> outputFiles = new HashMap<>();
        if (this.outputDirectoryEnabled()) {
            outputFiles.putAll(ScriptService.uploadOutputFiles(taskRunnerRunContext, this.getOutputDirectory()));
        }

        if (this.outputFiles != null) {
            outputFiles.putAll(FilesService.outputFiles(taskRunnerRunContext, this.outputFiles));
        }
        return outputFiles;
    }

    @SuppressWarnings("unchecked")
    public <T extends TaskRunnerDetailResult> TaskRunner<T> getTaskRunner() {
        if (runnerType != null) {
            return switch (runnerType) {
                case DOCKER -> (TaskRunner<T>) Docker.from(dockerOptions);
                case PROCESS -> (TaskRunner<T>) new Process();
            };
        }

        // special case to take into account the deprecated dockerOptions if set
        if (taskRunner instanceof Docker && dockerOptions != null) {
            return (TaskRunner<T>) Docker.from(dockerOptions);
        }

        return (TaskRunner<T>) taskRunner;
    }

    public Boolean getEnableOutputDirectory() {
        if (this.enableOutputDirectory == null) {
            // For compatibility reasons, if legacy runnerType property is used, we enable the output directory
            return this.runnerType != null;
        }

        return this.enableOutputDirectory;
    }

    public Path getOutputDirectory() {
        if (this.outputDirectory == null) {
            this.outputDirectory = this.workingDirectory.resolve(IdUtils.create());
            if (!this.outputDirectory.toFile().mkdirs()) {
                throw new RuntimeException("Unable to create the output directory " + this.outputDirectory);
            }
        }

        return this.outputDirectory;
    }

    public String render(RunContext runContext, String command, List<String> internalStorageLocalFiles) throws IllegalVariableEvaluationException, IOException {
        TaskRunner<?> taskRunner = this.getTaskRunner();
        return ScriptService.replaceInternalStorage(
            this.runContext,
            taskRunner.additionalVars(runContext, this),
            command,
            taskRunner instanceof RemoteRunnerInterface
        );
    }

    public String render(RunContext runContext, Property<String> command) throws IllegalVariableEvaluationException, IOException {
        TaskRunner<?> taskRunner = this.getTaskRunner();
        if (command == null) {
            return null;
        }

        return runContext.render(command).as(String.class, taskRunner.additionalVars(runContext, this))
            .map(throwFunction(c -> ScriptService.replaceInternalStorage(runContext, c, taskRunner instanceof RemoteRunnerInterface)))
            .orElse(null);
    }

    public List<String> renderCommands(RunContext runContext, Property<List<String>> commands) throws IllegalVariableEvaluationException, IOException {
        TaskRunner<?> taskRunner = this.getTaskRunner();
        return ScriptService.replaceInternalStorage(
            this.runContext,
            taskRunner.additionalVars(runContext, this),
            commands,
            taskRunner instanceof RemoteRunnerInterface
        );
    }

    protected List<String> getBeforeCommandsWithOptions(List<String> beforeCommands) throws IllegalVariableEvaluationException {
        if (!this.isFailFast()) {
            return beforeCommands;
        }

        if (beforeCommands == null || beforeCommands.isEmpty()) {
            return getExitOnErrorCommands();
        }

        ArrayList<String> newCommands = new ArrayList<>(beforeCommands.size() + 1);
        newCommands.addAll(getExitOnErrorCommands());
        newCommands.addAll(beforeCommands);
        return newCommands;
    }

    protected List<String> getExitOnErrorCommands() {
        TargetOS os = this.getTargetOS();

        // If targetOS is Windows OR targetOS is AUTO && current system is windows and we use process as a runner.(TLDR will run on windows)
        if (os == TargetOS.WINDOWS ||
            (os == TargetOS.AUTO && SystemUtils.IS_OS_WINDOWS && this.getTaskRunner() instanceof Process)) {
            return List.of("");
        }
        // errexit option may be unsupported by non-shell interpreter.
        return List.of("set -e");
    }
}