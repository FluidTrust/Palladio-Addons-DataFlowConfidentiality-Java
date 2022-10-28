package edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.application.cli;

import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.eclipse.emf.ecore.plugin.EcorePlugin;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.xtext.linking.impl.AbstractCleaningLinker;
import org.eclipse.xtext.linking.impl.DefaultLinkingService;
import org.eclipse.xtext.parser.antlr.AbstractInternalAntlrParser;
import org.eclipse.xtext.resource.containers.ResourceSetBasedAllContainersStateProvider;
import org.palladiosimulator.dataflow.confidentiality.pcm.dddsl.DDDslStandaloneSetup;

import de.uka.ipd.sdq.workflow.Workflow;
import de.uka.ipd.sdq.workflow.jobs.SequentialBlackboardInteractingJob;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.application.jobs.AnalysisResultBlackboard;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.application.jobs.CaseStudyWorkflowBuilder;
import tools.mdsd.library.standalone.initialization.emfprofiles.EMFProfileInitializationTask;
import tools.mdsd.library.standalone.initialization.log4j.Log4jInitilizationTask;

public class PCSCaseStudyCLI implements IApplication {

    private static final String EXECUTABLE_NAME = "casestudy";

    @Override
    public Object start(final IApplicationContext context) throws Exception {
        final String[] args = Optional.ofNullable(context.getArguments()
            .get(IApplicationContext.APPLICATION_ARGS))
            .filter(String[].class::isInstance)
            .map(String[].class::cast)
            .orElseThrow(
                    () -> new IllegalStateException("Cannot access application arguments because of framework error."));

        Callable<Integer> action = createActionFromCommandLine(args);
        return action.call();
    }

    private Callable<Integer> createActionFromCommandLine(final String[] args) {
        // build options
        final Options options = new Options();
        final Option helpOption = new Option("h", "Displays available commands (other options are ignored)");
        final Option folderOption = Option.builder("f")
            .argName("folder")
            .hasArg()
            .required()
            .desc("Folder containing case study variants")
            .build();
        final Option resultOption = Option.builder("r")
            .argName("csvFile")
            .hasArg()
            .required()
            .desc("Name of result file (has to end with \".csv\")")
            .build();
        
        final Option scenarioOption = Option.builder("u").argName("usageScenario").hasArg().desc("Name of the targeted Usage Scenario").build();
        
        final Option stackLimitOption = Option.builder("s")
            .argName("stack limit")
            .hasArg()
            .desc("Stack size limit as specified for SWI Prolog.")
            .build();
        
        final Option characteristicOption = Option.builder("c").argName("characteristics").hasArgs().desc("Variable Characteristation in the format VARIABLE_NAME:VALUE").build();
        
        options.addOption(helpOption)
            .addOption(folderOption)
//            .addOption(resultOption)
            .addOption(scenarioOption)
            .addOption(stackLimitOption)
            .addOption(characteristicOption);
        
        
        

        // parse command line
        final CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = null;
        try {
            commandLine = parser.parse(options, args);
        } catch (ParseException e) {
            return createHelpAction(options, System.err,
                    "An error occured while parsing the command line: " + e.getLocalizedMessage());
        }

        // print help if requested to do so
        if (commandLine.hasOption(helpOption.getOpt())) {
            return createHelpAction(options, System.out);
        }

        // validate arguments
        String folderArgument = commandLine.getOptionValue(folderOption.getOpt());
        File scenarioFolder = new File(folderArgument).getAbsoluteFile();
        if (!scenarioFolder.isDirectory()) {
            return createHelpAction(options, System.err,
                    "The given " + folderOption.getArgName() + " has to be an existing directory." + folderArgument);
        }

//        String resultArgument = commandLine.getOptionValue(resultOption.getOpt());
//        File resultFile = new File(resultArgument).getAbsoluteFile();
//        if (!resultFile.getParentFile()
//            .isDirectory()) {
//            return createHelpAction(options, System.err,
//                    "The given " + resultOption.getArgName() + " has to be located in an existing directory.");
//        }
//        if (!resultFile.getName()
//            .endsWith(".csv")) {
//            return createHelpAction(options, System.err,
//                    "The given " + resultOption.getArgName() + " has to end with \".csv\". " + resultFile.getName());
//        }
        
        Optional<String> scenario = Optional.ofNullable(commandLine.getOptionValue(scenarioOption.getOpt()));
        
        var variables = commandLine.getOptionValues(characteristicOption.getOpt());
        
        if(variables != null && !Arrays.stream(variables).allMatch(this::checkFormat)) {
        	return createHelpAction(options, System.err,"The variables does not match the required format: InputString:" + variables);
        }
        
        
        

        // create run action
        return createRunAction(scenarioFolder, /*resultFile*/ null, scenario, variables);
    }

    protected Callable<Integer> createHelpAction(Options options, PrintStream ps) {
        return createHelpAction(options, ps, null);
    }

    protected Callable<Integer> createHelpAction(Options options, PrintStream ps, String customMessage) {
        return () -> {
            Optional.ofNullable(customMessage)
                .ifPresent(ps::println);
            printHelp(options, System.err);
            return 1;
        };
    }

    protected void printHelp(Options options, PrintStream ps) {
        HelpFormatter formatter = new HelpFormatter();
        try (PrintWriter pw = new PrintWriter(ps)) {
            formatter.printHelp(pw, HelpFormatter.DEFAULT_WIDTH, EXECUTABLE_NAME, null, options,
                    HelpFormatter.DEFAULT_LEFT_PAD, HelpFormatter.DEFAULT_DESC_PAD, null);
        }
    }

    protected Callable<Integer> createRunAction(File scenarioFolder, File resultFile, Optional<String> scenario, String variables[]) {
        return () -> {
            try {
                // initialization
                EcorePlugin.ExtensionProcessor.process(null);
                new EMFProfileInitializationTask("org.palladiosimulator.dataflow.confidentiality.pcm.model.profile", "profile.emfprofile_diagram").initilizationWithoutPlatform();
                DDDslStandaloneSetup.doSetup();
                new Log4jInitilizationTask().initilizationWithoutPlatform();
                BasicConfigurator.configure(new ConsoleAppender(new PatternLayout("%d{HH:mm:ss,SSS} %m%n")));
                Logger.getLogger(AbstractInternalAntlrParser.class).setLevel(Level.WARN);
                Logger.getLogger(DefaultLinkingService.class).setLevel(Level.WARN);
                Logger.getLogger(ResourceSetBasedAllContainersStateProvider.class).setLevel(Level.WARN);
                Logger.getLogger(AbstractCleaningLinker.class).setLevel(Level.WARN);
                
                // build and run job
                var jobBuilder = CaseStudyWorkflowBuilder.builder()
                    .casesFolder(scenarioFolder)
                    .resultFile(resultFile)
                    .scenario(scenario.isEmpty() ? null : scenario.get())
                    .variables(variables);
                var job = jobBuilder
                    .build();
                var workflow = new Workflow(job);
                workflow.run();
                
                if(job instanceof SequentialBlackboardInteractingJob) {
                	var blackboard = ((SequentialBlackboardInteractingJob) job).getBlackboard();
                	if(blackboard instanceof AnalysisResultBlackboard) {
                		var board = ((AnalysisResultBlackboard)blackboard);
                        for (var directory : board.getPartitionIds()) {
                            var result = board.getPartition(directory);
                            if(result.hasFoundViolations())
                            	return 10;
                        }
                	}
                }
                	
                
            } catch (Exception e) {
                System.err.println("Error while running case study workflow: " + e.getLocalizedMessage());
                return 2;
            }
            return 0;
        };
    }
    
    private boolean checkFormat(String inputString) {
    	var regex = "[\\p{javaLowerCase}\\p{javaUpperCase}]+:[\\p{javaLowerCase}\\p{javaUpperCase}\\p{Digit}]+";
    	return Pattern.matches(regex, inputString);
    }

    @Override
    public void stop() {
        // not implemented
    }

}
